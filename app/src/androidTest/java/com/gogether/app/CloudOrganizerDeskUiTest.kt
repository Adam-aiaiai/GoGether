package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.*
import com.gogether.app.core.data.cloud.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.cloud.*
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Isolated data, real production composables. Never writes to the team's server. */
@OptIn(ExperimentalTestApi::class)
class CloudOrganizerDeskUiTest {
    @get:Rule val compose = createComposeRule()
    private val clock = System.currentTimeMillis()
    private val base = DemoData.create(clock)
    private var event by mutableStateOf(base.events.first().copy(
        id = "organizer-ui-${UUID.randomUUID()}", organizerId = "alice", title = "Campus badminton meetup",
        startAt = clock + 10 * 60_000, endAt = clock + 100 * 60_000, status = EventStatus.OPEN,
        city = "Suzhou", meetingLocation = "Sports Centre, Court 3", meetingAt = clock,
        meetingNotes = "Meet Alice by the east entrance. Bring your racket and water.",
    ))
    private var now by mutableLongStateOf(clock)
    private var showing by mutableStateOf(true)
    private var code by mutableStateOf<String?>("GOGETHER-CLOUD-1|ui-layout-fixture-not-valid-for-check-in")
    private var expires by mutableLongStateOf(clock + 30_000)
    private var meeting by mutableStateOf(true)
    private var loading by mutableStateOf(false)
    private var information by mutableStateOf(true)
    private var loaded by mutableStateOf(true)
    private var rosterLoading by mutableStateOf(false)
    private var rosterError by mutableStateOf("")
    private var people by mutableStateOf(listOf("Bob", "Carol", "David", "Emma", "Finn", "Grace").mapIndexed { i, name ->
        CloudAttendancePerson(name.lowercase(), name, if (i < 4) clock - i * 60_000 else 0)
    })
    private var width by mutableIntStateOf(400)
    private var height by mutableIntStateOf(860)
    private var font by mutableFloatStateOf(1f)
    private var showRequests = 0
    private var bindRequests = 0
    private var refreshRequests = 0
    private var attendanceRequests = 0

    private fun launch() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then
                DeviceConfigurationOverride.FontScale(font)) {
                GogetherTheme {
                    CloudOrganizerCheckInContent(event, now, meeting, code, expires, showing, loading, information,
                        false, "", people, loaded, rosterLoading, rosterError, {}, { refreshRequests++ }, {},
                        { attendanceRequests++ }, { showRequests++; showing = true }, { showing = false }, { bindRequests++ })
                }
            }
        }
    }

    private val stationary = listOf("cloud_qr_slot", "cloud_attendance_preview", "cloud_meeting_details", "cloud_checkin_rules")
    private fun positions() = stationary.associateWith { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
    private fun tap(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    @Test fun hideAndShowKeepEveryLowerSectionInPlaceAcrossSizes() {
        launch()
        for ((w, h, f) in listOf(Triple(400, 860, 1f), Triple(320, 568, 1.5f), Triple(840, 1100, 1f))) {
            compose.runOnIdle { width = w; height = h; font = f }
            compose.onNodeWithTag("cloud_hide_code").performScrollTo()
            val before = positions()
            if (w == 400) capture("01-organizer-code")
            compose.onNodeWithTag("cloud_hide_code").performClick()
            compose.onNodeWithTag("cloud_qr").assertDoesNotExist()
            compose.onNodeWithTag("cloud_organizer_status").assertTextEquals("Check-in open")
            assertEquals("Hiding must preserve layout and scroll at ${w}dp", before, positions())
            if (w == 400) capture("02-organizer-hidden")
            // The replacement action remains in the same reserved slot. No new page or collapsed region.
            compose.onNodeWithTag("cloud_issue_code").performClick()
            compose.onNodeWithTag("cloud_qr").assertExists()
            assertEquals("Showing must preserve layout and scroll at ${w}dp", before, positions())
            if (w == 840) capture("07-organizer-tablet")
        }
        assertEquals(3, showRequests)
        assertEquals(0, bindRequests)
    }

    @Test fun moreThanFourAvatarsScrollHorizontallyWithoutChangingCountOrWrapping() {
        compose.runOnIdle {
            people = listOf("Bob", "Carol", "David", "Emma", "Finn", "Grace", "Henry", "Ivy").mapIndexed { i, name ->
                CloudAttendancePerson(name.lowercase(), name, clock - i * 60_000)
            }
        }
        launch()
        compose.onNodeWithTag("cloud_attendance_preview").performScrollTo()
        compose.onNodeWithTag("cloud_attendance_preview_count").assertTextEquals("8 of 8 checked in")
        val before = positions()
        compose.onNodeWithTag("cloud_preview_bob").assertIsDisplayed()
        compose.onNodeWithTag("cloud_attendance_avatars").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("cloud_preview_ivy").assertIsDisplayed()
        assertEquals(compose.onNodeWithTag("cloud_preview_henry").getUnclippedBoundsInRoot().top,
            compose.onNodeWithTag("cloud_preview_ivy").getUnclippedBoundsInRoot().top)
        assertEquals(before, positions())
        capture("06-organizer-avatar-scroll")
        tap("cloud_tab_attendance")
        assertEquals(1, attendanceRequests)
    }

    @Test fun meetingAndRulesOpenAndCloseWithoutChangingCodeOrBindingLocation() {
        launch()
        tap("cloud_meeting_details")
        compose.onNodeWithTag("cloud_meeting_sheet").assertIsDisplayed()
        compose.onNodeWithText(event.meetingNotes).assertIsDisplayed()
        compose.onNodeWithTag("cloud_meeting_maps").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("cloud_update_meeting").assertIsDisplayed()
        capture("04-meeting-point", modal = true)
        compose.onNodeWithTag("cloud_meeting_sheet_close").performClick()
        assertTrue(showing)
        assertEquals(0, bindRequests)
        tap("cloud_checkin_rules")
        compose.onNodeWithTag("cloud_rules_sheet").assertIsDisplayed()
        compose.onNodeWithText("Within 200 m of the confirmed meeting point.").assertIsDisplayed()
        capture("05-check-in-rules", modal = true)
        compose.onNodeWithTag("cloud_rules_done").assertIsDisplayed().performClick()
        compose.onNodeWithTag("cloud_rules_sheet").assertDoesNotExist()
        assertTrue(showing)
        assertEquals(0, bindRequests)
        tap("cloud_meeting_details")
        compose.onNodeWithTag("cloud_update_meeting").performClick()
        assertEquals(1, bindRequests)
        compose.onNodeWithTag("cloud_meeting_sheet").assertDoesNotExist()
    }

    @Test fun expiredCodeAndLoadingNeverMoveAttendanceOrShowAnInvalidQr() {
        launch()
        val before = positions()
        compose.runOnIdle { expires = clock - 1 }
        compose.onNodeWithTag("cloud_qr").assertDoesNotExist()
        compose.onNodeWithText("Getting a fresh code…").assertExists()
        assertEquals(before, positions())
        compose.runOnIdle { code = null; loading = true; information = false }
        compose.onNodeWithTag("cloud_organizer_retry").assertIsNotEnabled()
        assertEquals(before, positions())
        compose.runOnIdle { loading = false }
        tap("cloud_organizer_retry")
        assertEquals(1, refreshRequests)
        compose.runOnIdle { information = true; showing = false; now = event.endAt }
        compose.onNodeWithTag("cloud_organizer_status").assertTextEquals("Event ended")
        compose.onNodeWithTag("cloud_issue_code").assertDoesNotExist()
        tap("cloud_meeting_details")
        compose.onNodeWithTag("cloud_update_meeting").assertDoesNotExist()
    }

    @Test fun missingRosterDoesNotPretendThatZeroPeopleHaveCheckedIn() {
        loaded = false; rosterLoading = true; people = emptyList()
        launch()
        compose.onNodeWithTag("cloud_attendance_preview_count").assertTextEquals("Loading…")
        compose.onNodeWithText("No one has checked in yet.").assertDoesNotExist()
        compose.runOnIdle { rosterLoading = false; rosterError = "Connection lost" }
        compose.onNodeWithTag("cloud_attendance_preview_count").assertTextEquals("Unavailable")
        compose.runOnIdle { loaded = true; rosterError = "" }
        compose.onNodeWithTag("cloud_attendance_preview_count").assertTextEquals("0 of 0 checked in")
        compose.onNodeWithText("No one has checked in yet.").assertExists()
    }

    private class Backend(private val clock: Long, var rows: List<Map<String, Any?>>) : CloudOperations {
        override val storage: FirebaseStorage get() = error("No media operation in this test")
        override val sync = MutableStateFlow(CloudSyncState())
        val calls = mutableListOf<Pair<String, String?>>()
        var gate: CompletableDeferred<Unit>? = null
        var fail = false
        var allowIssue = false
        override suspend fun refresh() = Unit
        override suspend fun call(action: String, payload: Map<String, Any?>, requestId: String?): Map<String, Any?> {
            calls += action to (payload["eventId"] as? String)
            return when (action) {
                "eventInfo" -> mapOf("serverNow" to clock, "meeting" to mapOf("lat" to 31.27, "lng" to 120.58))
                "reviews" -> mapOf("reviews" to emptyList<Any>())
                "issueCheckInCode" -> {
                    check(allowIssue)
                    check((payload["fix"] as? Map<*, *>)?.isNotEmpty() == true)
                    mapOf("serverNow" to clock, "expiresAt" to clock + 30_000,
                        "code" to "GOGETHER-CLOUD-1|isolated-ui-test|not-valid-for-check-in|${calls.size}")
                }
                "attendance" -> {
                    gate?.await()
                    check(!fail) { "Could not refresh attendance. Try again." }
                    mapOf("attendees" to rows)
                }
                else -> error("Unexpected mutation: $action")
            }
        }
    }

    private fun backend() = Backend(clock, people.map { mapOf("userId" to it.userId, "name" to it.name, "checkedInAt" to it.checkedInAt) })
    private fun repository() = FakeGogetherRepository(base.copy(currentUserId = "alice", events = listOf(event),
        registrations = base.registrations.filter { it.eventId == base.events.first().id }
            .map { it.copy(eventId = event.id) }), now = { clock })

    private class NetworkFixture(local: GogetherRepository, backend: CloudOperations) :
        GogetherRepository by local, CloudOperations by backend

    @Test fun normalEventEntryCanIssueHideAndReopenCodeAndReturnFromRosterAndReviews() {
        val device = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (permission in listOf("ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION")) {
            device.executeShellCommand("pm grant com.gogether.app android.permission.$permission").close()
        }
        val backend = backend().apply { allowIssue = true }
        val repo = NetworkFixture(repository(), backend)
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
        compose.onNodeWithTag("nav_my-events").performClick()
        tap("personal_tab_CREATED")
        tap("event_${event.id}")
        tap("open_checkin")
        compose.onNodeWithTag("cloud_organizer_desk").assertExists()
        tap("cloud_issue_code")
        compose.waitUntil(25_000) { compose.onAllNodesWithTag("cloud_qr").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("cloud_hide_code").performScrollTo()
        val before = positions()
        compose.onNodeWithTag("cloud_hide_code").performClick()
        compose.onNodeWithTag("cloud_qr").assertDoesNotExist()
        assertEquals(before, positions())
        val issued = backend.calls.count { it.first == "issueCheckInCode" }
        Thread.sleep(11_000) // The existing automatic issuance interval is ten seconds.
        assertEquals("Hidden codes must stop refreshing", issued, backend.calls.count { it.first == "issueCheckInCode" })
        tap("cloud_issue_code")
        compose.waitUntil(25_000) { backend.calls.count { it.first == "issueCheckInCode" } > issued }
        compose.onNodeWithTag("cloud_qr").assertExists()
        tap("cloud_tab_attendance")
        compose.onNodeWithTag("cloud_attendance_count").assertTextEquals("4 of 6 checked in")
        tap("cloud_tab_reviews")
        compose.onNodeWithText("Reviews open after the event ends.").assertExists()
        Espresso.pressBack()
        compose.onNodeWithTag("cloud_attendance_page").assertExists()
        Espresso.pressBack()
        compose.onNodeWithTag("cloud_organizer_desk").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("open_checkin").assertExists()
        assertTrue(backend.calls.all { it.second == event.id })
        assertTrue(backend.calls.none { it.first == "submitCheckIn" || it.first == "bindMeeting" })
    }

    @Test fun realScreenConnectsViewAllSearchFiltersRefreshAndBack() {
        val repo = repository(); val backend = backend()
        compose.setContent { GogetherTheme { CloudCheckInScreen(repo, backend, event.id, clock, {}) } }
        compose.onNodeWithTag("cloud_organizer_desk").assertExists()
        compose.onNodeWithTag("cloud_attendance_preview_count").assertTextEquals("4 of 6 checked in")
        tap("cloud_tab_attendance")
        compose.onNodeWithTag("cloud_attendance_count").assertTextEquals("4 of 6 checked in")
        capture("03-attendance")
        compose.onNodeWithTag("cloud_attendance_filter_waiting").performScrollTo().performClick()
        compose.onNodeWithTag("cloud_attendee_finn").assertExists()
        compose.onNodeWithTag("cloud_attendee_bob").assertDoesNotExist()
        compose.onNodeWithTag("cloud_attendance_filter_all").performClick()
        compose.onNodeWithTag("cloud_attendance_search").performTextInput("Bob")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("cloud_attendee_bob").assertExists()
        compose.onNodeWithTag("cloud_attendee_carol").assertDoesNotExist()
        compose.onNodeWithTag("cloud_attendance_search").performTextClearance()
        Espresso.closeSoftKeyboard()
        compose.runOnIdle {
            backend.rows = backend.rows.map { if (it["userId"] == "finn") it + ("checkedInAt" to clock) else it }
        }
        val previousCalls = backend.calls.size
        compose.onNodeWithTag("cloud_attendance_refresh").performClick()
        compose.onNodeWithTag("cloud_attendance_count").assertTextEquals("5 of 6 checked in")
        assertEquals("The roster refresh must not refresh unrelated content", listOf("attendance"), backend.calls.drop(previousCalls).map { it.first })
        Espresso.pressBack()
        compose.onNodeWithTag("cloud_organizer_desk").assertExists()
        compose.onNodeWithTag("cloud_attendance_preview_count").assertTextEquals("5 of 6 checked in")
        assertTrue(backend.calls.all { it.second == event.id })
    }

    @Test fun rosterFailureKeepsLastListAndRetryUpdatesItAfterLoading() {
        val repo = repository(); val backend = backend()
        compose.setContent { GogetherTheme { CloudCheckInScreen(repo, backend, event.id, clock, {}, attendanceOnly = true) } }
        compose.onNodeWithTag("cloud_attendance_count").assertTextEquals("4 of 6 checked in")
        compose.runOnIdle { backend.gate = CompletableDeferred(); backend.fail = true }
        compose.onNodeWithTag("cloud_attendance_refresh").performClick()
        compose.onNodeWithTag("cloud_attendance_loading").assertExists()
        compose.onNodeWithTag("cloud_attendance_refresh").assertIsNotEnabled()
        compose.onNodeWithTag("cloud_attendance_count").assertTextEquals("4 of 6 checked in")
        compose.runOnIdle { backend.gate!!.complete(Unit) }
        compose.onNodeWithTag("cloud_attendance_error").assertExists()
        compose.onNodeWithTag("cloud_attendance_count").assertTextEquals("4 of 6 checked in")
        compose.runOnIdle { backend.gate = null; backend.fail = false; backend.rows = emptyList() }
        tap("cloud_attendance_retry")
        compose.onNodeWithTag("cloud_attendance_count").assertTextEquals("0 of 0 checked in")
        compose.onNodeWithTag("cloud_attendance_error").assertDoesNotExist()
        compose.onNodeWithText("No participants yet.").assertExists()
    }

    @Test fun switchingAccountCannotKeepOrganizerRosterOpen() {
        val repo = repository(); val backend = backend()
        compose.setContent { GogetherTheme { CloudCheckInScreen(repo, backend, event.id, clock, {}, attendanceOnly = true) } }
        compose.onNodeWithTag("cloud_attendee_bob").assertExists()
        compose.runOnIdle { runBlocking { repo.login("bob").getOrThrow() } }
        compose.onNodeWithText("Only the organizer can view attendance.").assertExists()
        compose.onNodeWithTag("cloud_attendance_page").assertDoesNotExist()
        compose.onNodeWithTag("cloud_attendee_bob").assertDoesNotExist()
        val before = backend.calls.count { it.first == "attendance" }
        compose.onNodeWithTag("cloud_tab_checkin").performClick()
        compose.onNodeWithTag("cloud_participant_checkin").assertExists()
        assertEquals(before, backend.calls.count { it.first == "attendance" })
    }

    @Test fun changingEventDropsPreviousRosterAndWaitingRequest() {
        val first = event
        val second = event.copy(id = "other-${UUID.randomUUID()}", title = "Evening study group")
        val repo = FakeGogetherRepository(base.copy(currentUserId = "alice", events = listOf(first, second)))
        val backend = backend()
        var id by mutableStateOf(first.id)
        compose.setContent { GogetherTheme { CloudCheckInScreen(repo, backend, id, clock, {}, attendanceOnly = true) } }
        compose.onNodeWithTag("cloud_attendee_bob").assertExists()
        compose.runOnIdle { backend.gate = CompletableDeferred() }
        compose.onNodeWithTag("cloud_attendance_refresh").performClick()
        compose.runOnIdle { backend.rows = listOf(mapOf("userId" to "zoe", "name" to "Zoe", "checkedInAt" to clock)); id = second.id }
        compose.onNodeWithTag("cloud_attendee_bob").assertDoesNotExist()
        compose.onNodeWithText(second.title).assertExists()
        compose.runOnIdle { backend.gate!!.complete(Unit) }
        compose.onNodeWithTag("cloud_attendee_zoe").assertExists()
        compose.onNodeWithTag("cloud_attendance_count").assertTextEquals("1 of 1 checked in")
        assertEquals(second.id, backend.calls.last { it.first == "attendance" }.second)
    }

    private fun capture(name: String, modal: Boolean = false) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "organizer-desk").apply { mkdirs() }
        val bitmap = if (modal) instrumentation.uiAutomation.takeScreenshot() else compose.onRoot().captureToImage().asAndroidBitmap()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

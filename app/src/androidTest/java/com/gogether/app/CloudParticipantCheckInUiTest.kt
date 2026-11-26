package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import com.gogether.app.core.data.*
import com.gogether.app.core.data.cloud.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.CheckInWindow
import com.gogether.app.feature.checkin.cloud.CloudCheckInScreen
import com.gogether.app.feature.checkin.cloud.CloudParticipantCheckInContent
import com.gogether.app.ui.theme.GogetherTheme
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

@OptIn(ExperimentalTestApi::class)
class CloudParticipantCheckInUiTest {
    @get:Rule val compose = createComposeRule()
    private val clock = System.currentTimeMillis()
    private val base = DemoData.create(clock)
    private var event by mutableStateOf(base.events.first().copy(
        id = "participant-ui-${UUID.randomUUID()}", organizerId = "alice", title = "Campus badminton meetup",
        startAt = clock + 10 * 60_000, endAt = clock + 100 * 60_000, status = EventStatus.OPEN,
        city = "Suzhou", meetingLocation = "Sports centre east entrance", meetingAt = clock,
        meetingNotes = "Look for Alice by the blue sign.",
    ))
    private var registration by mutableStateOf<RegistrationStatus?>(RegistrationStatus.CONFIRMED)
    private var receipt by mutableStateOf<Long?>(null)
    private var now by mutableLongStateOf(clock)
    private var loading by mutableStateOf(false)
    private var information by mutableStateOf(true)
    private var meeting by mutableStateOf(true)
    private var busy by mutableStateOf(false)
    private var pending by mutableStateOf(false)
    private var unreadable by mutableStateOf(false)
    private var message by mutableStateOf("")
    private var scans = 0
    private var backs = 0
    private var refreshes = 0
    private var reviews = 0
    private var resolutions = 0
    private var retries = 0

    private fun launch(width: Int = 360, height: Int = 740, fontScale: Float = 1f,
                       restorer: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then
                DeviceConfigurationOverride.FontScale(fontScale)) {
                GogetherTheme {
                    CloudParticipantCheckInContent(event, "Bob", "Alice", registration, now, meeting, receipt,
                        loading, information, busy, pending, unreadable, message,
                        { backs++ }, { refreshes++ }, { reviews++ }, { scans++ }, { resolutions++ }, { retries++ })
                }
            }
        }
        if (restorer == null) compose.setContent(content) else restorer.setContent(content)
    }

    @Test fun readyAndSuccessKeepContextAndOnePinnedAction() {
        launch()
        val scanBounds = compose.onNodeWithTag("cloud_scan_checkin").fetchSemanticsNode().boundsInRoot
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(rootBounds.center.x, scanBounds.center.x, 1f)
        compose.onNodeWithTag("cloud_scan_checkin").assertWidthIsEqualTo(288.dp).assertHeightIsEqualTo(52.dp)
        compose.onNodeWithTag("cloud_scan_checkin").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, scans)
        compose.onNodeWithText("Location verified").assertDoesNotExist()
        compose.onNodeWithText("Be within 200 m of the meeting point. Location is checked when you scan.").assertIsDisplayed()
        capture("bob-ready.png")
        compose.onNodeWithTag("cloud_checkin_meeting_notes").performClick()
        compose.onNodeWithText(event.meetingNotes).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cloud_checkin_meeting_notes").performClick()
        compose.onNodeWithTag("cloud_tab_reviews").performScrollTo().performClick()
        assertEquals(1, reviews)
        compose.runOnIdle { receipt = clock }
        compose.onNodeWithTag("cloud_checkin_event").assertTextEquals(event.title)
        compose.onNodeWithTag("cloud_scan_checkin").assertDoesNotExist()
        compose.onNodeWithTag("cloud_checkin_receipt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cloud_result_meeting").assertTextContains("Sports centre east entrance", substring = true)
        compose.onNodeWithText("Hosted by Alice").assertIsDisplayed()
        compose.onNodeWithTag("cloud_checkin_done").assertIsDisplayed().assertTextEquals("Done")
        val doneBounds = compose.onNodeWithTag("cloud_checkin_done").fetchSemanticsNode().boundsInRoot
        assertEquals(scanBounds.width, doneBounds.width, 1f)
        assertEquals(scanBounds.height, doneBounds.height, 1f)
        assertEquals(scanBounds.center.x, doneBounds.center.x, 1f)
        capture("bob-success.png")
        compose.onNodeWithTag("cloud_checkin_done").performClick()
        compose.onNodeWithTag("cloud_checkin_result").assertDoesNotExist()
        compose.onNodeWithTag("cloud_participant_checkin").assertIsDisplayed()
        compose.onNodeWithTag("cloud_checkin_receipt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cloud_scan_checkin").assertDoesNotExist()
        assertEquals("Done must stay on E's scan entry", 0, backs)
        capture("bob-after-done.png")
        val resultBounds = compose.onNodeWithTag("cloud_checkin_view_result").fetchSemanticsNode().boundsInRoot
        assertEquals(doneBounds.width, resultBounds.width, 1f)
        assertEquals(doneBounds.height, resultBounds.height, 1f)
        assertEquals(doneBounds.center.x, resultBounds.center.x, 1f)
        compose.onNodeWithTag("cloud_checkin_view_result").performClick()
        compose.onNodeWithTag("cloud_checkin_result").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("cloud_checkin_result").assertDoesNotExist()
        assertEquals(0, backs)
        compose.onNodeWithTag("cloud_checkin_back").performClick()
        assertEquals(1, backs)
    }

    @Test fun smallPhoneWithLargeTextKeepsActionAndFullContentReachable() {
        launch(width = 320, height = 568, fontScale = 1.5f)
        compose.onNodeWithTag("cloud_scan_checkin").assertIsDisplayed()
        compose.onNodeWithTag("cloud_checkin_meeting_notes").performScrollTo().performClick()
        compose.onNodeWithText(event.meetingNotes).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cloud_participant_status").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cloud_scan_checkin").assertIsDisplayed()
        capture("bob-small-large-text.png")
    }

    @Test fun landscapeKeepsScanAndBackReachable() {
        launch(width = 640, height = 360)
        compose.onNodeWithTag("cloud_scan_checkin").assertIsDisplayed()
        compose.onNodeWithTag("cloud_checkin_back").assertIsDisplayed().performClick()
        assertEquals(1, backs)
        compose.onNodeWithTag("cloud_participant_status").performScrollTo().assertIsDisplayed()
    }

    @Test fun smallResultKeepsFullActivityMeetingNotesAndDoneReachable() {
        event = event.copy(title = "Campus badminton meetup for beginners and returning players",
            meetingLocation = "Sports centre east entrance next to the student information desk")
        receipt = clock
        launch(width = 320, height = 568, fontScale = 1.5f)
        compose.onNodeWithTag("cloud_checkin_event").performScrollTo().assertTextEquals(event.title)
        compose.onNodeWithTag("cloud_result_meeting").performScrollTo().assertTextContains(event.meetingLocation!!, substring = true)
        compose.onNodeWithTag("cloud_checkin_meeting_notes").performScrollTo().performClick()
        compose.onNodeWithText(event.meetingNotes).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cloud_checkin_done").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("cloud_checkin_view_result").assertIsDisplayed()
        assertEquals(0, backs)
    }

    @Test fun landscapeResultHasReachableDoneAndReturnsToItsEntry() {
        receipt = clock
        launch(width = 640, height = 360)
        compose.onNodeWithTag("cloud_checkin_receipt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cloud_checkin_done").performScrollTo().performClick()
        compose.onNodeWithTag("cloud_checkin_view_result").assertIsDisplayed()
        compose.onNodeWithTag("cloud_scan_checkin").assertDoesNotExist()
        assertEquals(0, backs)
    }

    @Test fun resultBackAndRestorationPreserveDismissalWithoutClearingAttendance() {
        receipt = clock
        val restorer = StateRestorationTester(compose)
        launch(restorer = restorer)
        compose.onNodeWithTag("cloud_checkin_result_back").performClick()
        restorer.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("cloud_checkin_result").assertDoesNotExist()
        compose.onNodeWithTag("cloud_checkin_receipt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cloud_scan_checkin").assertDoesNotExist()
        compose.onNodeWithTag("cloud_checkin_view_result").performClick()
        compose.onNodeWithTag("cloud_checkin_done").assertIsDisplayed()
        assertEquals(clock, receipt)
        assertEquals(0, backs)
    }

    @Test fun resultFallsBackToActivityMeetingAndResetsForAnotherEvent() {
        event = event.copy(meetingLocation = null, meetingAt = null)
        receipt = clock
        launch()
        compose.onNodeWithTag("cloud_result_meeting").assertTextContains(event.location, substring = true)
        compose.onNodeWithTag("cloud_checkin_done").performClick()
        compose.runOnIdle { event = event.copy(id = "another-event", title = "Weekend study circle") }
        compose.onNodeWithTag("cloud_checkin_result").assertIsDisplayed()
        compose.onNodeWithTag("cloud_checkin_event").assertTextEquals("Weekend study circle")
    }

    @Test fun loadingFailureAndMeetingRefreshDoNotOfferFalseReadiness() {
        loading = true; information = false
        launch()
        compose.onNodeWithTag("cloud_scan_checkin").assertIsNotEnabled()
        compose.runOnIdle { loading = false; message = "Connection unavailable. Please try again." }
        compose.onNodeWithTag("cloud_checkin_retry_load").assertIsDisplayed().performClick()
        assertEquals(1, refreshes)
        compose.onNodeWithTag("cloud_scan_checkin").assertDoesNotExist()
        compose.runOnIdle { information = true; meeting = false; message = "" }
        compose.onNodeWithTag("cloud_participant_status").assertTextEquals("Waiting for the organizer")
        compose.onNodeWithTag("cloud_scan_checkin").assertIsNotEnabled()
        compose.runOnIdle { meeting = true }
        compose.onNodeWithTag("cloud_scan_checkin").assertIsEnabled()
    }

    @Test fun uncertainAndUnreadableAttemptsNeverEnableAnotherScan() {
        pending = true
        message = "Connection lost. Your check-in result is not confirmed."
        launch()
        compose.onNodeWithTag("cloud_scan_checkin").assertDoesNotExist()
        compose.onNodeWithTag("cloud_resolve_checkin").assertIsDisplayed().performClick()
        compose.onNodeWithTag("cloud_retry_checkin").assertIsDisplayed().performClick()
        assertEquals(1, resolutions); assertEquals(1, retries)
        compose.runOnIdle { busy = true }
        compose.onNodeWithTag("cloud_resolve_checkin").assertIsNotEnabled()
        compose.onNodeWithTag("cloud_retry_checkin").assertIsNotEnabled()
        compose.runOnIdle { busy = false; pending = false; unreadable = true }
        compose.onNodeWithTag("cloud_scan_checkin").assertIsNotEnabled()
        compose.onNodeWithTag("cloud_participant_status").assertTextEquals("Check-in needs attention")
        assertEquals(0, scans)
    }

    @Test fun eligibilityAndTimeAvailabilityMatchExistingRules() {
        launch()
        for (status in listOf(null, RegistrationStatus.WAITLISTED, RegistrationStatus.CANCELLED, RegistrationStatus.REJECTED)) {
            compose.runOnIdle { registration = status }
            compose.onNodeWithTag("cloud_scan_checkin").assertIsNotEnabled()
        }
        compose.runOnIdle { registration = RegistrationStatus.CONFIRMED; now = CheckInWindow.opensAt(event) - 1 }
        compose.onNodeWithTag("cloud_scan_checkin").assertIsNotEnabled()
        compose.runOnIdle { now++ }
        compose.onNodeWithTag("cloud_scan_checkin").assertIsEnabled()
        compose.runOnIdle { now = CheckInWindow.closesAt(event) }
        compose.onNodeWithTag("cloud_scan_checkin").assertIsEnabled()
        compose.runOnIdle { now++ }
        compose.onNodeWithTag("cloud_scan_checkin").assertIsNotEnabled()
        compose.runOnIdle { now = clock; event = event.copy(status = EventStatus.CANCELLED) }
        compose.onNodeWithTag("cloud_scan_checkin").assertIsNotEnabled()
        compose.runOnIdle { event = event.copy(status = EventStatus.COMPLETED) }
        compose.onNodeWithTag("cloud_scan_checkin").assertIsNotEnabled()
    }

    private class Backend(val clock: Long) : CloudOperations {
        override val storage: FirebaseStorage get() = error("No image operation in this test")
        override val sync = MutableStateFlow(CloudSyncState())
        var receipt: Map<String, Any?>? = null
        var fail = false
        var requests = 0
        override suspend fun refresh() = Unit
        override suspend fun call(action: String, payload: Map<String, Any?>, requestId: String?): Map<String, Any?> = when (action) {
            "eventInfo" -> {
                requests++
                if (fail) error("Connection unavailable. Please try again.")
                mapOf("serverNow" to clock, "meeting" to mapOf("latitude" to 31.27), "receipt" to receipt, "ownFeedback" to null)
            }
            "reviews" -> mapOf("reviews" to emptyList<Any>())
            else -> error("Unexpected action: $action")
        }
    }

    @Test fun serverReceiptRefreshAndAccountSwitchUseExistingDataFlow() {
        val registrations = listOf("bob", "carol").map { user ->
            base.registrations.first().copy(id = UUID.randomUUID().toString(), eventId = event.id, userId = user,
                status = if (user == "bob") RegistrationStatus.CONFIRMED else RegistrationStatus.WAITLISTED)
        }
        val repo = FakeGogetherRepository(base.copy(currentUserId = "bob", events = listOf(event), registrations = registrations))
        val backend = Backend(clock)
        compose.setContent { GogetherTheme { CloudCheckInScreen(repo, backend, event.id, clock, {}) } }
        compose.onNodeWithTag("cloud_scan_checkin").assertIsEnabled()
        compose.runOnIdle { backend.receipt = mapOf("checkedInAt" to clock) }
        compose.onNodeWithTag("cloud_checkin_refresh").performClick()
        compose.onNodeWithTag("cloud_checkin_receipt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cloud_scan_checkin").assertDoesNotExist()
        compose.onNodeWithTag("cloud_checkin_done").performScrollTo().performClick()
        compose.onNodeWithTag("cloud_checkin_refresh").performClick()
        compose.onNodeWithTag("cloud_checkin_result").assertDoesNotExist()
        compose.onNodeWithTag("cloud_checkin_view_result").assertIsDisplayed()
        compose.runOnIdle { backend.receipt = null; runBlocking { repo.login("carol").getOrThrow() } }
        compose.onNodeWithTag("cloud_participant_status").performScrollTo().assertTextEquals("You're on the waitlist")
        compose.onNodeWithTag("cloud_checkin_receipt").assertDoesNotExist()
        compose.onNodeWithTag("cloud_scan_checkin").assertIsNotEnabled()
        compose.onNodeWithTag("cloud_tab_reviews").performScrollTo().performClick()
        compose.onNodeWithTag("cloud_tab_checkin").performScrollTo().performClick()
        compose.onNodeWithTag("cloud_participant_status").performScrollTo().assertTextEquals("You're on the waitlist")
    }

    @Test fun serverLoadFailureCanRetryThroughRealScreen() {
        val registration = base.registrations.first().copy(eventId = event.id, userId = "bob", status = RegistrationStatus.CONFIRMED)
        val repo = FakeGogetherRepository(base.copy(currentUserId = "bob", events = listOf(event), registrations = listOf(registration)))
        val backend = Backend(clock).apply { fail = true }
        compose.setContent { GogetherTheme { CloudCheckInScreen(repo, backend, event.id, clock, {}) } }
        compose.onNodeWithTag("cloud_checkin_retry_load").assertIsDisplayed()
        compose.runOnIdle { backend.fail = false }
        compose.onNodeWithTag("cloud_checkin_retry_load").performClick()
        compose.onNodeWithTag("cloud_scan_checkin").assertIsDisplayed().assertIsEnabled()
        assertTrue(backend.requests >= 2)
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "e-ui-polish").apply { mkdirs() }
        File(folder, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

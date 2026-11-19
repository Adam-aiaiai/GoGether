package com.gogether.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.gogether.app.core.data.*
import com.gogether.app.core.model.RegistrationStatus
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MemberCEIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = PreferencesPrototypeStore(context)
    private var sessionId: String? = null
    private val time = System.currentTimeMillis()
    private val fix = PositionFix(MeetingPoint(31.25, 120.7), 5.0, 1000)
    private fun fixture(): Pair<FakeGogetherRepository, RegistrationAttendanceBridge> {
        val seed = DemoData.create(time).let { s -> s.copy(currentUserId = "alice", events = s.events.map {
            if (it.id == "event-1") it.copy(createdAt = time + 173, startAt = time + 600_000,
                endAt = time + 7_800_000, registrationDeadline = time + 300_000) else it
        }) }
        val repo = FakeGogetherRepository(seed, { time })
        val bridge = RegistrationAttendanceBridge({ repo.state.value }, store, { time }, { 1000 })
        sessionId = bridge.prepare("alice", "event-1", fix).id
        return repo to bridge
    }
    @After fun cleanupOnlyThisTestsRecords() {
        sessionId?.let { id ->
            val prefs = context.getSharedPreferences("checkin_prototype_v1", Context.MODE_PRIVATE)
            val edit = prefs.edit()
            prefs.all.keys.filter { it.contains(id) }.forEach { edit.remove(it) }
            edit.commit()
        }
    }
    @Test fun cAvatarEntryOpensBobsBoundEPageAndShowsSavedCheckIn() {
        val (repo, bridge) = fixture()
        val session = bridge.session(repo.state.value.event("event-1")!!)!!
        val raw = PrototypeCodeCodec.encode(bridge.backend.issue("alice", session, fix))
        runBlocking { repo.login("bob").getOrThrow() }
        // Exercise the same backend the scanner calls; real camera/GPS remains a device test.
        bridge.backend.submit("bob", raw, fix)
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
        compose.onNodeWithTag("event_event-1").performScrollTo().performClick()
        compose.onNodeWithTag("participants_toggle").performScrollTo().performClick()
        compose.onNodeWithTag("participant_name_bob", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("open_checkin").performScrollTo().performClick()
        compose.onNodeWithTag("participant_event_title").assertTextEquals("Badminton, anyone?")
        compose.onNodeWithTag("participant_status_title").assertTextEquals("Checked in")
        compose.onNodeWithTag("demo_checkin").assertDoesNotExist()
        compose.onNodeWithTag("participant_panel").assertIsDisplayed()
    }
    @Test fun organizerManagementAndEAttendanceUseTheSameReceiptAndPromotedRoster() {
        val (repo, bridge) = fixture()
        val session = bridge.session(repo.state.value.event("event-1")!!)!!
        val raw = PrototypeCodeCodec.encode(bridge.backend.issue("alice", session, fix))
        runBlocking { repo.login("bob").getOrThrow(); repo.cancelRegistration("event-1").getOrThrow(); repo.login("emma").getOrThrow() }
        bridge.backend.submit("emma", raw, fix)
        runBlocking { repo.login("alice").getOrThrow() }
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
        compose.onNodeWithTag("event_event-1").performScrollTo().performClick()
        compose.onNodeWithTag("participants_toggle").performScrollTo().performClick()
        compose.onNodeWithTag("participant_name_bob", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("participant_name_emma", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("manage_event").performScrollTo().performClick()
        compose.onNodeWithText("1 checked in", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("manage_attendance").performScrollTo().performClick()
        compose.onNodeWithTag("organizer_panel").assertIsDisplayed()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("attendance_search_open").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("attendance_search_open").assertIsEnabled()
        assertEquals(RegistrationStatus.CONFIRMED, repo.state.value.registration("event-1", "emma")!!.status)
        assertEquals("emma", bridge.backend.attendance("alice", session).single { it.checkedInAt != null }.userId)
    }
}

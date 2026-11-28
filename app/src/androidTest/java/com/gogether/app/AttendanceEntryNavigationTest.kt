package com.gogether.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.gogether.app.core.data.*
import com.gogether.app.core.model.EventStatus
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AttendanceEntryNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val time = System.currentTimeMillis()
    private val store = PreferencesPrototypeStore(context)
    private val drafts = FeedbackDraftStore(context)
    private var recordId: String? = null

    private fun repository(status: EventStatus): FakeGogetherRepository {
        val seed = DemoData.create(time)
        val event = seed.event("event-1")!!.copy(
            createdAt = System.nanoTime(), status = status,
            startAt = if (status == EventStatus.COMPLETED) time + 600_000 else time - 7_800_000,
            endAt = if (status == EventStatus.COMPLETED) time + 7_800_000 else time - 600_000,
        )
        val repo = FakeGogetherRepository(seed.copy(currentUserId = "alice", events = listOf(event)), { time })
        recordId = RegistrationAttendanceBridge({ repo.state.value }, store).key(event)
        return repo
    }

    @After fun cleanup() {
        recordId?.let { id ->
            val prefs = context.getSharedPreferences("checkin_prototype_v1", Context.MODE_PRIVATE)
            val edit = prefs.edit()
            prefs.all.keys.filter { it.contains(id) }.forEach(edit::remove)
            edit.commit()
        }
    }

    private fun openManagement(repo: FakeGogetherRepository) {
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
        compose.onNodeWithTag("nav_profile").performClick()
        compose.onNodeWithTag("history_event-1").performScrollTo().performClick()
        compose.onNodeWithTag("manage_event").performScrollTo().performClick()
        compose.onNodeWithTag("manage_attendance").performScrollTo().performClick()
    }

    private fun assertDirectAttendance() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Attendance record").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Attendance record").assertIsDisplayed()
        compose.onNodeWithTag("feedback_submit").assertDoesNotExist()
        compose.onNodeWithText("Event review").assertDoesNotExist()
    }

    @Test fun completedManagementOpensAttendanceAndBackReturnsToManagementWithoutTouchingReviewDraft() {
        val repo = repository(EventStatus.COMPLETED)
        val draft = FeedbackDraft(4, "I will finish my review later.",
            FeedbackDetails(anonymous = true, visibility = FeedbackVisibility.ORGANIZER_ONLY))
        drafts.save("alice", recordId!!, draft)
        openManagement(repo)
        assertDirectAttendance()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("manage_attendance").assertExists()
        assertEquals(draft, drafts.load("alice", recordId!!))
        assertNull(store.feedback(recordId!!, "alice"))
        Espresso.pressBack()
        compose.onNodeWithTag("open_checkin").performScrollTo().performClick()
        compose.onNodeWithTag("feedback_submit").assertDoesNotExist()
        compose.onNodeWithTag("history_detail").performScrollToNode(hasText("You did not check in. A valid check-in is required to review this event."))
        compose.onNodeWithText("You did not check in. A valid check-in is required to review this event.").assertIsDisplayed()
        assertEquals(draft, drafts.load("alice", recordId!!))
        assertNull(store.feedback(recordId!!, "alice"))
    }

    @Test fun elapsedEndTimeUsesAttendanceEntryAndSystemBackReturnsToManagement() {
        openManagement(repository(EventStatus.OPEN))
        assertDirectAttendance()
        Espresso.pressBack()
        compose.onNodeWithTag("manage_attendance").assertExists()
        compose.onNodeWithTag("feedback_submit").assertDoesNotExist()
    }

    @Test fun cancelledManagementKeepsHistoricalAttendanceReadable() {
        openManagement(repository(EventStatus.CANCELLED))
        assertDirectAttendance()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("manage_attendance").assertExists()
    }

    @Test fun attendanceEntryRejectsParticipantAndHidesRosterAfterAccountSwitch(): Unit = runBlocking {
        val repo = repository(EventStatus.COMPLETED)
        compose.setContent { GogetherTheme {
            RepositoryCheckInScreen(repo, "event-1", time, {}, attendanceOnly = true)
        } }
        assertDirectAttendance()
        repo.login("bob").getOrThrow()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Only this event's organizer can view attendance.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Attendance record").assertDoesNotExist()
        compose.onNodeWithTag("feedback_submit").assertDoesNotExist()
    }
}

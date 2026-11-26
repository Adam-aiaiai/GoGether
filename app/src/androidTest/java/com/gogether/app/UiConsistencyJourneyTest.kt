package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.*
import com.gogether.app.core.data.cloud.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.cloud.*
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real composables with isolated fixtures: screenshots never publish to a live account. */
@OptIn(ExperimentalTestApi::class)
class UiConsistencyJourneyTest {
    @get:Rule val compose = createComposeRule()
    private val clock = System.currentTimeMillis()

    private fun capture(name: String) {
        compose.waitForIdle()
        // Let the platform touch ripple finish before recording the selected colour.
        android.os.SystemClock.sleep(500)
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "ui-consistency").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun tap(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (!tag.startsWith("nav_")) node.performScrollTo()
        node.performClick()
    }
    private fun phone(content: @Composable () -> Unit) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 860.dp)) then
                DeviceConfigurationOverride.FontScale(1f)) { GogetherTheme(content) }
        }
    }

    @Test fun sharedHeadersPreserveNavigationFiltersAndUnsavedProfileChanges() {
        val original = DemoData.create(clock).copy(currentUserId = "alice",
            questions = listOf(EventQuestion("ui-question", "event-1", "bob", "Can I borrow a racket?", clock - 120_000,
                "Of course. We have two spare rackets, and all levels are welcome.", "alice", clock - 60_000)),
            notifications = listOf(Notification("ui-notice", "alice", "event-1", NotificationType.QUESTION_ASKED,
                "Bob asked a question about Badminton, anyone? Can I borrow a racket?", clock - 120_000,
                questionId = "ui-question")))
        val repo = FakeGogetherRepository(original, { clock })
        phone { GogetherApp(repo) }
        tap("category_sports")
        compose.onNodeWithTag("event_event-2").assertDoesNotExist()
        capture("01-explore")
        tap("nav_create")
        capture("02-create-event")
        tap("nav_profile")
        tap("edit_profile")
        compose.onAllNodesWithText("Edit profile").assertCountEquals(1)
        compose.onNodeWithTag("interest_STUDY").performClick().assertIsSelected()
        capture("03-edit-profile")
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(original.currentUser!!.interests, repo.state.value.currentUser!!.interests)
        tap("nav_explore")
        tap("event_event-1")
        compose.onNodeWithText("Who's coming").performScrollTo()
        capture("04-event-details")
        compose.onNodeWithTag("open_questions").performScrollTo()
        capture("04b-event-actions")
        tap("open_questions")
        compose.onAllNodesWithText("Questions & answers").assertCountEquals(1)
        compose.onNodeWithTag("answer_ui-question").assertExists()
        capture("05-questions")
        tap("questions_unanswered")
        compose.onNodeWithTag("answer_ui-question").assertDoesNotExist()
        tap("questions_all")
        compose.onNodeWithTag("answer_ui-question").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("open_questions").assertExists()
        tap("nav_my-events")
        capture("05b-my-events")
        tap("open_inbox")
        capture("06-notifications")
        tap("notification_open_ui-notice")
        compose.onAllNodesWithText("Questions & answers").assertCountEquals(1)
        compose.onNodeWithTag("question_ui-question").assertExists()
        assertEquals(original.questions, repo.state.value.questions)
    }

    private class GalleryBackend(private val clock: Long) : CloudOperations {
        override val storage: FirebaseStorage get() = error("No photo upload in this visual journey")
        override val sync = MutableStateFlow(CloudSyncState())
        override suspend fun refresh() = Unit
        override suspend fun call(action: String, payload: Map<String, Any?>, requestId: String?): Map<String, Any?> = when(action) {
            "eventInfo" -> mapOf("serverNow" to clock, "meeting" to null, "receipt" to null, "ownFeedback" to null)
            "reviews" -> mapOf("reviews" to emptyList<Any>())
            "attendance" -> mapOf("attendees" to listOf(
                mapOf("userId" to "bob", "name" to "Bob", "checkedInAt" to clock - 10_800_000),
                mapOf("userId" to "emma", "name" to "Emma", "checkedInAt" to clock - 10_740_000),
                mapOf("userId" to "david", "name" to "David", "checkedInAt" to 0L)))
            else -> error("This journey must not submit: $action")
        }
    }

    @Test fun cloudReviewAndAttendanceKeepSelectionsAndEventBinding() {
        val base = DemoData.create(clock)
        val id = "ui-style-${UUID.randomUUID()}"
        val event = base.event("event-history")!!.copy(id = id, title = "Campus badminton meetup", category = Category.SPORTS,
            startAt = clock - 10_800_000, endAt = clock - 3_600_000,
            location = "Sports Centre, Court 3", meetingLocation = "Sports Centre, east entrance")
        val registrations = base.registrations.filter { it.eventId == "event-history" }.map { it.copy(eventId = id) }
        val repo = FakeGogetherRepository(base.copy(currentUserId = "bob", events = listOf(event), registrations = registrations))
        val backend = GalleryBackend(clock)
        var roster by mutableStateOf(false)
        phone {
            if (roster) CloudCheckInScreen(FakeGogetherRepository(base.copy(currentUserId = "alice", events = listOf(event), registrations = registrations)),
                backend, id, clock, {}, attendanceOnly = true)
            else CloudCheckInScreen(repo, backend, id, clock, {})
        }
        try {
            compose.onNodeWithTag("feedback_rating_5").performScrollTo().performClick()
            capture("07-review-rating")
            compose.reviewNode("feedback_tag_welcoming").performScrollTo().performClick().assertIsSelected()
            compose.reviewNode("feedback_tag_well_organized").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithText("What went well").performScrollTo()
            capture("08-review-tags")
            compose.reviewNode("cloud_feedback_comment").performScrollTo().performTextReplacement("Friendly people and a well-organized session. I would join again.")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.reviewNode("cloud_feedback_comment").performScrollTo()
            capture("09-review-comment")
            assertTrue(repo.state.value.feedback.isEmpty())
            compose.runOnIdle { roster = true }
            compose.onNodeWithTag("cloud_attendance_count").performScrollTo().assertTextEquals("2 of 3 checked in")
            capture("10-attendance")
            compose.onNodeWithText("Search participants").performTextInput("Bob")
            compose.onNodeWithText("Emma").assertDoesNotExist()
        } finally {
            CloudFeedbackDraftStore(InstrumentationRegistry.getInstrumentation().targetContext).clear("bob", id)
        }
    }

    @Test fun resultRetainsDoneActionOnPhoneAndTablet() {
        val event = DemoData.create(clock).events.first().copy(startAt = clock - 600_000, endAt = clock + 6_600_000)
        var width by mutableIntStateOf(400)
        var done = 0
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 860.dp))) {
                GogetherTheme { CloudCheckInResultContent(event, "Bob", "Alice", clock, { done++ }) }
            }
        }
        compose.onNodeWithTag("cloud_checkin_done").performScrollTo().assertWidthIsEqualTo(320.dp)
        capture("11-done-phone")
        compose.onNodeWithTag("cloud_checkin_done").performClick()
        compose.runOnIdle { width = 840 }
        compose.onNodeWithTag("cloud_checkin_done").performScrollTo().assertWidthIsEqualTo(672.dp)
        capture("12-done-tablet")
        compose.onNodeWithTag("cloud_checkin_result_back").performClick()
        compose.runOnIdle { assertEquals(2, done) }
    }
}

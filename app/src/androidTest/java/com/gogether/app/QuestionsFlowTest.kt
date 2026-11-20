package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuestionsFlowTest {
    @get:Rule val compose = createComposeRule()
    private fun launch(repo: GogetherRepository) { compose.setContent { GogetherTheme { GogetherApp(repo) } } }
    private fun settle() {
        val start = compose.mainClock.currentTime
        while (compose.onAllNodesWithTag("snackbar").fetchSemanticsNodes().isNotEmpty()) {
            check(compose.mainClock.currentTime - start < 15_000)
            compose.mainClock.advanceTimeBy(100)
        }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        settle()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "d-qa-ui").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun tap(tag: String) {
        settle()
        val node = compose.onNodeWithTag(tag)
        if (!tag.startsWith("nav_")) node.performScrollTo()
        node.performClick()
    }
    private fun text(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
        closeSoftKeyboard()
    }
    private fun switch(user: String) { tap("nav_profile"); tap("switch_$user") }
    private fun inbox() { tap("nav_my-events"); tap("open_inbox") }

    @Test fun prospectiveUserAsksOrganizerAnswersAndNotificationOpensTheQuestion() {
        val repo = FakeGogetherRepository(DemoData.create().copy(currentUserId = "finn"))
        launch(repo)
        tap("event_event-1"); tap("open_questions")
        compose.onNodeWithTag("post_question").assertIsNotEnabled()
        text("question_input", "Can I borrow a racket?"); tap("post_question")
        compose.waitUntil(5_000) { repo.state.value.questions.size == 1 }
        val q = repo.state.value.questions.single()
        compose.onNodeWithTag("question_input").assertTextContains("")
        switch("alice"); inbox()
        val questionNotice = repo.state.value.notifications.single { it.type == NotificationType.QUESTION_ASKED }
        tap("notification_open_${questionNotice.id}")
        compose.onNodeWithTag("question_${q.id}").performScrollTo().assertIsDisplayed()
        tap("reply_${q.id}"); text("answer_input", "Yes, spare rackets are available."); tap("send_answer_${q.id}")
        compose.waitUntil(5_000) { repo.state.value.questions.single().answer != null }
        switch("finn"); inbox()
        val answerNotice = repo.state.value.notifications.single { it.type == NotificationType.QUESTION_ANSWERED }
        tap("notification_read_${answerNotice.id}"); tap("notification_open_${answerNotice.id}")
        compose.onNodeWithTag("answer_${q.id}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Yes, spare rackets are available.").assertIsDisplayed()
        compose.onNodeWithTag("reply_${q.id}").assertDoesNotExist()
        capture("question-answer")
        assertTrue(repo.state.value.notifications.single { it.id == answerNotice.id }.isRead)
        assertFalse(repo.state.value.notifications.single { it.id == questionNotice.id }.isRead)
    }

    @Test fun ordinaryEventEditorNotifiesParticipantsAndInboxOpensUpdatedDetails() {
        val repo = FakeGogetherRepository(DemoData.create().copy(currentUserId = "alice"))
        launch(repo)
        tap("event_event-1"); tap("manage_event"); tap("edit_event")
        text("edit_location", "North gym entrance"); tap("save_event")
        compose.waitUntil(5_000) { repo.state.value.event("event-1")?.location == "North gym entrance" }
        assertEquals(5, repo.state.value.notifications.count { it.type == NotificationType.EVENT_UPDATED })
        switch("bob"); inbox()
        val notice = repo.state.value.notifications.single { it.userId == "bob" }
        compose.onNodeWithText("North gym entrance", substring = true).performScrollTo().assertIsDisplayed()
        capture("activity-change-notice")
        tap("notification_open_${notice.id}")
        compose.onAllNodesWithText("North gym entrance", substring = true)[0].performScrollTo().assertIsDisplayed()
    }

    @Test fun failedQuestionRetainsDraftAndBlocksRepeatedSubmission() {
        val base = FakeGogetherRepository(DemoData.create().copy(currentUserId = "finn"))
        val pending = CompletableDeferred<Result<Unit>>()
        var attempts = 0
        val repo = object : GogetherRepository by base {
            override suspend fun askQuestion(eventId: String, content: String): Result<Unit> { attempts++; return pending.await() }
        }
        launch(repo); tap("event_event-1"); tap("open_questions")
        text("question_input", "Please keep my draft"); tap("post_question")
        compose.onNodeWithTag("post_question").assertIsNotEnabled(); assertEquals(1, attempts)
        compose.runOnIdle { pending.complete(Result.failure(IllegalStateException("Could not save. Try again."))) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Ask organizer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("question_input").assertTextContains("Please keep my draft")
        assertTrue(base.state.value.questions.isEmpty())
    }

    @Test fun failedAnswerRetainsDraftAndExistingQuestion() {
        val base = FakeGogetherRepository()
        runBlocking { base.login("finn").getOrThrow(); base.askQuestion("event-1", "What should I bring?").getOrThrow(); base.login("alice").getOrThrow() }
        val id = base.state.value.questions.single().id
        val repo = object : GogetherRepository by base {
            override suspend fun answerQuestion(questionId: String, content: String) = Result.failure<Unit>(IllegalStateException("Could not save answer."))
        }
        launch(repo); tap("event_event-1"); tap("manage_event"); tap("manage_questions")
        tap("reply_$id"); text("answer_input", "Bring water and a racket"); tap("send_answer_$id")
        settle()
        compose.onNodeWithTag("answer_input").assertTextContains("Bring water and a racket")
        assertNull(base.state.value.questions.single().answer)
        assertTrue(base.state.value.notifications.none { it.type == NotificationType.QUESTION_ANSWERED })
    }

    @Test fun endedEventKeepsQuestionAndAnswerButDisablesWriting() {
        val now = System.currentTimeMillis()
        val seed = DemoData.create(now)
        val event = seed.event("event-history")!!
        val question = EventQuestion("old-question", event.id, "bob", "Old question", now - 10_000,
            "Old organizer reply", event.organizerId, now - 9_000)
        val repo = FakeGogetherRepository(seed.copy(currentUserId = "bob", questions = listOf(question)))
        launch(repo); tap("nav_my-events"); tap("personal_tab_HISTORY"); tap("event_${event.id}"); tap("open_questions")
        compose.onNodeWithText("Questions closed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("answer_old-question").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("question_input").assertDoesNotExist()
        compose.onNodeWithTag("reply_old-question").assertDoesNotExist()
    }
}

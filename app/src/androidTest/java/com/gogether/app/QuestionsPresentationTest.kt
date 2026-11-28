package com.gogether.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.Page
import com.gogether.app.feature.coordination.AnnouncementSection
import com.gogether.app.feature.profile.importProfilePhoto
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercise the redesigned lists through real navigation and repository callbacks. */
@OptIn(ExperimentalTestApi::class)
class QuestionsPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val now = System.currentTimeMillis()
    private fun fixture(): AppSnapshot {
        val seed = DemoData.create(now)
        return seed.copy(currentUserId = "alice",
            events = seed.events.map { if (it.id == "event-1") it.copy(title = "Campus badminton meetup") else it },
            questions = listOf(
                EventQuestion("bob-q", "event-1", "bob", "Can I borrow a racket?", now - 60000,
                    "Yes, we have two spare rackets.", "alice", now - 30000),
                EventQuestion("carol-q", "event-1", "carol", "Which entrance should we use?", now - 120000),
                EventQuestion("david-q", "event-1", "david", "Are beginners welcome?", now - 180000),
                EventQuestion("emma-q", "event-1", "emma", "Should we bring water?", now - 86400000,
                    "Yes, please bring your own bottle.", "alice", now - 85000000)),
            announcements = listOf(Announcement("arrival", "event-1", "alice",
                "Please arrive 10 minutes early. Meet at the east entrance.", now - 600000)),
            notifications = listOf(Notification("legacy", "bob", "event-1", NotificationType.ANNOUNCEMENT,
                "Bring water and comfortable shoes.", now - 86400000)))
    }
    private fun launch(repo: GogetherRepository, compact: Boolean = false) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(
                if (compact) DpSize(320.dp, 720.dp) else DpSize(400.dp, 860.dp)) then
                DeviceConfigurationOverride.FontScale(if (compact) 1.3f else 1f)) { GogetherTheme { GogetherApp(repo) } }
        }
    }
    private fun tap(tag: String) {
        val node = compose.onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
    }
    private fun openQuestions() {
        tap("nav_my-events"); tap("personal_tab_CREATED"); tap("event_event-1")
        tap("open_questions")
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "qa-notifications-20261128").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
            File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun filtersSwipeAndRepliesStayWithTheirQuestions() {
        launch(FakeGogetherRepository(fixture(), { now })); openQuestions()
        compose.onNodeWithTag("questions_all").assertIsSelected()
        compose.onNodeWithTag("answer_bob-q").assert(hasAnyAncestor(hasTestTag("question_thread_bob-q")))
        compose.onNodeWithTag("answer_emma-q").assert(hasAnyAncestor(hasTestTag("question_thread_emma-q")))
        capture("01-all")
        tap("questions_unanswered")
        compose.onNodeWithTag("questions_unanswered").assertIsSelected()
        compose.onNodeWithText("Unanswered (2)").assertIsDisplayed()
        compose.onNodeWithTag("question_carol-q").assertIsDisplayed()
        compose.onNodeWithTag("question_david-q").assertIsDisplayed()
        compose.onNodeWithTag("question_bob-q").assertDoesNotExist()
        capture("02-unanswered")
        compose.onNodeWithTag("questions_pager").performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("questions_all").assertIsSelected()
        compose.onNodeWithTag("answer_bob-q").assertIsDisplayed()
    }
    @Test fun savingInUnansweredUpdatesCountsAndEditingAndCancelKeepTheAnswer() {
        val repo = FakeGogetherRepository(fixture(), { now })
        launch(repo); openQuestions(); tap("questions_unanswered"); tap("reply_carol-q")
        compose.onNodeWithTag("answer_input").performScrollTo().performTextReplacement("Meet at the east entrance.")
        closeSoftKeyboard()
        compose.onNodeWithText("26/100").assertExists()
        compose.onNodeWithTag("send_answer_carol-q").performScrollTo()
        capture("03-reply-editor")
        tap("send_answer_carol-q")
        compose.waitUntil(5000) { repo.state.value.questions.first { it.id == "carol-q" }.answer != null }
        compose.onNodeWithText("Unanswered (1)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("question_carol-q").assertDoesNotExist()
        tap("questions_all"); tap("reply_carol-q")
        compose.onNodeWithTag("answer_input").assertTextContains("Meet at the east entrance.")
        compose.onNodeWithTag("send_answer_carol-q").assertIsNotEnabled()
        compose.onNodeWithTag("answer_input").performTextReplacement("Discard this draft")
        closeSoftKeyboard(); tap("cancel_answer_carol-q")
        assertEquals("Meet at the east entrance.", repo.state.value.questions.first { it.id == "carol-q" }.answer)
    }
    @Test fun bothAnnouncementEntrypointsRenderTheSameRows() {
        val repo = FakeGogetherRepository(fixture(), { now })
        launch(repo)
        tap("nav_my-events"); tap("personal_tab_CREATED"); tap("event_event-1")
        compose.onNodeWithTag("announcement_arrival").performScrollTo().assertIsDisplayed()
        capture("04-event-announcement")
        tap("manage_event")
        compose.onNodeWithTag("announcement_arrival").performScrollTo().assertIsDisplayed()
        capture("05-management-announcement")
    }
    @Test fun announcementVisibilityAndLegacyMessagesRemainUserScoped() {
        var state by mutableStateOf(fixture())
        compose.setContent { GogetherTheme { Page { AnnouncementSection(state.event("event-1")!!, state) } } }
        compose.onNodeWithTag("announcement_arrival").assertExists()
        compose.onNodeWithTag("legacy_announcement_legacy").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(currentUserId = "bob") }
        compose.onNodeWithTag("legacy_announcement_legacy").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(currentUserId = "emma") } // waitlisted participant
        compose.onNodeWithTag("announcement_arrival").assertExists()
        compose.onNodeWithTag("legacy_announcement_legacy").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(currentUserId = "finn") } // not registered
        compose.onNodeWithTag("announcement_arrival").assertDoesNotExist()
    }
    @Test fun smallScreenKeepsFullQuestionsAndImportedAvatarPreviewAccessible() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val source = File(inst.targetContext.cacheDir, "qa-avatar-source.jpg")
        inst.context.assets.open("review-badminton.jpg").use { source.writeBytes(it.readBytes()) }
        val avatar = runBlocking { importProfilePhoto(inst.targetContext, Uri.fromFile(source)) }
        source.delete()
        val seed = fixture()
        val longQuestion = "Which entrance should we use, and is there somewhere to leave our bags while we play badminton?"
        val data = seed.copy(users = seed.users.map { if (it.id == "bob")
            it.copy(displayName = "Bob Alexander Robinson", avatarUri = avatar) else it },
            questions = seed.questions.map { if (it.id == "bob-q") it.copy(content = longQuestion) else it })
        launch(FakeGogetherRepository(data, { now }), compact = true); openQuestions()
        compose.onNodeWithText(longQuestion).performScrollTo().assertIsDisplayed()
        compose.waitUntil(10000) {
            compose.onAllNodesWithContentDescription("Bob Alexander Robinson's avatar").fetchSemanticsNodes().isNotEmpty()
        }
        capture("06-small-screen")
        compose.onNodeWithContentDescription("Bob Alexander Robinson's avatar").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithContentDescription("Event photo preview").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Close photo").assertIsDisplayed().performClick()
        tap("questions_unanswered")
        compose.onNodeWithTag("question_carol-q").performScrollTo().assertIsDisplayed()
        tap("reply_carol-q")
        compose.onNodeWithTag("answer_input").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cancel_answer_carol-q").performScrollTo().assertIsDisplayed()
    }
}

package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.profile.importProfilePhoto
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class ProfileAndInboxUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = System.currentTimeMillis()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder get() = File(context.getExternalFilesDir(null), "profile-inbox-20261128").apply { mkdirs() }
    private fun fixture(): AppSnapshot {
        val base = DemoData.create(now)
        val old = base.events.first()
        val history = listOf("Campus badminton meetup", "Weekend park walk", "Board games afternoon", "Photography walk").mapIndexed { i, title ->
            old.copy(id = "history-$i", title = title, startAt = now - (i + 1) * 86400000L,
                endAt = now - (i + 1) * 86400000L + 3600000, status = EventStatus.COMPLETED,
                category = listOf(Category.SPORTS, Category.OUTDOOR, Category.BOARD_GAMES, Category.PHOTOGRAPHY)[i])
        }
        return base.copy(currentUserId = "alice", events = base.events.filter { it.statusAt(now) == EventStatus.OPEN } + history,
            users = base.users.map { if (it.id == "alice") it.copy(interests = Category.entries.toSet(),
                gender = Gender.WOMAN, showGender = true, signature = "Always up for a walk and a good game.") else it },
            questions = listOf(EventQuestion("question-profile", old.id, "bob", "Can beginners join?", now - 300000)),
            notifications = listOf(
                Notification("reply", "alice", old.id, NotificationType.QUESTION_ASKED, "Can beginners join?", now - 300000, questionId = "question-profile"),
                Notification("meeting", "alice", old.id, NotificationType.MEETING_UPDATED, "Meet beside the east entrance. The time stays the same.", now - 600000),
                Notification("event", "alice", old.id, NotificationType.EVENT_UPDATED, "The activity now starts at 14:30.", now - 1200000),
                Notification("notice", "alice", old.id, NotificationType.ANNOUNCEMENT, "Please bring water and comfortable shoes.", now - 86400000, isRead = true),
                Notification("other", "bob", old.id, NotificationType.ANNOUNCEMENT, "Another user's unread notice", now)))
    }
    private fun launch(repo: GogetherRepository) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 860.dp)) then
                DeviceConfigurationOverride.FontScale(1f)) { GogetherTheme { GogetherApp(repo) } }
        }
    }
    private fun tap(tag: String) {
        runCatching { compose.onNodeWithTag(tag).performScrollTo() }
        compose.onNodeWithTag(tag).performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
    }
    private fun capture(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val bitmap = if (dialog) InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            else compose.onRoot().captureToImage().asAndroidBitmap()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun profileEditorSavesAndHistoryUsesTheSameRows() {
        val repo = FakeGogetherRepository(fixture(), { now })
        launch(repo)
        tap("nav_profile")
        compose.onNodeWithTag("history_history-2").assertIsDisplayed()
        compose.onNodeWithText("Waitlisted").assertDoesNotExist()
        compose.onNodeWithText("Profile", useUnmergedTree = true).assertExists() // bottom navigation only
        capture("01-profile")
        tap("profile_history_more")
        compose.onNodeWithTag("history_history-3").assertIsDisplayed()
        capture("03-activity-history")
        compose.onNodeWithContentDescription("Back").performClick()
        tap("edit_profile")
        compose.onNodeWithTag("save_profile").assertIsNotEnabled()
        compose.onNodeWithTag("interest_OTHERS").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("interest_OTHERS").getUnclippedBoundsInRoot().bottom <=
            compose.onNodeWithTag("nav_profile").getUnclippedBoundsInRoot().top)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        capture("02-edit-profile")
        compose.onNodeWithTag("profile_name").performTextReplacement("Alice Park")
        tap("profile_gender")
        tap("gender_NON_BINARY")
        compose.onNodeWithTag("profile_signature").performTextReplacement("See you outdoors!")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        tap("save_profile")
        compose.waitUntil(5000) { repo.state.value.currentUser?.displayName == "Alice Park" }
        assertEquals(Gender.NON_BINARY, repo.state.value.currentUser?.gender)
        assertEquals("See you outdoors!", repo.state.value.currentUser?.signature)
    }
    @Test fun photoPreviewIsCenteredAndImportSurvivesSourceRemoval() {
        val source = File(context.cacheDir, "profile-import-source.jpg")
        val fixtureImage = Bitmap.createBitmap(360, 240, Bitmap.Config.ARGB_8888)
        fixtureImage.eraseColor(android.graphics.Color.rgb(45, 120, 90))
        source.outputStream().use { fixtureImage.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val durable = runBlocking { importProfilePhoto(context, android.net.Uri.fromFile(source)) }
        source.delete()
        assertTrue(File(android.net.Uri.parse(durable).path!!).exists())
        val repo = FakeGogetherRepository(fixture(), { now })
        launch(repo)
        tap("nav_profile")
        // The bundled cover uses the same gallery as the user's uploaded photos.
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Profile cover").fetchSemanticsNodes().isNotEmpty() }
        tap("profile_cover")
        compose.onNodeWithTag("feedback_photo_preview").assertExists()
        capture("06-cover-preview", true)
        compose.onNodeWithContentDescription("Close photo").performClick()
        compose.onNodeWithTag("edit_profile").assertIsDisplayed()
    }
    @Test fun fewerInterestsAndHistoryKeepSectionPositionsFixed() {
        val initial = fixture()
        val data = MutableStateFlow(initial)
        val local = FakeGogetherRepository(initial, { now })
        val repo = object : GogetherRepository by local { override val state = data }
        launch(repo)
        tap("nav_profile")
        val before = compose.onNodeWithTag("profile_history_more").getUnclippedBoundsInRoot()
        compose.runOnIdle {
            data.value = initial.copy(users = initial.users.map { if (it.id == "alice")
                it.copy(interests = setOf(Category.SPORTS), signature = "") else it },
                events = initial.events.filter { !it.id.startsWith("history-") || it.id == "history-0" })
        }
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithTag("profile_history_more").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("history_history-0").assertIsDisplayed()
        compose.onNodeWithTag("history_history-1").assertDoesNotExist()
        capture("07-profile-sparse")
    }
    @Test fun notificationTabsSwipeAndReadActionsRemainUserScoped() {
        val repo = FakeGogetherRepository(fixture(), { now })
        launch(repo)
        tap("nav_my-events"); tap("open_inbox")
        compose.onNodeWithTag("notification_open_reply").assertIsDisplayed()
        capture("04-interactions")
        compose.onNodeWithTag("inbox_pager").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("notification_open_meeting").assertIsDisplayed()
        capture("05-activity-updates")
        tap("inbox_interactions")
        tap("notification_open_reply")
        compose.waitUntil(5000) { repo.state.value.notifications.first { it.id == "reply" }.isRead }
        compose.onNodeWithTag("question_question-profile").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        tap("read_all")
        compose.waitUntil(5000) { repo.state.value.notifications.filter { it.userId == "alice" }.all { it.isRead } }
        assertFalse(repo.state.value.notifications.first { it.id == "other" }.isRead)
        compose.onNodeWithTag("read_all").assertIsNotEnabled()
    }
}

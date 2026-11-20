package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AbdFlowTest {
    @get:Rule val compose = createComposeRule()
    private fun settle() {
        val start = compose.mainClock.currentTime
        while (compose.onAllNodesWithTag("snackbar").fetchSemanticsNodes().isNotEmpty()) {
            assertTrue(compose.mainClock.currentTime - start < 15_000)
            compose.mainClock.advanceTimeBy(100)
        }
        compose.waitForIdle()
    }
    private fun click(tag: String) {
        closeSoftKeyboard(); settle()
        val node = compose.onNodeWithTag(tag)
        if (!tag.startsWith("nav_")) node.performScrollTo()
        node.performClick()
    }
    private fun text(tag: String, value: String) = compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    private fun capture(name: String) {
        settle()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "abd-ui").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun date(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    private fun time(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

    @Test fun bsEditorAdjustsConflictingMeetingTimeAndKeepsDsMeetingPlace() {
        val repo = FakeGogetherRepository()
        runBlocking {
            repo.login("alice").getOrThrow()
            val event = repo.state.value.event("event-1")!!
            repo.updateMeeting(event.id, MeetingUpdate(event.startAt + 90 * 60_000, "South entrance", "Bring water")).getOrThrow()
        }
        val original = repo.state.value.event("event-1")!!
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
        click("nav_my-events"); click("personal_tab_CREATED"); click("event_event-1"); click("manage_event"); click("edit_event")
        val shorterEnd = original.startAt + 60 * 60_000
        text("edit_end_date", date(shorterEnd)); text("edit_end_time", time(shorterEnd))
        click("save_event")
        compose.onNodeWithTag("edit_error").assertTextContains("Meeting time is after the new end time.", substring = true)
        assertEquals(original, repo.state.value.event(original.id))
        text("edit_meeting_date", date(original.startAt)); text("edit_meeting_time", time(original.startAt))
        click("save_event")
        compose.waitUntil(5_000) { repo.state.value.event(original.id)?.endAt == shorterEnd }
        assertEquals(original.startAt, repo.state.value.event(original.id)?.meetingAt)
        assertEquals("South entrance", repo.state.value.event(original.id)?.meetingLocation)
        click("manage_event")
        compose.onNodeWithText("South entrance", substring = true).performScrollTo().assertIsDisplayed()
        capture("management")
    }

    @Test fun announcementsRenderOnceKeepLegacyMessagesAndRespectMembership() {
        val initial = DemoData.create().copy(notifications = listOf(
            Notification("legacy", "bob", "event-1", NotificationType.ANNOUNCEMENT, "Earlier AB announcement", 1, true)))
        val repo = FakeGogetherRepository(initial)
        runBlocking {
            repo.login("alice").getOrThrow()
            repo.postAnnouncement("event-1", "Bring your student card").getOrThrow()
            repo.login("bob").getOrThrow()
        }
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
        click("event_event-1")
        compose.onAllNodesWithText("Open meeting location in Maps").assertCountEquals(1)
        compose.onNodeWithText("Bring your student card").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Bring your student card").assertCountEquals(1)
        compose.onNodeWithText("Earlier AB announcement").performScrollTo().assertIsDisplayed()
        capture("announcements")
        click("nav_my-events"); click("open_inbox"); click("inbox_unread")
        compose.onNodeWithText("Earlier AB announcement").assertDoesNotExist()
        compose.onNodeWithText("Bring your student card").assertExists()
        capture("inbox")
        click("nav_profile"); click("switch_finn"); click("event_event-1")
        compose.onAllNodesWithText("Open meeting location in Maps").assertCountEquals(1)
        compose.onNodeWithText("Bring your student card").assertDoesNotExist()
        compose.onNodeWithText("Earlier AB announcement").assertDoesNotExist()
        click("nav_my-events"); click("open_inbox")
        compose.onNodeWithText("Inbox · 0 unread").assertExists()
        compose.onNodeWithText("Bring your student card").assertDoesNotExist()
    }
}

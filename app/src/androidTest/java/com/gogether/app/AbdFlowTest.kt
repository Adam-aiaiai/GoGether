package com.gogether.app

import android.graphics.Bitmap
import android.view.View
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click as viewClick
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
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
import org.hamcrest.Matcher

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
        if (tag.startsWith("switch_") || tag == "logout") compose.onNodeWithTag("profile_account_menu").performClick()

        closeSoftKeyboard(); settle()
        val node = compose.onNodeWithTag(tag)
        if (!tag.startsWith("nav_")) runCatching { node.performScrollTo() }
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
    private fun pickDate(tag: String, at: Long) {
        click(tag)
        val date = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
        onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
            override fun getDescription() = "select $date"
            override fun perform(controller: UiController, view: View) {
                (view as DatePicker).updateDate(date.year, date.monthValue - 1, date.dayOfMonth)
            }
        })
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(viewClick())
    }
    private fun pickTime(tag: String, at: Long) {
        click(tag)
        val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime()
        onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(TimePicker::class.java)
            override fun getDescription() = "select $time"
            override fun perform(controller: UiController, view: View) {
                (view as TimePicker).hour = time.hour
                view.minute = time.minute
            }
        })
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(viewClick())
    }

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
        pickDate("edit_end_date", shorterEnd); pickTime("edit_end_time", shorterEnd)
        click("save_event")
        compose.onNodeWithTag("edit_error").assertTextContains("Meeting time is after the new end time.", substring = true)
        assertEquals(original, repo.state.value.event(original.id))
        pickDate("edit_meeting_date", original.startAt); pickTime("edit_meeting_time", original.startAt)
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
        compose.onAllNodesWithText("Open meeting location in Maps").assertCountEquals(0)
        compose.onNodeWithText("Bring your student card").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Bring your student card").assertCountEquals(1)
        compose.onNodeWithText("Earlier AB announcement").performScrollTo().assertIsDisplayed()
        capture("announcements")
        click("nav_my-events"); click("open_inbox"); click("inbox_activity")
        compose.onNodeWithText("Earlier AB announcement").assertExists()
        compose.onNodeWithText("Bring your student card").assertExists()
        capture("inbox")
        click("nav_profile"); click("switch_finn"); click("event_event-1")
        compose.onAllNodesWithText("Open meeting location in Maps").assertCountEquals(0)
        compose.onNodeWithText("Bring your student card").assertDoesNotExist()
        compose.onNodeWithText("Earlier AB announcement").assertDoesNotExist()
        click("nav_my-events"); click("open_inbox")
        compose.onNodeWithTag("read_all").assertIsNotEnabled()
        compose.onNodeWithText("Bring your student card").assertDoesNotExist()
    }
}

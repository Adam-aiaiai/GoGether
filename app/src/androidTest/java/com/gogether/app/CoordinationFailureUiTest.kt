package com.gogether.app

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

class CoordinationFailureUiTest {
    @get:Rule val compose = createComposeRule()
    private fun tap(tag:String) { val node = compose.onNodeWithTag(tag); if (!tag.startsWith("nav_")) node.performScrollTo(); node.performClick() }
    @Test fun failedAnnouncementPreservesDraftAndBlocksDuplicateSubmissions() {
        val initial=DemoData.create().copy(currentUserId="alice")
        val base=FakeGogetherRepository(initial)
        val result=CompletableDeferred<Result<Unit>>()
        var attempts=0
        val repo=object:GogetherRepository by base {
            override suspend fun postAnnouncement(eventId:String,content:String):Result<Unit> { attempts++; return result.await() }
        }
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
        tap("event_event-1"); tap("manage_event")
        compose.onNodeWithText("Organizer announcement").performScrollTo().performTextInput("Keep my draft")
        closeSoftKeyboard()
        compose.onNodeWithText("Post announcement").performScrollTo().performClick()
        compose.onNodeWithText("Publishing…").assertIsNotEnabled()
        assertEquals(1,attempts)
        compose.runOnIdle { result.complete(Result.failure(IllegalStateException("Could not save. Try again."))) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Post announcement").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Keep my draft").assertExists()
        assertTrue(base.state.value.announcements.isEmpty())
    }
    @Test fun missingNotificationTargetProvidesReturnToExplore() {
        val initial=DemoData.create().copy(currentUserId="bob",notifications=listOf(
            Notification("missing","bob","deleted-event",NotificationType.ANNOUNCEMENT,"Old event update",System.currentTimeMillis())))
        val repo=FakeGogetherRepository(initial)
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
        tap("nav_my-events"); tap("open_inbox"); tap("notification_open_missing")
        compose.onNodeWithText("Event not found").assertExists()
        tap("missing_event_explore"); compose.onNodeWithTag("search").assertExists()
    }
}

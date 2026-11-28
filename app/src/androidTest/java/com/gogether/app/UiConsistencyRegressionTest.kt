package com.gogether.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.DemoData
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.coordination.*
import com.gogether.app.feature.event.CreateEventScreen
import com.gogether.app.feature.profile.EditProfileScreen
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.feature.checkin.cloud.CloudCheckInResultContent
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class UiConsistencyRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val now = System.currentTimeMillis()
    private fun capture(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(200)
        val folder = File(inst.targetContext.getExternalFilesDir(null), "ui-consistency-fixes").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun profileSaveOnlyEnablesAfterAChangeAndCancelDoesNotSave() {
        val user = User("ui-polish-user", "Alice", setOf(Category.SPORTS), now)
        var saves = 0
        var cancelled = false
        var saved: Set<Category>? = null
        compose.setContent { GogetherTheme { EditProfileScreen(user, false, { profile ->
            saves++; saved = profile.interests
        }, { cancelled = true }) } }
        compose.onNodeWithTag("save_profile").assertIsNotEnabled()
        capture("01-profile-unchanged")
        compose.onNodeWithTag("interest_STUDY").performClick()
        compose.onNodeWithTag("save_profile").assertIsEnabled()
        capture("02-profile-changed")
        compose.onNodeWithTag("save_profile").performClick()
        compose.runOnIdle { assertEquals(1, saves); assertEquals(setOf(Category.SPORTS, Category.STUDY), saved) }
        compose.onNodeWithTag("edit_profile_back").performClick()
        compose.runOnIdle { assertTrue(cancelled); assertEquals(1, saves) }
    }

    @Test fun meetingPickerFieldsRespectBusyAndCancelDiscardsEditing() {
        val event = DemoData.create(now).event("event-1")!!
        var busy by mutableStateOf(false)
        var saves = 0
        compose.setContent { GogetherTheme { Page {
            MeetingEditor(event, now, busy) { _, done -> saves++; done() }
        } } }
        compose.onNodeWithTag("edit_meeting").performClick()
        compose.onNodeWithTag("meeting_date").performScrollTo().assertHasClickAction()
        compose.onNodeWithTag("meeting_time").assertHasClickAction()
        compose.onNodeWithTag("meeting_date").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsActions.SetText))
        capture("03-meeting-fields")
        compose.runOnIdle { busy = true }
        compose.onNodeWithTag("meeting_date").assertIsNotEnabled()
        compose.onNodeWithTag("meeting_time").assertIsNotEnabled()
        compose.runOnIdle { busy = false }
        compose.onNodeWithTag("meeting_notes").performScrollTo().performTextReplacement("Please meet beside the entrance.")
        closeSoftKeyboard()
        compose.onNodeWithText("Cancel editing").performScrollTo()
        capture("04-meeting-cancel-and-heading")
        compose.onNodeWithText("Cancel editing").performClick()
        compose.onNodeWithTag("edit_meeting").assertExists()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test fun systemNightModeKeepsWhitePagesAndReadableReviewText() {
        var screen by mutableIntStateOf(0)
        var palette: androidx.compose.material3.ColorScheme? = null
        compose.setContent {
            val night = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            CompositionLocalProvider(LocalConfiguration provides night) {
                GogetherTheme {
                    val colors = MaterialTheme.colorScheme
                    SideEffect { palette = colors }
                    if (screen == 0) FeedbackEditor("polish-theme", ReviewEventSummary("Campus badminton meetup", "Alice", now),
                        FeedbackDraft(4, "Friendly group and clear directions. I'd join again."), false, "", "",
                        {}, {}, {}, {}, {}, photos = {})
                    else if (screen == 1) CreateEventScreen(false, null, {}, {})
                    else CloudCheckInResultContent(DemoData.create(now).event("event-1")!!,
                        "Bob", "Alice", now, {})
                }
            }
        }
        compose.runOnIdle {
            assertEquals(Color.White, palette!!.background)
            assertEquals(Color(0xFF26272B), palette!!.onSurface)
        }
        compose.onNodeWithText("Write a review").assertIsDisplayed()
        capture("05-night-setting-review")
        compose.runOnIdle { screen = 1 }
        compose.onNodeWithTag("create_description").performScrollTo().performTextReplacement("A friendly game for all levels.")
        closeSoftKeyboard()
        capture("06-night-setting-create")
        compose.runOnIdle { screen = 2 }
        compose.onNodeWithText("Check-in successful").assertExists()
        capture("10-night-setting-checkin-result")
    }

    @Test fun filledConfirmationReboundsBeforeClosingAndRepeatedClickRunsOnce() {
        var shown by mutableStateOf(true)
        var calls = 0
        compose.setContent { GogetherTheme {
            if (shown) SquareActionDialog("Save changes?", { shown = false }, compact = true,
                actions = { PageActionButton({ calls++; shown = false }, Modifier.testTag("confirm")) {
                    Text("Save changes", Modifier.testTag("confirm_label"))
                } }) {
                Text("Your changes will be saved.")
            }
        } }
        val button = compose.onNodeWithTag("confirm")
        val label = compose.onNodeWithTag("confirm_label", useUnmergedTree = true)
        val width = label.fetchSemanticsNode().boundsInRoot.width
        compose.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        // The scrollable dialog first distinguishes a tap from a scroll gesture.
        compose.mainClock.advanceTimeBy(320)
        assertEquals(.94f, label.fetchSemanticsNode().boundsInRoot.width / width, .01f)
        button.performTouchInput { up() }
        button.performClick() // A second accessibility click must not submit twice.
        compose.mainClock.advanceTimeBy(224)
        button.assertExists()
        assertTrue(label.fetchSemanticsNode().boundsInRoot.width > width)
        assertEquals(0, calls)
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        button.assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun replyCancelKeepsTheSavedAnswerUnchanged() {
        val seed = DemoData.create(now).copy(currentUserId = "alice", questions = listOf(
            EventQuestion("ui-polish-question", "event-1", "bob", "Can I borrow a racket?", now)))
        var answers = 0
        compose.setContent { GogetherTheme {
            QuestionsScreen(seed.event("event-1")!!, seed, now, false, null, { _, _ -> }, { _, _, _ -> answers++ })
        } }
        compose.onNodeWithTag("reply_ui-polish-question").performScrollTo().performClick()
        compose.onNodeWithText("Cancel reply").performScrollTo()
        capture("07-reply-cancel-and-heading")
        compose.onNodeWithText("Cancel reply").performClick()
        compose.onNodeWithText("Cancel reply").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, answers) }
    }
}

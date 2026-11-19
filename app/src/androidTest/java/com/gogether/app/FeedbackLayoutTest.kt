package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class FeedbackLayoutTest {
    @org.junit.After fun cleanupDraft() {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("checkin_prototype_v1", 0)
            .edit().remove(FeedbackDraftStore.key("bob", session.id)).commit()
    }
    @get:Rule val compose = createComposeRule()
    private val time = 1_800_000_000_000L
    private val session = PrototypeSession(java.util.UUID.randomUUID().toString(), MeetingPoint(31.27, 120.74), time - 7_200_000, time)
    private var record by mutableStateOf(PrototypeHistoryEntry(session, PrototypeReceipt(session.id, "bob", session.startAt), null, false))
    private var busy by mutableStateOf(false)
    private var submissions = 0
    private var failure by mutableStateOf(false)
    private var message by mutableStateOf("")

    private fun launch(scale: Float = 1f) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp)) then
                DeviceConfigurationOverride.FontScale(scale)) {
                GogetherTheme {
                    PrototypeHistoryScreen("bob", listOf(record), time, busy, message, {}, { id, rating, comment, details ->
                        submissions++
                        if (failure) message = "Could not save. Please try again."
                        else record = record.copy(feedback = PrototypeFeedback(id, "bob", rating, comment, time, details))
                    }, initialSessionId = session.id)
                }
            }
        }
    }

    @Test fun detailedReviewPreservesSelectionsAfterFailureAndSavesThemOnRetry() {
        launch()
        fun find(tag: String): SemanticsNodeInteraction {
            compose.onNodeWithTag("history_detail").performScrollToNode(hasTestTag(tag))
            return compose.onNodeWithTag(tag)
        }
        compose.onNodeWithTag("feedback_rating_4").performClick()
        capture("feedback-full-top.png")
        find("feedback_organization_5").performClick()
        find("feedback_clear_dimensions").performClick()
        find("feedback_organization_5").assertIsNotSelected().performClick()
        find("feedback_venue_3").performClick()
        find("feedback_atmosphere_4").performClick()
        compose.onNodeWithTag("history_detail").performScrollToIndex(3)
        capture("feedback-full-dimensions.png")
        find("feedback_tag_welcoming").performClick()
        find("feedback_tag_join_again").performClick().performClick().assertIsNotSelected()
        find("feedback_tag_more_guidance").performClick()
        compose.onNodeWithTag("history_detail").performScrollToIndex(8)
        capture("feedback-full-tags.png")
        find("feedback_comment").performTextInput("A little more guidance for beginners would help next time.")
        Espresso.closeSoftKeyboard()
        capture("feedback-full-comment.png")
        compose.runOnIdle { failure = true }
        compose.onNodeWithTag("feedback_submit").performClick()
        compose.onNodeWithText("Could not save. Please try again.").assertIsDisplayed()
        find("feedback_organization_5").assertIsSelected()
        find("feedback_tag_welcoming").assertIsSelected()
        find("feedback_tag_more_guidance").assertIsSelected()
        find("feedback_comment").assertTextContains("A little more guidance for beginners would help next time.")
        compose.runOnIdle { failure = false }
        compose.onNodeWithTag("feedback_submit").performClick()
        compose.onNodeWithTag("feedback_submit").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(FeedbackDetails(5, 3, 4, listOf("welcoming", "more_guidance")), record.feedback!!.details)
            assertEquals(2, submissions)
        }
        compose.onNodeWithTag("history_detail").performScrollToNode(hasText("Experience ratings"))
        capture("feedback-full-saved.png")
    }

    @Test fun compactFormHasExplicitStarsAndPinnedSubmitThenShowsSavedReview() {
        launch()
        compose.onNodeWithText("Event review").assertIsDisplayed()
        compose.onNodeWithTag("feedback_submit").assertIsDisplayed().assertIsNotEnabled()
        (1..5).forEach { compose.onNodeWithTag("feedback_rating_$it").assertIsDisplayed().assertIsNotSelected() }
        val footer = compose.onNodeWithTag("feedback_submit").fetchSemanticsNode().boundsInRoot
        capture("feedback-redesign-empty.png")
        compose.onNodeWithContentDescription("4 out of 5, Good").performClick()
        compose.onNodeWithTag("feedback_rating_label").assertTextEquals("4 / 5  Good")
        compose.onNodeWithTag("history_detail").performScrollToNode(hasTestTag("feedback_comment"))
        compose.onNodeWithTag("feedback_comment").performScrollTo().performTextInput("The directions were clear, and I had a great time with everyone.")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("history_detail").performScrollToIndex(0)
        compose.onNodeWithTag("feedback_submit").assertIsDisplayed().assertIsEnabled()
        assertEquals(footer, compose.onNodeWithTag("feedback_submit").fetchSemanticsNode().boundsInRoot)
        capture("feedback-redesign-filled.png")
        compose.onNodeWithTag("feedback_submit").performClick()
        compose.onNodeWithText("My review").assertIsDisplayed()
        compose.onNodeWithTag("feedback_saved").assertTextEquals("My review 4 / 5")
        compose.onNodeWithText("The directions were clear, and I had a great time with everyone.").assertExists()
        compose.onNodeWithTag("feedback_submit").assertDoesNotExist()
        assertEquals(1, submissions)
        capture("feedback-redesign-saved.png")
    }

    @Test fun largeTextKeepsSubmitVisibleAndValidatesLengthAndBusyState() {
        launch(1.6f)
        compose.onNodeWithTag("feedback_submit").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("history_detail").performScrollToNode(hasTestTag("feedback_rating_5"))
        compose.onNodeWithTag("feedback_rating_5").assertIsDisplayed().performClick()
        compose.onNodeWithTag("history_detail").performScrollToNode(hasTestTag("feedback_comment"))
        compose.onNodeWithTag("feedback_comment").performScrollTo().performClick().performTextInput("a".repeat(501))
        compose.onNodeWithTag("feedback_submit").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("feedback_comment").performTextReplacement("a".repeat(500))
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("feedback_submit").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { busy = true }
        compose.onNodeWithTag("feedback_submit").assertIsNotEnabled()
        compose.onNodeWithTag("history_detail").performScrollToNode(hasTestTag("feedback_comment"))
        compose.onNodeWithTag("feedback_comment").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { busy = false }
        compose.onNodeWithTag("feedback_comment").performTextReplacement("A little more guidance for beginners would help next time.")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("history_detail").performScrollToIndex(0)
        capture("feedback-redesign-large-text.png")
        compose.onNodeWithTag("feedback_submit").assertIsDisplayed()
        compose.runOnIdle { record = record.copy(cancelled = true) }
        compose.onNodeWithTag("feedback_submit").assertDoesNotExist()
        assertEquals(0, submissions)
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

package com.gogether.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalTestApi::class)
class FeedbackDraftUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("checkin_prototype_v1", 0)
    private val drafts = FeedbackDraftStore(context)
    private val time = 1_800_000_000_000L
    private val sessions = List(2) { PrototypeSession(UUID.randomUUID().toString(), MeetingPoint(31.27, 120.74), time - 7200000, time) }
    private var actor by mutableStateOf("bob")
    private var activity by mutableIntStateOf(0)
    private var showing by mutableStateOf(true)
    private var saved by mutableStateOf<PrototypeFeedback?>(null)
    private var message by mutableStateOf("")
    private var fail = true

    private fun launch(scale: Float = 1f) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp)) then DeviceConfigurationOverride.FontScale(scale)) {
                GogetherTheme {
                    if (showing) key(actor, activity) {
                        val session = sessions[activity]
                        PrototypeHistoryScreen(actor, listOf(PrototypeHistoryEntry(session, null, saved, false)), time, false, message,
                            { showing = false }, { _, rating, comment, details ->
                                if (fail) message = "Could not save. Please try again."
                                else {
                                    val review = PrototypeFeedback(session.id, actor, rating, comment, time, details)
                                    PreferencesPrototypeStore(context).saveFeedback(review)
                                    saved = review; message = ""
                                }
                            }, initialSessionId = session.id)
                    }
                }
            }
        }
    }
    private fun find(tag: String): SemanticsNodeInteraction {
        return compose.reviewNode(tag)
    }
    @After fun cleanup() {
        val edit = prefs.edit()
        sessions.forEach { session ->
            listOf("bob", "carol").forEach { edit.remove(FeedbackDraftStore.key(it, session.id)) }
            prefs.all.keys.filter { it.contains(session.id) }.forEach(edit::remove)
        }
        edit.commit()
    }

    @Test fun leavingAndReenteringRestoresEveryFieldWithoutSharingAccountsOrActivities() {
        launch()
        compose.onNodeWithTag("feedback_rating_4").performClick()
        find("feedback_organization_5").performClick()
        find("feedback_venue_3").performClick()
        find("feedback_atmosphere_4").performClick()
        find("feedback_tag_welcoming").performClick()
        find("feedback_comment").performTextInput("I would love to join again.")
        Espresso.closeSoftKeyboard()
        find("feedback_anonymous").performClick()
        find("feedback_visibility_ORGANIZER_ONLY").performClick()
        // Real navigation removes the form from composition, rather than relying on instance-state restore.
        compose.reviewNode("feedback_comment")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("history_list").assertExists()
        compose.onNodeWithTag("history_${sessions[0].id}").performClick()
        find("feedback_comment").assertTextContains("I would love to join again.")
        find("feedback_organization_5").assertIsSelected()
        find("feedback_venue_3").assertIsSelected()
        find("feedback_atmosphere_4").assertIsSelected()
        find("feedback_tag_welcoming").assertIsSelected()
        find("feedback_anonymous").assertIsOn()
        find("feedback_visibility_ORGANIZER_ONLY").assertIsSelected()
        compose.runOnIdle { actor = "carol" }
        compose.reviewNode("feedback_submit").assertIsNotEnabled()
        find("feedback_comment").performTextInput("The venue was comfortable and easy to find.")
        Espresso.closeSoftKeyboard()
        compose.onAllNodesWithText("I would love to join again.", substring = true).assertCountEquals(0)
        find("feedback_anonymous").assertIsOff()
        find("feedback_visibility_PUBLIC").assertIsSelected()
        compose.runOnIdle { actor = "bob"; activity = 1 }
        compose.reviewNode("feedback_submit").assertIsNotEnabled()
        find("feedback_rating_2").performClick()
        find("feedback_comment").performTextInput("This event had a relaxed and friendly atmosphere.")
        Espresso.closeSoftKeyboard()
        compose.runOnIdle { activity = 0 }
        find("feedback_rating_4").assertIsSelected()
        find("feedback_comment").assertTextContains("I would love to join again.")
        assertEquals("The venue was comfortable and easy to find.", drafts.load("carol", sessions[0].id).comment)
        assertEquals("This event had a relaxed and friendly atmosphere.", drafts.load("bob", sessions[1].id).comment)
    }

    @Test fun failedSubmitAndFullPageRecreationPreserveDraftSuccessfulSubmitRemovesIt() {
        launch(1.6f)
        find("feedback_rating_4").performClick()
        find("feedback_comment").performTextInput("A good start. I will finish this review later.")
        Espresso.closeSoftKeyboard()
        compose.reviewNode("feedback_submit").assertIsDisplayed().performClick()
        compose.onNodeWithText("Could not save. Please try again.").assertIsDisplayed()
        compose.runOnIdle { showing = false }
        compose.onNodeWithTag("history_screen").assertDoesNotExist()
        compose.runOnIdle { showing = true; message = "" }
        find("feedback_comment").assertTextContains("A good start. I will finish this review later.")
        compose.reviewNode("feedback_submit").assertIsDisplayed()
        compose.runOnIdle { fail = false }
        compose.reviewNode("feedback_submit").performClick()
        compose.onNodeWithTag("feedback_saved").assertExists()
        assertFalse(prefs.contains(FeedbackDraftStore.key("bob", sessions[0].id)))
        assertEquals("A good start. I will finish this review later.", PreferencesPrototypeStore(context).feedback(sessions[0].id, "bob")!!.comment)
    }
}

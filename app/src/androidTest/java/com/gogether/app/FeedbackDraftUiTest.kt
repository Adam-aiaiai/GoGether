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
                                if (fail) message = "保存失败，请重试。"
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
        compose.onNodeWithTag("history_detail").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
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
        find("feedback_comment").performTextInput("下次继续参加")
        Espresso.closeSoftKeyboard()
        find("feedback_anonymous").performClick()
        find("feedback_visibility_ORGANIZER_ONLY").performClick()
        // Real navigation removes the form from composition, rather than relying on instance-state restore.
        compose.onNodeWithText("< 返回").performClick()
        compose.onNodeWithTag("history_list").assertExists()
        compose.onNodeWithTag("history_${sessions[0].id}").performClick()
        find("feedback_comment").assertTextContains("下次继续参加")
        find("feedback_organization_5").assertIsSelected()
        find("feedback_venue_3").assertIsSelected()
        find("feedback_atmosphere_4").assertIsSelected()
        find("feedback_tag_welcoming").assertIsSelected()
        find("feedback_anonymous").assertIsOn()
        find("feedback_visibility_ORGANIZER_ONLY").assertIsSelected()
        compose.runOnIdle { actor = "carol" }
        compose.onNodeWithTag("feedback_submit").assertIsNotEnabled()
        find("feedback_comment").performTextInput("Carol 的草稿")
        Espresso.closeSoftKeyboard()
        compose.onAllNodesWithText("下次继续参加", substring = true).assertCountEquals(0)
        find("feedback_anonymous").assertIsOff()
        find("feedback_visibility_PUBLIC").assertIsSelected()
        compose.runOnIdle { actor = "bob"; activity = 1 }
        compose.onNodeWithTag("feedback_submit").assertIsNotEnabled()
        find("feedback_rating_2").performClick()
        find("feedback_comment").performTextInput("另一场活动的感受")
        Espresso.closeSoftKeyboard()
        compose.runOnIdle { activity = 0 }
        find("feedback_rating_4").assertIsSelected()
        find("feedback_comment").assertTextContains("下次继续参加")
        assertEquals("Carol 的草稿", drafts.load("carol", sessions[0].id).comment)
        assertEquals("另一场活动的感受", drafts.load("bob", sessions[1].id).comment)
    }

    @Test fun failedSubmitAndFullPageRecreationPreserveDraftSuccessfulSubmitRemovesIt() {
        launch(1.6f)
        find("feedback_rating_4").performClick()
        find("feedback_comment").performTextInput("先写一半")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("feedback_submit").assertIsDisplayed().performClick()
        compose.onNodeWithText("保存失败，请重试。").assertIsDisplayed()
        compose.runOnIdle { showing = false }
        compose.onNodeWithTag("history_screen").assertDoesNotExist()
        compose.runOnIdle { showing = true; message = "" }
        find("feedback_comment").assertTextContains("先写一半")
        compose.onNodeWithTag("feedback_submit").assertIsDisplayed()
        compose.runOnIdle { fail = false }
        compose.onNodeWithTag("feedback_submit").performClick()
        compose.onNodeWithTag("feedback_saved").assertExists()
        assertFalse(prefs.contains(FeedbackDraftStore.key("bob", sessions[0].id)))
        assertEquals("先写一半", PreferencesPrototypeStore(context).feedback(sessions[0].id, "bob")!!.comment)
    }
}

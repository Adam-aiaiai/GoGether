package com.gogether.app

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class CheckInEntryUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = 1_800_000_000_000L
    private val session = PrototypeSession(UUID.randomUUID().toString(), MeetingPoint(31.27, 120.74), now, now + 3_600_000)

    @After fun removeOnlyThisTestsDrafts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val edit = context.getSharedPreferences("checkin_prototype_v1", 0).edit()
        listOf("bob", "carol").forEach { edit.remove(FeedbackDraftStore.key(it, session.id)) }
        edit.commit()
    }

    @Test fun entryContractRoundTripsAndRejectsMalformedActivityInsteadOfUsingGeneral() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(CheckInEntry.Activity(session), CheckInEntryIntent.read(CheckInEntryIntent.activity(context, session)))
        assertEquals(CheckInEntry.General, CheckInEntryIntent.read(CheckInEntryIntent.general(context)))
        assertEquals(CheckInEntry.General, CheckInEntryIntent.read(Intent(context, PrototypeCheckInActivity::class.java)))
        val broken = CheckInEntryIntent.activity(context, session).apply { removeExtra("eventId") }
        assertThrows(IllegalArgumentException::class.java) { CheckInEntryIntent.read(broken) }
        assertThrows(IllegalArgumentException::class.java) {
            CheckInEntryIntent.read(CheckInEntryIntent.general(context).putExtra("eventId", session.id))
        }
    }

    @Test fun accountMismatchHidesOldActivityRecordsAndFeedbackDraftImmediately() {
        var actor by mutableStateOf("bob")
        val entry = PrototypeHistoryEntry(session, PrototypeReceipt(session.id, "bob", now), null, false)
        val state = PrototypeUiState(ownerUserId = "bob", participantSession = session, receipt = entry.receipt, history = listOf(entry))
        compose.setContent { GogetherTheme {
            PrototypeScreen(actor, state, session.endAt, {}, {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("prototype_scan").performClick()
        compose.onNodeWithTag("feedback_rating_4").performScrollTo().performClick()
        compose.onNodeWithTag("history_detail").performScrollToNode(hasTestTag("feedback_comment"))
        compose.onNodeWithTag("feedback_comment").performScrollTo().performTextInput("I enjoyed meeting everyone. I will finish this review later.")
        compose.runOnIdle { actor = "carol" }
        compose.onNodeWithTag("checkin_identity_expired").assertIsDisplayed()
        compose.onNodeWithTag("participant_event_title").assertDoesNotExist()
        compose.onNodeWithTag("history_screen").assertDoesNotExist()
        compose.onNodeWithTag("feedback_comment").assertDoesNotExist()
    }

    @Test fun choosingHistoryActivityPassesThatExactActivityToTheEEntry() {
        val entry = PrototypeHistoryEntry(session, null, null, false)
        var selected: PrototypeSession? = null
        compose.setContent { GogetherTheme {
            PrototypeScreen("bob", PrototypeUiState(ownerUserId = "bob", history = listOf(entry)), now,
                {}, {}, {}, {}, {}, {}, {}, {}, selectActivity = { selected = it })
        } }
        compose.onNodeWithTag("open_history").performClick()
        compose.onNodeWithTag("history_${session.id}").performClick()
        compose.onNodeWithTag("history_open_checkin").performScrollTo().performClick()
        assertEquals(session, selected)
        compose.onNodeWithTag("history_screen").assertDoesNotExist()
    }

    @Test fun anotherActivityUsesExplicitGeneralCallbackRatherThanCurrentActivityScan() {
        var currentCalls = 0
        var generalCalls = 0
        compose.setContent { GogetherTheme {
            PrototypeScreen("bob", PrototypeUiState(ownerUserId = "bob", entry = CheckInEntry.Activity(session), participantSession = session),
                now, {}, {}, {}, { currentCalls++ }, {}, {}, {}, {}, scanOther = { generalCalls++ })
        } }
        compose.onNodeWithTag("prototype_scan").performClick()
        assertEquals(1, currentCalls)
        compose.onNodeWithTag("participant_help").performClick()
        compose.onNodeWithTag("participant_scan_other").performScrollTo().performClick()
        assertEquals(1, generalCalls)
        assertEquals(1, currentCalls)
    }
}

package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class ParticipantCheckInPanelTest {
    @get:Rule val compose = createComposeRule()
    private val now = 1_800_000_000_000L
    private var state by mutableStateOf(PrototypeUiState())
    private var actor by mutableStateOf("bob")
    private var scans = 0
    private var settings = 0
    private var retries = 0
    private var resolutions = 0
    private var displayTime by mutableStateOf(now)
    private val session = PrototypeSession("f23e4af8-b3ed-49ef-a7ee-d7c1cf2bf317",
        now + 600_000, now + 7_800_000)

    private fun launch(fontScale: Float = 1f, width: Int = 320, height: Int = 568) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then
                DeviceConfigurationOverride.FontScale(fontScale)) {
                GogetherTheme { PrototypeScreen(actor, state, displayTime, {}, {}, {}, { scans++ }, {}, {}, {}, { settings++ },
                    resolveCheckIn = { resolutions++ }) }
            }
        }
    }

    @Test fun compactPhoneShowsReadyBusySuccessAndRetryWithoutMixingOldReceipt() = verifyFlow(1f)
    @Test fun largeTextKeepsResultAndScanActionOnSmallPhone() = verifyFlow(1.6f)

    private fun verifyFlow(fontScale: Float) {
        launch(fontScale)
        compose.onNodeWithText("Status").assertDoesNotExist()
        compose.onNodeWithText("Registration status").assertDoesNotExist()
        compose.onNodeWithTag("participant_status_title").assertTextEquals("Scan to check in on arrival")
        capture("participant-ready-$fontScale.png")
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, scans) }
        val buttonBounds = compose.onNodeWithTag("prototype_scan").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state = state.copy(busy = true, scanStage = ParticipantScanStage.VERIFYING) }
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("participant_status_title").assertTextEquals("Verifying check-in")
        compose.runOnIdle { state = state.copy(busy = false, scanStage = ParticipantScanStage.SUCCESS,
            receipt = PrototypeReceipt("f23e4af8-b3ed-49ef-a7ee-d7c1cf2bf317", "bob", now)) }
        compose.onNodeWithTag("participant_status_title").assertTextEquals("Checked in successfully")
        val receipt = compose.onNodeWithTag("prototype_receipt").performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Receipt fits above the pinned button", receipt.bottom <= buttonBounds.top)
        assertEquals(buttonBounds, compose.onNodeWithTag("prototype_scan").assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("participant_help").assertIsDisplayed()
        capture("participant-result-$fontScale.png")
        compose.runOnIdle { state = state.copy(scanStage = ParticipantScanStage.ATTENTION, message = "This QR code has expired. Please scan again.") }
        compose.onNodeWithText("This QR code has expired. Please scan again.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("prototype_receipt").assertDoesNotExist()
        compose.onNodeWithTag("participant_status_title").assertTextEquals("Check-in notice")
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { state = state.copy(scanStage = ParticipantScanStage.READY) }
        compose.onNodeWithTag("participant_status_title").assertTextEquals("Checked in")
        compose.runOnIdle { actor = "carol" }
        compose.onNodeWithTag("prototype_receipt").assertDoesNotExist()
    }

    @Test fun landscapeKeepsActionsReachableAndHelpContainsPermissionEntry() {
        launch(width = 640, height = 360)
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed()
        compose.onNodeWithTag("participant_help").assertIsDisplayed().performClick()
        compose.onNodeWithText("Check-in guide").assertIsDisplayed()
        compose.onNodeWithTag("participant_settings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, settings) }
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed()
    }

    @Test fun eventContextSurvivesSuccessAndOpensItsOwnRecord() {
        state = PrototypeUiState(participantSession = session,
            history = listOf(PrototypeHistoryEntry(session, null, null, false)))
        launch(width = 360, height = 720)
        compose.onNodeWithTag("participant_event_title").assertTextEquals("Badminton Meetup")
        compose.onNodeWithText("Hosted by Alice").assertIsDisplayed()
        compose.onNodeWithText("Meeting point").assertIsDisplayed()
        compose.onNodeWithTag("participant_status_title").assertTextEquals("Ready to check in")
        capture("participant-event-ready.png")
        val receipt = PrototypeReceipt(session.id, "bob", now)
        compose.runOnIdle { state = state.copy(receipt = receipt, scanStage = ParticipantScanStage.SUCCESS,
            history = listOf(PrototypeHistoryEntry(session, receipt, null, false))) }
        compose.onNodeWithTag("participant_status_title").assertTextEquals("Checked in successfully")
        capture("participant-event-success.png")
        compose.onNodeWithTag("prototype_scan").performClick()
        compose.onNodeWithTag("history_detail").assertIsDisplayed()
        compose.onNodeWithTag("history_checkin").assertTextContains("Checked in", substring = true)
        compose.runOnIdle { assertEquals(0, scans) }
    }

    @Test fun checkedInEndedActivityOpensReviewAndKeepsTextMeetingAddress() {
        // This is a fresh review form; another test or a prior run may have saved a draft.
        val ended = session.copy(id = java.util.UUID.randomUUID().toString(),
            startAt = now - 7_800_000, endAt = now - 1)
        state = PrototypeUiState(participantSession = ended,
            history = listOf(PrototypeHistoryEntry(ended, PrototypeReceipt(ended.id, "bob", ended.startAt), null, false)))
        launch()
        compose.onNodeWithText("Meeting point").assertIsDisplayed()
        compose.onNodeWithTag("participant_map").assertDoesNotExist()
        compose.onNodeWithText("Review event").assertIsDisplayed().performClick()
        compose.onNodeWithTag("feedback_rating_4").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("feedback_submit").assertIsNotEnabled()
    }

    @Test fun oldReceiptDoesNotBelongToNewActivityAndClosedEventStillAllowsAnotherScan() {
        state = PrototypeUiState(participantSession = session,
            receipt = PrototypeReceipt("414f1652-7e75-4d90-9cd7-c42f3665623a", "bob", now))
        launch()
        compose.onNodeWithTag("prototype_receipt").assertDoesNotExist()
        compose.onNodeWithTag("participant_status_title").assertTextEquals("Ready to check in")
        compose.runOnIdle { state = state.copy(history = listOf(PrototypeHistoryEntry(session, null, null, true))) }
        compose.onNodeWithTag("prototype_scan").assertIsNotEnabled()
        compose.onNodeWithTag("participant_help").performClick()
        compose.onNodeWithTag("participant_scan_other").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, scans) }
    }

    @Test fun waitlistedAndUnregisteredAccountsCannotStartCheckIn() {
        actor = "emma"
        launch()
        compose.onNodeWithTag("prototype_scan").assertIsNotEnabled()
        compose.runOnIdle { actor = "outsider" }
        compose.onNodeWithTag("prototype_scan").assertIsNotEnabled()
    }

    @Test fun rejectedScanOffersAnotherScanWithoutLocationAction() {
        state = PrototypeUiState(participantSession = session, scanStage = ParticipantScanStage.ATTENTION,
            message = "Your registration changed. Scan again.")
        launch()
        compose.onNodeWithText("Scan again").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, scans) }
        compose.onNodeWithText("Retry location").assertDoesNotExist()
    }

    @Test fun uncertainSubmissionUsesExistingActionAndCanBeResolvedAfterActivityCloses() {
        state = PrototypeUiState(participantSession = session,
            pendingSubmission = CheckInAttempt("request-1", "bob", session),
            scanStage = ParticipantScanStage.RESOLVING, busy = true,
            message = "Checking your previous check-in. Please wait…")
        launch()
        compose.onNodeWithTag("participant_status_title").assertTextEquals("Checking your check-in result")
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed().assertIsNotEnabled()
        val bounds = compose.onNodeWithTag("prototype_scan").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            displayTime = session.endAt + 1
            state = state.copy(busy = false, scanStage = ParticipantScanStage.ATTENTION,
                registration = CheckInRegistration.NONE,
                history = listOf(PrototypeHistoryEntry(session, null, null, true)),
                message = "Check the result later. No need to scan again.")
        }
        compose.onNodeWithTag("participant_status_title").assertTextEquals("Check-in unconfirmed")
        compose.onNodeWithText("Check result").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, resolutions) }
        compose.runOnIdle { assertEquals(0, scans) }
        assertEquals(bounds, compose.onNodeWithTag("prototype_scan").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("prototype_receipt").assertDoesNotExist()
        val issue = compose.onNodeWithTag("participant_issue").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val area = compose.onNodeWithTag("participant_status_area").fetchSemanticsNode().boundsInRoot
        assertTrue("The whole recovery message fits on a small phone", issue.bottom <= area.bottom)
        capture("participant-result-unknown.png")
    }

    @Test fun pendingResultRemainsReachableFromEndedActivityHistory() {
        displayTime = session.endAt + 1
        state = PrototypeUiState(participantSession = session,
            pendingSubmission = CheckInAttempt("pending-history", "bob", session),
            scanStage = ParticipantScanStage.ATTENTION,
            history = listOf(PrototypeHistoryEntry(session, null, null, true)),
            message = "Check the result later. No need to scan again.")
        launch()
        compose.onNodeWithTag("open_history").performClick()
        compose.onNodeWithText("Check-in unconfirmed").assertIsDisplayed()
        compose.onNodeWithTag("history_${session.id}").performClick()
        compose.onNodeWithTag("history_checkin").assertDoesNotExist()
        compose.onNodeWithTag("history_resolve_checkin").performScrollTo().performClick()
        compose.onNodeWithText("Check result").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, resolutions) }
        compose.runOnIdle { assertEquals(0, scans) }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onNodeWithTag("prototype_screen").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

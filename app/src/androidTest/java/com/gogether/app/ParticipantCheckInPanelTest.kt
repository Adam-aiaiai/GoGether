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
    private val session = PrototypeSession("f23e4af8-b3ed-49ef-a7ee-d7c1cf2bf317", MeetingPoint(31.27, 120.74),
        now + 600_000, now + 7_800_000)

    private fun launch(fontScale: Float = 1f, width: Int = 320, height: Int = 568) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then
                DeviceConfigurationOverride.FontScale(fontScale)) {
                GogetherTheme { PrototypeScreen(actor, state, displayTime, {}, {}, {}, { scans++ }, {}, {}, {}, { settings++ },
                    retryLocation = { retries++ }, resolveCheckIn = { resolutions++ }) }
            }
        }
    }

    @Test fun compactPhoneShowsReadyBusySuccessAndRetryWithoutMixingOldReceipt() = verifyFlow(1f)
    @Test fun largeTextKeepsResultAndScanActionOnSmallPhone() = verifyFlow(1.6f)

    private fun verifyFlow(fontScale: Float) {
        launch(fontScale)
        compose.onNodeWithText("当前状态").assertDoesNotExist()
        compose.onNodeWithText("报名状态").assertDoesNotExist()
        compose.onNodeWithTag("participant_status_title").assertTextEquals("到场后扫码签到")
        capture("participant-ready-$fontScale.png")
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed().performClick()
        assertEquals(1, scans)
        val buttonBounds = compose.onNodeWithTag("prototype_scan").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state = state.copy(busy = true, scanStage = ParticipantScanStage.VERIFYING) }
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("participant_status_title").assertTextEquals("正在核验签到")
        compose.runOnIdle { state = state.copy(busy = false, scanStage = ParticipantScanStage.SUCCESS,
            receipt = PrototypeReceipt("f23e4af8-b3ed-49ef-a7ee-d7c1cf2bf317", "bob", now)) }
        compose.onNodeWithTag("participant_status_title").assertTextEquals("签到成功")
        val receipt = compose.onNodeWithTag("prototype_receipt").performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Receipt fits above the pinned button", receipt.bottom <= buttonBounds.top)
        assertEquals(buttonBounds, compose.onNodeWithTag("prototype_scan").assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("participant_help").assertIsDisplayed()
        capture("participant-result-$fontScale.png")
        compose.runOnIdle { state = state.copy(scanStage = ParticipantScanStage.ATTENTION, message = "二维码已过期，请重新扫码。") }
        compose.onNodeWithText("二维码已过期，请重新扫码。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("prototype_receipt").assertDoesNotExist()
        compose.onNodeWithTag("participant_status_title").assertTextEquals("签到提示")
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { state = state.copy(scanStage = ParticipantScanStage.READY) }
        compose.onNodeWithTag("participant_status_title").assertTextEquals("已签到")
        compose.runOnIdle { actor = "carol" }
        compose.onNodeWithTag("prototype_receipt").assertDoesNotExist()
    }

    @Test fun landscapeKeepsActionsReachableAndHelpContainsPermissionEntry() {
        launch(width = 640, height = 360)
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed()
        compose.onNodeWithTag("participant_help").assertIsDisplayed().performClick()
        compose.onNodeWithText("签到说明").assertIsDisplayed()
        compose.onNodeWithTag("participant_settings").performScrollTo().performClick()
        assertEquals(1, settings)
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed()
    }

    @Test fun eventContextSurvivesSuccessAndOpensItsOwnRecord() {
        state = PrototypeUiState(participantSession = session,
            history = listOf(PrototypeHistoryEntry(session, null, null, false)))
        launch(width = 360, height = 720)
        compose.onNodeWithTag("participant_event_title").assertTextEquals("羽毛球交流")
        compose.onNodeWithText("Alice 发起").assertIsDisplayed()
        compose.onNodeWithTag("participant_location").assertIsDisplayed()
        compose.onNodeWithTag("participant_status_title").assertTextEquals("现在可以签到")
        capture("participant-event-ready.png")
        val receipt = PrototypeReceipt(session.id, "bob", now)
        compose.runOnIdle { state = state.copy(receipt = receipt, scanStage = ParticipantScanStage.SUCCESS,
            history = listOf(PrototypeHistoryEntry(session, receipt, null, false))) }
        compose.onNodeWithTag("participant_status_title").assertTextEquals("签到成功")
        capture("participant-event-success.png")
        compose.onNodeWithTag("prototype_scan").performClick()
        compose.onNodeWithTag("history_detail").assertIsDisplayed()
        compose.onNodeWithTag("history_checkin").assertTextContains("已签到", substring = true)
        assertEquals(0, scans)
    }

    @Test fun endedActivityOpensReviewDirectlyAndLocationHasMapAction() {
        // This is a fresh review form; another test or a prior run may have saved a draft.
        val ended = session.copy(id = java.util.UUID.randomUUID().toString(),
            startAt = now - 7_800_000, endAt = now - 1)
        state = PrototypeUiState(participantSession = ended,
            history = listOf(PrototypeHistoryEntry(ended, null, null, false)))
        launch()
        compose.onNodeWithTag("participant_location").performClick()
        compose.onNodeWithText("集合位置").assertIsDisplayed()
        compose.onNodeWithTag("participant_map").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText("评价活动").assertIsDisplayed().performClick()
        compose.onNodeWithTag("feedback_rating_4").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("feedback_submit").assertIsNotEnabled()
    }

    @Test fun oldReceiptDoesNotBelongToNewActivityAndClosedEventStillAllowsAnotherScan() {
        state = PrototypeUiState(participantSession = session,
            receipt = PrototypeReceipt("414f1652-7e75-4d90-9cd7-c42f3665623a", "bob", now))
        launch()
        compose.onNodeWithTag("prototype_receipt").assertDoesNotExist()
        compose.onNodeWithTag("participant_status_title").assertTextEquals("现在可以签到")
        compose.runOnIdle { state = state.copy(history = listOf(PrototypeHistoryEntry(session, null, null, true))) }
        compose.onNodeWithTag("prototype_scan").assertIsNotEnabled()
        compose.onNodeWithTag("participant_help").performClick()
        compose.onNodeWithTag("participant_scan_other").performScrollTo().performClick()
        assertEquals(1, scans)
    }

    @Test fun waitlistedAndUnregisteredAccountsCannotStartCheckIn() {
        actor = "emma"
        launch()
        compose.onNodeWithTag("prototype_scan").assertIsNotEnabled()
        compose.runOnIdle { actor = "outsider" }
        compose.onNodeWithTag("prototype_scan").assertIsNotEnabled()
    }

    @Test fun locationRetryChangesToRescanAtExpiryWithoutMovingThePrimaryAction() {
        state = PrototypeUiState(participantSession = session, scanned = PrototypeCode(session, now, now + 30_000),
            locationRetryAvailable = true, scanStage = ParticipantScanStage.ATTENTION,
            message = "暂时无法定位，请到开阔处重试。")
        launch()
        compose.onNodeWithText("重新定位").assertIsDisplayed().performClick()
        assertEquals(1, retries)
        assertEquals(0, scans)
        val bounds = compose.onNodeWithTag("prototype_scan").fetchSemanticsNode().boundsInRoot
        capture("participant-location-retry.png")
        compose.runOnIdle { displayTime = now + 30_000 }
        compose.onNodeWithText("签到码已过期，请扫描组织者的新码。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("重新扫码").assertIsDisplayed().performClick()
        assertEquals(1, retries)
        assertEquals(1, scans)
        assertEquals(bounds, compose.onNodeWithTag("prototype_scan").fetchSemanticsNode().boundsInRoot)
        capture("participant-retry-expired.png")
    }

    @Test fun uncertainSubmissionUsesExistingActionAndCanBeResolvedAfterActivityCloses() {
        state = PrototypeUiState(participantSession = session,
            pendingSubmission = CheckInAttempt("request-1", "bob", session),
            scanStage = ParticipantScanStage.RESOLVING, busy = true,
            message = "正在核对上次签到结果，请稍候…")
        launch()
        compose.onNodeWithTag("participant_status_title").assertTextEquals("正在核对签到结果")
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed().assertIsNotEnabled()
        val bounds = compose.onNodeWithTag("prototype_scan").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            displayTime = session.endAt + 1
            state = state.copy(busy = false, scanStage = ParticipantScanStage.ATTENTION,
                registration = CheckInRegistration.NONE,
                history = listOf(PrototypeHistoryEntry(session, null, null, true)),
                message = "请稍后核对，无需重复扫码。")
        }
        compose.onNodeWithTag("participant_status_title").assertTextEquals("签到结果待确认")
        compose.onNodeWithText("核对签到结果").assertIsDisplayed().performClick()
        assertEquals(1, resolutions)
        assertEquals(0, scans)
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
            message = "请稍后核对，无需重复扫码。")
        launch()
        compose.onNodeWithTag("open_history").performClick()
        compose.onNodeWithText("签到结果待确认").assertIsDisplayed()
        compose.onNodeWithTag("history_${session.id}").performClick()
        compose.onNodeWithTag("history_checkin").assertDoesNotExist()
        compose.onNodeWithTag("history_resolve_checkin").performScrollTo().performClick()
        compose.onNodeWithText("核对签到结果").assertIsDisplayed().performClick()
        assertEquals(1, resolutions)
        assertEquals(0, scans)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onNodeWithTag("prototype_screen").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

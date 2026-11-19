package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.*
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalTestApi::class)
class AttendanceInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val now = 1_800_000_000_000L
    private val session = PrototypeSession("f23e4af8-b3ed-49ef-a7ee-d7c1cf2bf317", MeetingPoint(31.27, 120.74),
        now + 600_000, now + 7_800_000)
    private var state by mutableStateOf(PrototypeUiState(session = session,
        attendance = AttendanceLoadState(session, loaded = true, updatedAt = now),
        code = PrototypeCode(session, now, now + 30_000),
        attendees = listOf(PrototypeAttendee("bob", "Bob", now), PrototypeAttendee("carol", "Chris"),
            PrototypeAttendee("chen", "Chris", now - 60_000))))

    private fun launch(actor: String = "alice", fontScale: Float = 1f) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp)) then
                DeviceConfigurationOverride.FontScale(fontScale)) {
                GogetherTheme { PrototypeScreen(actor, state, now, {}, {}, {}, {}, {}, {}, {}, {},
                    dismissNotice = { id -> if (state.notice?.id == id) state = state.copy(notice = null) }) }
            }
        }
    }

    @Test fun avatarShowsTimeAndPendingDetailsFollowLatestRecord() {
        launch()
        val qr = compose.onNodeWithTag("prototype_qr").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("attendee_bob").performClick()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(now))
        compose.onNodeWithTag("attendance_detail").assertTextContains("Time: $date", substring = true)
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(qr, compose.onNodeWithTag("prototype_qr").assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        capture("attendance-time-radius12.png")
        compose.onNodeWithContentDescription("Hide check-in details").performClick()
        compose.onNodeWithTag("attendance_pending_tab").performClick()
        compose.onNodeWithTag("attendee_carol").performClick()
        compose.onNodeWithTag("attendance_detail").assertTextEquals("Not checked in")
        compose.runOnIdle { state = state.copy(attendees = state.attendees.map {
            if (it.userId == "carol") it.copy(checkedInAt = now) else it
        }) }
        compose.onNodeWithTag("attendance_detail").assertTextContains("Time: $date", substring = true)
        compose.runOnIdle { state = state.copy(attendees = state.attendees.filterNot { it.userId == "carol" }) }
        compose.onNodeWithTag("attendance_detail").assertDoesNotExist()
    }

    @Test fun searchFindsBothGroupsSameNamesAndEmptyResultsOnSmallLargeTextScreen() {
        launch(fontScale = 1.6f)
        val qr = compose.onNodeWithTag("prototype_qr").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("attendance_search_open").assertIsDisplayed().performClick()
        val input = compose.onNodeWithTag("attendance_search_input")
        input.performTextInput(" bOB ")
        input.performImeAction()
        compose.onNodeWithTag("search_person_bob").assertIsDisplayed()
        compose.onNodeWithTag("search_person_carol").assertDoesNotExist()
        input.performTextReplacement("Chris")
        input.performImeAction()
        compose.onNodeWithTag("search_person_carol").assertIsDisplayed()
        compose.onNodeWithTag("search_person_chen").assertIsDisplayed()
        capture("attendance-search-radius12.png")
        compose.onNodeWithTag("search_person_carol").performClick()
        compose.onNodeWithTag("attendance_detail").assertTextEquals("Not checked in")
        compose.onNodeWithTag("search_person_carol").performClick()
        compose.onNodeWithTag("attendance_detail").assertDoesNotExist()
        input.performTextReplacement("Nobody Matches This Name")
        input.performImeAction()
        compose.onNodeWithText("No matching names. Try another name.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("3 people found").assertIsDisplayed()
        // Dismiss the modal through the platform back event; the underlying QR never moves.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        assertEquals(qr, compose.onNodeWithTag("prototype_qr").assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
    }

    @Test fun participantSuccessIsBriefAndOldTimerCannotDismissNewNotice() {
        state = state.copy(session = null, code = null, receipt = PrototypeReceipt(session.id, "bob", now))
        launch("bob")
        compose.onNodeWithTag("checkin_notice").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state = state.copy(scanStage = ParticipantScanStage.SUCCESS, notice = PrototypeNotice(1, "Checked in successfully")) }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("checkin_notice").assertIsDisplayed()
        capture("participant-success-compact.png")
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { state = state.copy(notice = PrototypeNotice(2, "Checked in successfully")) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("checkin_notice").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("checkin_notice").assertDoesNotExist()
    }

    @Test fun organizerArrivalDoesNotMoveOrCoverQr() {
        launch()
        val qr = compose.onNodeWithTag("prototype_qr").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state = state.copy(notice = PrototypeNotice(1, "Bob just checked in")) }
        compose.mainClock.advanceTimeBy(32)
        val notice = compose.onNodeWithTag("checkin_notice").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(notice.top >= qr.bottom)
        assertEquals(qr, compose.onNodeWithTag("prototype_qr").fetchSemanticsNode().boundsInRoot)
        capture("organizer-arrival-radius12.png")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            val bitmap = if (name.contains("search")) InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                else compose.onNodeWithTag("prototype_screen").captureToImage().asAndroidBitmap()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

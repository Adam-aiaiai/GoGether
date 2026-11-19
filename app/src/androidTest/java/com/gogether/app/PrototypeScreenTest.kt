package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PrototypeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val now = 1_800_000_000_000L
    private val session = PrototypeSession("f23e4af8-b3ed-49ef-a7ee-d7c1cf2bf317", MeetingPoint(31.27, 120.74),
        now + 600_000, now + 7_800_000)
    private var state by mutableStateOf(PrototypeUiState())
    private var time by mutableStateOf(now)
    private var scanCalls = 0
    private fun launch(actor: String) {
        compose.setContent {
            GogetherTheme {
                PrototypeScreen(actor, state, time, {}, {}, {}, { scanCalls++ }, {}, {}, {}, {})
            }
        }
    }

    @Test fun attendanceMovesBetweenGroupsAndEmptyStatesStayAccurate() {
        var people by mutableStateOf(listOf(PrototypeAttendee("bob", "Bob"), PrototypeAttendee("carol", "Carol")))
        compose.setContent { GogetherTheme { PrototypeAttendance(people, Modifier.size(320.dp, 400.dp)) } }
        compose.onNodeWithText("No check-ins yet").assertExists()
        compose.onNodeWithTag("attendance_pending_tab").performClick()
        compose.onNode(hasTestTag("attendee_bob") and hasAnyAncestor(hasTestTag("attendance_pending"))).assertExists()
        compose.runOnIdle { people = people.map { it.copy(checkedInAt = now) } }
        compose.onNodeWithText("Everyone has checked in").assertExists()
        compose.onNodeWithTag("attendance_checked_tab").performClick()
        compose.onNode(hasTestTag("attendee_bob") and hasAnyAncestor(hasTestTag("attendance_checked"))).assertExists()
        compose.onAllNodesWithTag("attendee_bob").assertCountEquals(1)
        compose.onNodeWithText("2 participants, 2 checked in").assertExists()
        compose.runOnIdle { people = emptyList() }
        compose.onNodeWithText("No confirmed participants yet").assertExists()
    }

    @Test fun manyParticipantsWrapAndLastPersonRemainsReachableOnNarrowScreen() {
        val people = (0..10).map { PrototypeAttendee("person-$it", "Participant $it Alexandra Montgomery") }
        compose.setContent { GogetherTheme { PrototypeAttendance(people, Modifier.size(280.dp, 260.dp)) } }
        compose.onNodeWithTag("attendance_pending_tab").performClick()
        val first = compose.onNodeWithTag("attendee_person-0").fetchSemanticsNode().boundsInRoot
        val fourth = compose.onNodeWithTag("attendee_person-3").fetchSemanticsNode().boundsInRoot
        assertTrue("Fourth avatar must wrap to the next row", fourth.top > first.top)
        compose.onNodeWithTag("attendance_grid").performScrollToIndex(10)
        compose.onNodeWithTag("attendee_person-10").assertIsDisplayed()
        compose.onAllNodes(hasTestTag("attendee_person-10")).assertCountEquals(1)
    }

    @Test fun examplePreviewDoesNotChangeActualAttendanceAndResetsForNewEvent() {
        state = PrototypeUiState(session = session, code = PrototypeCode(session, now, now + 30_000),
            attendance = AttendanceLoadState(session, loaded = true, updatedAt = now),
            attendees = listOf(PrototypeAttendee("bob", "Bob")))
        launch("alice")
        compose.onNodeWithTag("organizer_more").performClick()
        compose.onNodeWithTag("attendance_preview").performScrollTo().performClick()
        compose.onNodeWithText("10 participants, 5 checked in").assertExists()
        compose.onNodeWithTag("prototype_qr").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "prototype-attendance.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("attendance_pending_tab").performClick()
        compose.onNodeWithTag("attendance_grid").performScrollToIndex(4)
        compose.onNodeWithTag("attendee_example-9").assertIsDisplayed()
        File(context.getExternalFilesDir(null), "prototype-attendance-groups.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("organizer_more").performClick()
        compose.onNodeWithTag("attendance_preview").performScrollTo().performClick()
        compose.onNodeWithText("1 participants, 0 checked in").assertExists()
        compose.onNodeWithTag("attendee_example-0").assertDoesNotExist()
        compose.onNodeWithTag("organizer_more").performClick()
        compose.onNodeWithTag("attendance_preview").performScrollTo().performClick()
        compose.runOnIdle { state = state.copy(session = session.copy(id = UUID.randomUUID().toString()), code = null) }
        compose.onNodeWithTag("attendee_example-0").assertDoesNotExist()
        assertNull(state.attendees.single().checkedInAt)
    }

    @Test fun organizerSeesCodeOnlyWhileItIsValidAndEventIsOpen() {
        state = PrototypeUiState(session = session, code = PrototypeCode(session, now, now + 30_000),
            message = "Location verified. Participants can now scan your code.")
        launch("alice")
        compose.onNodeWithTag("prototype_qr").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "prototype-organizer.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { time += 30_000 }
        compose.onNodeWithTag("prototype_qr").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(cancelled = true) }
        compose.onNodeWithTag("prototype_show").assertIsNotEnabled()
    }

    @Test fun smallPhoneKeepsQrVisibleWhileLongRosterScrolls() = checkBoundedLayout(320, 568, 1f)

    @Test fun landscapeKeepsQrAndRosterSideBySide() = checkBoundedLayout(640, 360, 1f)

    @Test fun largeTextKeepsBothTabsAndActionsReachable() = checkBoundedLayout(320, 568, 1.6f)

    @OptIn(ExperimentalTestApi::class)
    private fun checkBoundedLayout(width: Int, height: Int, fontScale: Float) {
        state = PrototypeUiState(session = session, code = PrototypeCode(session, now, now + 30_000),
            attendance = AttendanceLoadState(session, loaded = true, updatedAt = now),
            attendees = (0..39).map { PrototypeAttendee("person-$it", "Participant $it", if (it < 10) now else null) })
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then
                DeviceConfigurationOverride.FontScale(fontScale)) {
                GogetherTheme { PrototypeScreen("alice", state, now, {}, {}, {}, {}, {}, {}, {}, {}) }
            }
        }
        val before = compose.onNodeWithTag("prototype_qr").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("prototype_show").assertIsDisplayed()
        compose.onNodeWithTag("attendance_checked_tab").assertIsDisplayed()
        compose.onNodeWithTag("attendance_pending_tab").assertIsDisplayed().performClick()
        if (fontScale > 1.3f) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Pending\n30").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            val captureContext = InstrumentationRegistry.getInstrumentation().targetContext
            File(captureContext.getExternalFilesDir(null), "attendance-large-label.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            assertTrue("Large text tab overflow: " + layouts.joinToString { "size=${it.size}, lines=${it.lineCount}, width=${it.didOverflowWidth}, height=${it.didOverflowHeight}" },
                layouts.none { it.hasVisualOverflow })
        }
        compose.onNodeWithTag("attendance_grid").performScrollToIndex(29)
        compose.onNodeWithTag("attendee_person-39").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("prototype_qr").assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "attendance-${width}x${height}-$fontScale.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("organizer_more").assertIsDisplayed().performClick()
        compose.onNodeWithTag("attendance_preview").performScrollTo().assertIsDisplayed()
    }

    @Test fun participantCanScanButCannotCreateOrMoveTheMeetingPoint() {
        launch("bob")
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed().performClick()
        assertEquals(1, scanCalls)
        compose.onNodeWithTag("prototype_meeting").assertDoesNotExist()
        compose.onNodeWithTag("prototype_show").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(busy = true, message = "Getting location…") }
        compose.onNodeWithTag("prototype_scan").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(busy = false, scanStage = ParticipantScanStage.ATTENTION, message = "Precise location permission is required") }
        compose.onNodeWithTag("prototype_status").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Precise location permission is required").assertExists()
        compose.onNodeWithTag("prototype_receipt").assertDoesNotExist()
    }

    @Test fun successShowsLocalReceiptAndAllowsRetryAfterFailure() {
        state = PrototypeUiState(scanStage = ParticipantScanStage.ATTENTION, message = "This QR code has expired. Please scan again.")
        launch("bob")
        compose.onNodeWithTag("prototype_scan").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { state = state.copy(receipt = PrototypeReceipt(session.id, "bob", now),
            scanStage = ParticipantScanStage.SUCCESS,
            message = "Checked in successfully. Your record is saved on this device.") }
        compose.onNodeWithTag("prototype_receipt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("participant_status_title").assertTextEquals("Checked in successfully")
    }

    @Test fun storedMeetingAndReceiptSurviveRecreatingTheStore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = PreferencesPrototypeStore(context)
        val previous = first.hostedSession
        val testSession = session.copy(id = UUID.randomUUID().toString())
        try {
            first.hostedSession = testSession
            val receipt = PrototypeReceipt(testSession.id, "prototype-test-user", now)
            first.save(receipt)
            first.cancel(testSession.id)
            val restored = PreferencesPrototypeStore(context)
            assertEquals(testSession, restored.hostedSession)
            assertEquals(receipt, restored.receipt(testSession.id, "prototype-test-user"))
            assertTrue(restored.isCancelled(testSession.id))
        } finally {
            first.hostedSession = previous
            context.getSharedPreferences("checkin_prototype_v1", 0).edit()
                .remove("receipt.${testSession.id}.prototype-test-user")
                .remove("cancelled.${testSession.id}").commit()
        }
    }
}

package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

@OptIn(ExperimentalTestApi::class)
class AttendanceRefreshUiTest {
    @get:Rule val compose = createComposeRule()
    private val time = 1_800_000_000_000L
    private val session = PrototypeSession("f23e4af8-b3ed-49ef-a7ee-d7c1cf2bf317", MeetingPoint(31.27, 120.74), time, time + 60_000)
    private var state by mutableStateOf(PrototypeUiState(ownerUserId = "alice", session = session,
        code = PrototypeCode(session, time, time + 30_000), attendance = AttendanceLoadState(session, loading = true)))
    private fun launch(now: Long = time, refresh: (PrototypeSession) -> Unit = {}) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp))) {
                GogetherTheme { PrototypeScreen("alice", state, now, {}, {}, {}, {}, {}, {}, {}, {}, refreshAttendance = refresh) }
            }
        }
    }

    @Test fun loadingFailureRetryAndRosterChangesKeepQrInPlace() {
        var calls = 0
        launch { calls++ }
        val qr = compose.onNodeWithTag("prototype_qr").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("attendance_loading").assertIsDisplayed()
        compose.onNodeWithTag("attendance_refresh").assertIsNotEnabled()
        compose.onNodeWithText("No confirmed participants yet").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(attendance = AttendanceLoadState(session, error = "failure")) }
        compose.onNodeWithContentDescription("Retry loading attendance").assertIsDisplayed()
        val before = calls
        compose.onNodeWithTag("attendance_refresh").performClick()
        compose.runOnIdle { assertEquals(before + 1, calls) }
        compose.runOnIdle {
            state = state.copy(attendees = listOf(PrototypeAttendee("bob", "Bob", time), PrototypeAttendee("carol", "Carol")),
                attendance = AttendanceLoadState(session, loaded = true, updatedAt = time))
        }
        compose.onNodeWithTag("attendee_bob").assertIsDisplayed().performClick()
        compose.onNodeWithTag("attendance_detail").assertTextContains("Time:", substring = true)
        compose.runOnIdle { state = state.copy(attendance = state.attendance.copy(error = "offline")) }
        compose.onNodeWithText("Update failed. Showing previous results.").assertIsDisplayed()
        compose.onNodeWithTag("attendance_detail").assertIsDisplayed()
        capture("attendance-refresh-failure.png")
        compose.runOnIdle {
            state = state.copy(attendees = listOf(PrototypeAttendee("carol", "Carol", time + 1), PrototypeAttendee("david", "David")),
                attendance = state.attendance.copy(error = null, updatedAt = time + 1))
        }
        compose.onNodeWithTag("attendance_detail").assertDoesNotExist()
        compose.onNodeWithTag("attendee_bob").assertDoesNotExist()
        compose.onNodeWithTag("attendee_carol").assertIsDisplayed()
        compose.onNodeWithTag("attendance_pending_tab").performClick()
        compose.onNodeWithTag("attendee_david").assertIsDisplayed()
        assertEquals(qr, compose.onNodeWithTag("prototype_qr").assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state = state.copy(attendees = emptyList()) }
        compose.onNodeWithText("No confirmed participants yet").assertIsDisplayed()
    }

    @Test fun endedOldActivityHasReadOnlyAttendanceSearchAndTimeWithoutCurrentQr() {
        val old = session.copy(id = "e13e4af8-b3ed-49ef-a7ee-d7c1cf2bf318", startAt = time - 120_000, endAt = time - 60_000)
        state = state.copy(history = listOf(PrototypeHistoryEntry(old, null, null, false)))
        var requested: PrototypeSession? = null
        launch { requested = it }
        compose.onNodeWithTag("organizer_more").performClick()
        compose.onNodeWithTag("open_history").performScrollTo().performClick()
        compose.onNodeWithTag("history_${old.id}").performClick()
        compose.onNodeWithTag("history_open_attendance").performScrollTo().performClick()
        compose.onNodeWithTag("attendance_record_screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(old, requested) }
        compose.onNodeWithTag("prototype_qr").assertDoesNotExist()
        compose.onNodeWithTag("prototype_show").assertDoesNotExist()
        compose.onNodeWithText("Event ended").assertIsDisplayed()
        // Old/current activities must never share displayed people while the new load is pending.
        compose.onNodeWithTag("attendance_search_open").assertIsNotEnabled()
        compose.runOnIdle {
            state = state.copy(attendees = listOf(PrototypeAttendee("bob", "Bob", old.startAt), PrototypeAttendee("carol", "Carol")),
                attendance = AttendanceLoadState(old, loaded = true, updatedAt = time))
        }
        compose.onNodeWithTag("attendee_bob").performClick()
        compose.onNodeWithTag("attendance_detail").assertTextContains("Time:", substring = true)
        capture("attendance-ended-record.png")
        compose.onNodeWithContentDescription("Hide check-in details").performClick()
        compose.onNodeWithTag("attendance_search_open").performClick()
        compose.onNodeWithTag("attendance_search_input").performTextInput("carol")
        compose.onNodeWithTag("attendance_search_input").performImeAction()
        compose.onNodeWithTag("search_person_carol").assertIsDisplayed().performClick()
        compose.onNodeWithTag("attendance_detail").assertTextEquals("Not checked in")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText("< Back").performClick()
        compose.onNodeWithTag("history_open_attendance").assertExists()
        compose.runOnIdle { assertEquals(session, state.session) }
    }

    @Test fun visibleRosterReloadsOnResumeAndStopsWhenBackgrounded() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle = registry
        }
        var requests = 0
        var stops = 0
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                GogetherTheme { PrototypeScreen("alice", state, time, {}, {}, {}, {}, {}, {}, {}, {},
                    refreshAttendance = { requests++ }, stopAttendance = { stops++ }) }
            }
        }
        compose.runOnIdle { assertEquals(1, requests); owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, stops); owner.registry.currentState = Lifecycle.State.STARTED }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, requests); owner.registry.currentState = Lifecycle.State.DESTROYED }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, stops) }
    }

    @Test fun endedAttendanceCanBeReadAfterRecreatingLocalStore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val old = session.copy(id = UUID.randomUUID().toString())
        val prefs = context.getSharedPreferences("checkin_prototype_v1", 0)
        try {
            PreferencesPrototypeStore(context).apply {
                rememberSession("alice", old)
                save(PrototypeReceipt(old.id, "bob", time))
            }
            val reopened = LocalPrototypeBackend(PreferencesPrototypeStore(context), { old.endAt + 1 }, { 0 })
            assertTrue(reopened.history("alice").any { it.session == old })
            assertEquals(time, reopened.attendance("alice", old).single { it.userId == "bob" }.checkedInAt)
            assertThrows(IllegalArgumentException::class.java) { reopened.attendance("bob", old) }
        } finally {
            prefs.edit().remove("session.${old.id}").remove("member.${old.id}.alice")
                .remove("receipt.${old.id}.bob").commit()
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onNodeWithTag("prototype_screen").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

package com.gogether.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PhaseOneAcceptanceTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val time = System.currentTimeMillis()
    private val seed = DemoData.create(time).let { s -> s.copy(currentUserId = "alice", events = listOf(
        s.event("event-1")!!.copy(createdAt = System.nanoTime(), startAt = time + 600_000,
            endAt = time + 7_800_000, registrationDeadline = time + 300_000, capacity = 6))) }
    private val repo = FakeGogetherRepository(seed, { time })
    private val store = PreferencesPrototypeStore(context)
    private val bridge = RegistrationAttendanceBridge({ repo.state.value }, store, { time })
    private val id = bridge.key(seed.events.single())
    @After fun clean() {
        val prefs = context.getSharedPreferences("checkin_prototype_v1", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        prefs.all.keys.filter { it.contains(id) }.forEach(edit::remove)
        edit.commit()
    }
    @Test fun organizerConfirmsArrivalBeforeCodeAndIsOutsideParticipantPendingCount() {
        compose.setContent {
            val displayNow by produceState(System.currentTimeMillis()) {
                while (true) { value = System.currentTimeMillis(); delay(250) }
            }
            GogetherTheme { RepositoryCheckInScreen(repo, "event-1", displayNow, {}) }
        }
        compose.onNodeWithTag("prototype_show").assertIsEnabled().performClick()
        compose.onNodeWithText("Confirm your arrival?").assertIsDisplayed()
        assertNull(store.receipt(id, "alice"))
        compose.onNodeWithText("Confirm and show code").performClick()
        compose.waitUntil(5_000) { store.receipt(id, "alice") != null }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("prototype_qr").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("prototype_qr").assertIsDisplayed()
        assertTrue(store.receipt(id, "alice")!!.organizer)
        compose.onNodeWithTag("attendance_refresh").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("3 participants, 0 checked in").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("organizer_attendance").assertTextContains("Arrival confirmed", substring = true)
        val first = store.receipt(id, "alice")
        compose.onNodeWithTag("prototype_show").performClick()
        compose.onNodeWithText("Confirm your arrival?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(first, store.receipt(id, "alice")) }
    }
    @Test fun endedUnattendedMemberSeesRecordInsteadOfReviewEditor() {
        val ended = FakeGogetherRepository(seed.copy(currentUserId = "bob", events = seed.events.map { it.copy(status = EventStatus.COMPLETED) }), { time })
        compose.setContent { GogetherTheme { RepositoryCheckInScreen(ended, "event-1", time, {}) } }
        compose.onNodeWithTag("feedback_editor").assertDoesNotExist()
        compose.onNodeWithTag("feedback_submit").assertDoesNotExist()
        compose.onNodeWithText("You did not check in. A valid check-in is required to review this event.").performScrollTo().assertIsDisplayed()
    }
    @Test fun sameTimeRejoinPersistsNewReceiptAndArchivesOldCycle(): Unit = runBlocking {
        val session = bridge.prepare("alice", "event-1")
        val raw = PrototypeCodeCodec.encode(bridge.backend.issue("alice", session))
        repo.login("bob").getOrThrow()
        val old = bridge.backend.submit("bob", raw)
        repo.cancelRegistration("event-1").getOrThrow(); repo.join("event-1").getOrThrow()
        val reopened = RegistrationAttendanceBridge({ repo.state.value }, PreferencesPrototypeStore(context), { time })
        assertNull(reopened.receipt(repo.state.value.event("event-1")!!, "bob"))
        val fresh = reopened.backend.submit("bob", raw)
        assertNotEquals(old.registrationKey, fresh.registrationKey)
        assertEquals(fresh, PreferencesPrototypeStore(context).receipt(id, "bob"))
        val prefs = context.getSharedPreferences("checkin_prototype_v1", Context.MODE_PRIVATE)
        assertTrue(prefs.all.keys.any { it.startsWith("receipt-archive.$id.bob.") })
        val file = File(context.cacheDir, "phase1-$id.json")
        try {
            val snapshot = reopened.withAttendance(repo.state.value)
            FileSnapshotStore(file).save(snapshot)
            assertEquals(fresh.registrationKey, FileSnapshotStore(file).load()!!.activeCheckIn("event-1", "bob")!!.registrationKey)
        } finally { file.delete() }
    }
    @Test fun legacySavedCoordinatesAreDiscardedAndNoLocationPermissionsRemain() {
        val legacy = "GOGETHER-LOCAL-QR-1|$id|31.2|120.6|${time+600_000}|${time+7_800_000}|$time|${time+30_000}"
        val saved = PrototypeSessionStorage.decode(legacy)
        assertEquals(id, saved.id)
        assertEquals(PrototypeSession(id, time+600_000, time+7_800_000), saved)
        assertThrows(IllegalArgumentException::class.java) { PrototypeCodeCodec.decode(legacy) }
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.any { it.contains("LOCATION") })
    }
    @Test fun organizerReviewEditorDisablesAnonymousAndSanitizesOldDraft() {
        val session = bridge.prepare("alice", "event-1")
        bridge.backend.issue("alice", session)
        FeedbackDraftStore(context).save("alice", id, FeedbackDraft(4, "Host review", FeedbackDetails(anonymous = true)))
        val ended = FakeGogetherRepository(seed.copy(events = seed.events.map { it.copy(status = EventStatus.COMPLETED) }), { time })
        compose.setContent { GogetherTheme { RepositoryCheckInScreen(ended, "event-1", time, {}) } }
        compose.reviewNode("feedback_anonymous").assertIsOff().assertIsNotEnabled()
        compose.reviewNode("feedback_submit").assertIsEnabled()
    }
}

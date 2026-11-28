package com.gogether.app

import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RegistrationCycleTest {
    private var now = 1_800_000_000_000L
    private val initial = DemoData.create(now).let { s -> s.copy(currentUserId = "alice", events = s.events.map {
        if (it.id == "event-1") it.copy(startAt = now + 600_000, endAt = now + 7_800_000,
            registrationDeadline = now + 300_000, capacity = 6) else it
    }) }
    @Test fun sameMillisecondRejoinCannotReuseOldReceiptOrOldAttempt() = runTest {
        val repo = FakeGogetherRepository(initial, { now })
        val store = MemoryPrototypeStore()
        val bridge = RegistrationAttendanceBridge({ repo.state.value }, store, { now })
        val session = bridge.prepare("alice", "event-1")
        val raw = PrototypeCodeCodec.encode(bridge.backend.issue("alice", session))
        repo.login("bob").getOrThrow()
        val before = bridge.backend.submit("bob", raw)
        repo.cancelRegistration("event-1").getOrThrow()
        assertNull(bridge.receipt(repo.state.value.event("event-1")!!, "bob"))
        repo.join("event-1").getOrThrow()
        assertNull(bridge.receipt(repo.state.value.event("event-1")!!, "bob"))
        assertEquals(before, store.receipt(session.id, "bob"))
        assertThrows(IllegalArgumentException::class.java) { bridge.backend.submit("bob", raw, before.registrationKey) }
        val after = bridge.backend.submit("bob", raw)
        assertEquals(before.checkedInAt, after.checkedInAt)
        assertNotEquals(before.registrationKey, after.registrationKey)
        assertEquals(after, bridge.receipt(repo.state.value.event("event-1")!!, "bob"))
        now = session.endAt
        bridge.backend.submitFeedback("bob", session.id, 4, "Attended after rejoining")
    }
    @Test fun legacyReceiptBeforeLatestJoinCannotGrantReviewOrParticipation() {
        val reg = initial.registration("event-1", "bob")!!
        val state = initial.copy(checkIns = listOf(CheckIn("legacy", "event-1", "bob", reg.joinedAt - 1)))
        assertNull(state.activeCheckIn("event-1", "bob"))
        assertNotNull(state.copy(checkIns = listOf(CheckIn("legacy", "event-1", "bob", reg.joinedAt))).activeCheckIn("event-1", "bob"))
    }
    @Test fun rejoinWithoutNewScanCannotReviewAfterEnd() = runTest {
        val repo = FakeGogetherRepository(initial, { now })
        val bridge = RegistrationAttendanceBridge({ repo.state.value }, MemoryPrototypeStore(), { now })
        val session = bridge.prepare("alice", "event-1")
        val raw = PrototypeCodeCodec.encode(bridge.backend.issue("alice", session))
        repo.login("bob").getOrThrow(); bridge.backend.submit("bob", raw)
        repo.cancelRegistration("event-1").getOrThrow(); repo.join("event-1").getOrThrow()
        now = session.endAt
        assertThrows(IllegalArgumentException::class.java) { bridge.backend.submitFeedback("bob", session.id, 5, "") }
        assertNull(bridge.withAttendance(repo.state.value).activeCheckIn("event-1", "bob"))
    }
}

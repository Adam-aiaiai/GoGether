package com.gogether.app

import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceRefreshTest {
    private val time = 1_800_000_000_000L
    private val fix = PositionFix(MeetingPoint(31.27, 120.74), 10.0, 0)
    private fun scenario(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }
    private fun backend(store: MemoryPrototypeStore = MemoryPrototypeStore()) = LocalPrototypeBackend(store, { time }, { 0 })

    @Test fun loadingFailureAndRetryAreIndependentOfQrAndLocation() = scenario {
        val backend = backend()
        val session = backend.createMeeting("alice", fix)
        var locations = 0
        var requests = 0
        var response = CompletableDeferred<List<PrototypeAttendee>>()
        val model = PrototypeViewModel("alice", backend, PositionSource { locations++; fix }, now = { time },
            attendanceSource = AttendanceSource { _, _ -> requests++; response.await() })
        assertEquals(0, requests) // Constructor must not perform a fallible roster request.
        model.showCode(); runCurrent()
        val qr = model.state.value.code
        assertNotNull(qr)
        assertEquals(0, requests)
        model.refreshAttendance(session); runCurrent()
        assertTrue(model.state.value.attendance.loading)
        assertFalse(model.state.value.attendance.loaded)
        assertFalse(model.state.value.busy)
        model.refreshAttendance(session); runCurrent()
        assertEquals(1, requests) // Coalesce repeated clicks while loading.
        response.completeExceptionally(IllegalStateException("offline")); runCurrent()
        assertFalse(model.state.value.attendance.loading)
        assertNotNull(model.state.value.attendance.error)
        assertEquals(qr, model.state.value.code)
        response = CompletableDeferred()
        model.refreshAttendance(session); runCurrent()
        response.complete(listOf(PrototypeAttendee("bob", "Bob", time))); runCurrent()
        assertTrue(model.state.value.attendance.loaded)
        assertNull(model.state.value.attendance.error)
        assertNull(model.state.value.notice) // First successful snapshot is not a new arrival.
        assertEquals(1, locations)
        assertEquals(qr, model.state.value.code)
        model.leaveForeground()
    }

    @Test fun failedUpdateKeepsSnapshotAndRetryAppliesMembershipAndTimestampChanges() = scenario {
        val backend = backend()
        val session = backend.createMeeting("alice", fix)
        var people = listOf(PrototypeAttendee("bob", "Bob"), PrototypeAttendee("carol", "Carol"))
        var failure = false
        val model = PrototypeViewModel("alice", backend, PositionSource { error("Must not locate") }, now = { time },
            attendanceSource = AttendanceSource { _, _ -> if (failure) error("offline") else people })
        model.refreshAttendance(session); runCurrent()
        val initial = model.state.value.attendees
        failure = true
        model.refreshAttendance(session); runCurrent()
        assertEquals(initial, model.state.value.attendees)
        assertTrue(model.state.value.attendance.loaded)
        assertNotNull(model.state.value.attendance.error)
        failure = false
        people = listOf(PrototypeAttendee("bob", "Bob New Name", time), PrototypeAttendee("david", "David"))
        model.refreshAttendance(session); runCurrent()
        assertEquals(people, model.state.value.attendees)
        assertEquals("Bob New Name just checked in", model.state.value.notice!!.text)
        model.dismissNotice(model.state.value.notice!!.id)
        model.refreshAttendance(session); runCurrent()
        assertNull(model.state.value.notice)
        people = emptyList()
        model.refreshAttendance(session); runCurrent()
        assertTrue(model.state.value.attendance.loaded)
        assertTrue(model.state.value.attendees.isEmpty())
        // A removed participant reappearing with the same receipt is not another check-in.
        people = listOf(PrototypeAttendee("bob", "Bob", time))
        model.refreshAttendance(session); runCurrent()
        assertNull(model.state.value.notice)
    }

    @Test fun oldActivityResponseCannotOverwriteNewSelectionEvenIfSourceIgnoresCancellation() = scenario {
        val backend = backend()
        val first = backend.createMeeting("alice", fix)
        val second = backend.createMeeting("alice", fix)
        val oldResponse = CompletableDeferred<List<PrototypeAttendee>>()
        val model = PrototypeViewModel("alice", backend, PositionSource { fix }, now = { time },
            attendanceSource = AttendanceSource { _, session ->
                if (session == first) withContext(NonCancellable) { oldResponse.await() }
                else listOf(PrototypeAttendee("carol", "Carol"))
            })
        model.refreshAttendance(first); runCurrent()
        model.refreshAttendance(second); runCurrent()
        assertEquals(second, model.state.value.attendance.session)
        oldResponse.complete(listOf(PrototypeAttendee("bob", "Bob", time))); runCurrent()
        assertEquals(listOf("carol"), model.state.value.attendees.map { it.userId })
        assertEquals(second, model.state.value.attendance.session)
        assertNull(model.state.value.notice)
    }

    @Test fun logoutDuringLoadClearsRosterAndDiscardsLateResult() = scenario {
        val backend = backend()
        val session = backend.createMeeting("alice", fix)
        var user: String? = "alice"
        val response = CompletableDeferred<List<PrototypeAttendee>>()
        val model = PrototypeViewModel("alice", backend, PositionSource { fix }, now = { time },
            identity = CheckInIdentitySource { user },
            attendanceSource = AttendanceSource { _, _ -> withContext(NonCancellable) { response.await() } })
        model.refreshAttendance(session); runCurrent()
        user = "bob"
        assertFalse(model.refreshIdentity())
        response.complete(listOf(PrototypeAttendee("bob", "Bob", time))); runCurrent()
        assertTrue(model.state.value.identityExpired)
        assertTrue(model.state.value.attendees.isEmpty())
        assertNull(model.state.value.attendance.session)
        model.refreshAttendance(session); runCurrent()
        assertTrue(model.state.value.attendees.isEmpty())
    }

    @Test fun leavingCancelsRosterRequestAndResumeCanRetryWithoutQr() = scenario {
        val backend = backend()
        val session = backend.createMeeting("alice", fix)
        var cancelled = false
        var first = true
        val model = PrototypeViewModel("alice", backend, PositionSource { error("No GPS") }, now = { time },
            attendanceSource = AttendanceSource { _, _ ->
                if (first) try { awaitCancellation() } finally { cancelled = true }
                else emptyList()
            })
        model.refreshAttendance(session); runCurrent()
        model.leaveForeground(); runCurrent()
        assertTrue(cancelled)
        assertFalse(model.state.value.attendance.loading)
        assertNull(model.state.value.attendance.error)
        first = false
        model.refreshAttendance(session); runCurrent()
        assertTrue(model.state.value.attendance.loaded)
        assertNull(model.state.value.code)
    }

    @Test fun endedAndCancelledOldActivityRemainReadableWithoutIssuingCodeOrAnnouncingArrivals() = scenario {
        val store = MemoryPrototypeStore()
        val backend = backend(store)
        val first = backend.createMeeting("alice", fix)
        val current = backend.createMeeting("alice", fix)
        store.save(PrototypeReceipt(first.id, "bob", time))
        val model = PrototypeViewModel("alice", backend, PositionSource { error("No GPS") }, now = { first.endAt + 1 })
        model.refreshAttendance(first); runCurrent()
        assertEquals(time, model.state.value.attendees.single { it.userId == "bob" }.checkedInAt)
        store.save(PrototypeReceipt(first.id, "carol", time + 1))
        model.refreshAttendance(first); runCurrent()
        assertEquals(2, model.state.value.attendees.count { it.checkedInAt != null })
        assertNull(model.state.value.notice)
        assertNull(model.state.value.code)
        assertEquals(current, model.state.value.session)
        store.cancel(first.id)
        model.refreshAttendance(first); runCurrent()
        assertNull(model.state.value.attendance.error)
        assertEquals(2, model.state.value.attendees.count { it.checkedInAt != null })
        model.refreshAttendance(current); runCurrent()
        assertTrue(model.state.value.attendees.all { it.checkedInAt == null })
    }

    @Test fun participantCannotReadOrganizerRoster() = scenario {
        val backend = backend()
        val session = backend.createMeeting("alice", fix)
        var calls = 0
        val model = PrototypeViewModel("bob", backend, PositionSource { fix },
            attendanceSource = AttendanceSource { _, _ -> calls++; emptyList() })
        model.refreshAttendance(session); runCurrent()
        assertEquals(0, calls)
        assertFalse(model.state.value.attendance.loaded)
    }
}

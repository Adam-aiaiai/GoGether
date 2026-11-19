package com.gogether.app

import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultiActivityViewModelTest {
    private val time = 1_800_000_000_000L
    private val fixtures = MultiActivityCheckInScenario(time)
    private val a = fixtures.aliceFirst
    private val b = fixtures.aliceSecond
    private val c = fixtures.carolEvent
    private val store = MemoryPrototypeStore().also(fixtures::seed)
    private fun backend(clock: Long = time) = LocalPrototypeBackend(store, { clock }, { 0 }, fixtures.registrations, fixtures.activities)
    private fun fix(session: PrototypeSession) = PositionFix(session.meeting, 10.0, 0)
    private fun raw(session: PrototypeSession) = PrototypeCodeCodec.encode(PrototypeCode(session, time, time + 30_000))
    private fun test(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    @Test fun carolHostsHerOwnActivityAndBecomesParticipantWhenEnteringAlicesActivity() = test {
        val model = PrototypeViewModel("carol", backend(), PositionSource { fix(c) }, now = { time },
            entry = CheckInEntry.Activity(c), registrations = fixtures.registrations)
        assertEquals(c, model.state.value.session)
        assertNull(model.state.value.participantSession)
        model.showCode(); runCurrent()
        assertEquals(c, model.state.value.code!!.session)
        model.refreshAttendance(c); runCurrent()
        assertEquals(listOf("carol", "bob", "david"), model.state.value.attendees.map { it.userId })
        model.enterActivity(a); runCurrent()
        assertNull(model.state.value.code)
        assertNull(model.state.value.session)
        assertEquals(a, model.state.value.participantSession)
        assertTrue(model.state.value.attendees.isEmpty())
        assertNull(model.state.value.notice)
        model.leaveForeground()
    }

    @Test fun bobCannotUseASecondEventsCodeOrCarryReceiptIntoWaitlistedEvent() = test {
        var locations = 0
        val model = PrototypeViewModel("bob", backend(), PositionSource { locations++; fix(c) }, now = { time },
            entry = CheckInEntry.Activity(a), registrations = fixtures.registrations)
        model.receiveScan(raw(c), model.beginScan()); runCurrent()
        assertTrue(model.state.value.message.contains("不是本场活动"))
        assertEquals(0, locations)
        assertTrue(store.receipts.isEmpty())
        model.enterActivity(c)
        model.receiveScan(raw(c), model.beginScan()); runCurrent()
        assertEquals(c.id, model.state.value.receipt!!.sessionId)
        model.enterActivity(b)
        assertNull(model.state.value.receipt)
        assertNull(model.state.value.notice)
        assertEquals(CheckInRegistration.WAITLISTED, model.state.value.registration)
        model.receiveScan(raw(b), model.beginScan()); runCurrent()
        assertEquals(1, locations)
        assertTrue(model.state.value.message.contains("候补"))
        assertEquals(setOf(c.id), store.receipts.values.map { it.sessionId }.toSet())
    }

    @Test fun delayedRosterFromEarlierActivityCannotOverwriteNewRosterOrEmitNotice() = test {
        val old = CompletableDeferred<List<PrototypeAttendee>>()
        var calls = 0
        val model = PrototypeViewModel("alice", backend(), PositionSource { fix(a) }, now = { time },
            entry = CheckInEntry.Activity(a), registrations = fixtures.registrations,
            attendanceSource = AttendanceSource { _, session ->
                calls++
                if (session == a) withContext(NonCancellable) { old.await() } else fixtures.registrations.participants(session.id)
            })
        model.refreshAttendance(a); runCurrent()
        model.enterActivity(b)
        model.refreshAttendance(b); runCurrent()
        old.complete(listOf(PrototypeAttendee("bob", "Bob", time))); runCurrent()
        assertEquals(b, model.state.value.attendance.session)
        assertEquals(listOf("alice", "david"), model.state.value.attendees.map { it.userId })
        assertNull(model.state.value.notice)
        model.enterActivity(c)
        model.refreshAttendance(c); runCurrent()
        assertEquals(2, calls)
        assertTrue(model.state.value.attendees.isEmpty())
        model.leaveForeground()
    }

    @Test fun changingActivityClearsPrivateReviewResultBeforeAnotherLoad() = test {
        val backend = backend(a.endAt)
        backend.submitFeedback("bob", a.id, 4, "A 私密", FeedbackDetails(visibility = FeedbackVisibility.ORGANIZER_ONLY))
        backend.submitFeedback("bob", c.id, 3, "C 私密", FeedbackDetails(visibility = FeedbackVisibility.ORGANIZER_ONLY))
        val model = PrototypeViewModel("carol", backend, PositionSource { fix(c) }, now = { a.endAt },
            entry = CheckInEntry.Activity(c), registrations = fixtures.registrations)
        model.loadReviews(c); runCurrent()
        assertEquals("C 私密", model.state.value.reviews.single().comment)
        model.enterActivity(a)
        assertNull(model.state.value.reviewSessionId)
        assertTrue(model.state.value.reviews.isEmpty())
        model.loadReviews(a); runCurrent()
        assertTrue(model.state.value.reviews.isEmpty())
    }
}

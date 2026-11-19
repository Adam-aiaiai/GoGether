package com.gogether.app

import com.gogether.app.feature.checkin.prototype.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

internal class MemoryPrototypeStore : PrototypeStore {
    override var hostedSession: PrototypeSession? = null
    private val cancelled = mutableSetOf<String>()
    val receipts = mutableMapOf<Pair<String, String>, PrototypeReceipt>()
    private val known = mutableMapOf<String, PrototypeSession>()
    private val members = mutableMapOf<String, MutableSet<String>>()
    private val feedbacks = mutableMapOf<Pair<String, String>, PrototypeFeedback>()
    override fun isCancelled(id: String) = id in cancelled
    override fun cancel(id: String) { cancelled += id }
    override fun receipt(sessionId: String, userId: String) = receipts[sessionId to userId]
    @Synchronized override fun save(receipt: PrototypeReceipt) {
        require(receipts[receipt.sessionId to receipt.userId] == null) { "You are already checked in." }
        receipts[receipt.sessionId to receipt.userId] = receipt
    }
    @Synchronized override fun rememberSession(userId: String, session: PrototypeSession) {
        require(known[session.id] == null || known[session.id] == session) { "The event details do not match the saved record." }
        known[session.id] = session
        members.getOrPut(userId) { mutableSetOf() }.add(session.id)
    }
    override fun sessions(userId: String) = members[userId].orEmpty().mapNotNull(known::get)
    override fun feedback(sessionId: String, userId: String) = feedbacks[sessionId to userId]
    override fun feedbackForSession(sessionId: String) = feedbacks.values.filter { it.sessionId == sessionId }
    @Synchronized override fun saveFeedback(feedback: PrototypeFeedback) {
        require(feedbacks[feedback.sessionId to feedback.userId] == null) { "You have already reviewed this event." }
        feedbacks[feedback.sessionId to feedback.userId] = feedback
    }
}

class PrototypeProtocolTest {
    @Test fun attendanceUsesOnlyConfirmedPeopleAndThisEventsLocalReceipts() {
        val store = MemoryPrototypeStore()
        val host = backend(store)
        val session = host.createMeeting("alice", fix())
        store.save(PrototypeReceipt(session.id, "bob", now))
        store.save(PrototypeReceipt(session.id, "emma", now))
        store.save(PrototypeReceipt(UUID.randomUUID().toString(), "carol", now))
        val people = host.attendance("alice", session)
        assertEquals(listOf("alice", "bob", "carol", "david"), people.map { it.userId })
        assertEquals(listOf("bob"), people.filter { it.checkedInAt != null }.map { it.userId })
        assertThrows(IllegalArgumentException::class.java) { host.attendance("bob", session) }
        val next = host.createMeeting("alice", fix())
        assertTrue(host.attendance("alice", next).all { it.checkedInAt == null })
        now = session.endAt + 1
        assertEquals(people, host.attendance("alice", session))
        store.cancel(session.id)
        assertEquals(people, host.attendance("alice", session))
        assertThrows(IllegalArgumentException::class.java) { host.attendance("alice", session.copy(endAt = session.endAt + 1)) }
        assertThrows(IllegalArgumentException::class.java) { host.attendance("alice", session.copy(id = UUID.randomUUID().toString())) }
    }

    @Test fun attendanceDoesNotInventSynchronizationBetweenPhones() {
        val host = backend()
        val session = host.createMeeting("alice", fix())
        val raw = PrototypeCodeCodec.encode(host.issue("alice", session, fix()))
        backend().submit("bob", raw, fix())
        assertTrue(host.attendance("alice", session).all { it.checkedInAt == null })
    }

    private var now = 1_800_000_000_000L
    private var elapsed = 100_000L
    private val point = MeetingPoint(0.0, 0.0)
    private fun fix(distance: Double = 0.0) = PositionFix(
        MeetingPoint(Math.toDegrees(distance / 6_371_000), 0.0), 10.0, elapsed)
    private fun backend(store: PrototypeStore = MemoryPrototypeStore()) =
        LocalPrototypeBackend(store, { now }, { elapsed })

    @Test fun bothPhonesMustBeWithin200MetersOfTheSameFixedMeetingPoint() {
        val host = backend()
        val session = host.createMeeting("alice", fix())
        assertEquals(point, session.meeting)
        assertThrows(IllegalArgumentException::class.java) { host.issue("alice", session, fix(201.0)) }
        val code = host.issue("alice", session, fix(200.0))
        assertEquals(point, code.session.meeting)
        val attendee = backend()
        val raw = PrototypeCodeCodec.encode(code)
        assertThrows(IllegalArgumentException::class.java) { attendee.submit("bob", raw, fix(200.01)) }
        assertEquals("bob", attendee.submit("bob", raw, fix(200.0)).userId)
    }

    @Test fun separateDevicesCanScanSameCodeWithoutSharingMemoryOrConsumingItGlobally() {
        val host = backend()
        val session = host.createMeeting("alice", fix())
        val raw = PrototypeCodeCodec.encode(host.issue("alice", session, fix()))
        val attendeeStore = MemoryPrototypeStore()
        val attendee = backend(attendeeStore)
        attendee.submit("bob", raw, fix())
        attendee.submit("carol", raw, fix())
        assertEquals(2, attendeeStore.receipts.size)
        // Reconstructing the service with the same durable store must not allow a second record.
        assertThrows(IllegalArgumentException::class.java) { backend(attendeeStore).submit("bob", raw, fix()) }
    }

    @Test fun staleInaccurateFutureAndMockPositionsCannotCreateIssueOrSubmit() {
        val host = backend()
        val session = host.createMeeting("alice", fix())
        val raw = PrototypeCodeCodec.encode(host.issue("alice", session, fix()))
        val bad = listOf(fix().copy(accuracyMeters = 50.01), fix().copy(accuracyMeters = Double.NaN),
            fix().copy(accuracyMeters = -1.0), fix().copy(elapsedMillis = elapsed - 15_001),
            fix().copy(elapsedMillis = elapsed + 1), fix().copy(elapsedMillis = Long.MIN_VALUE),
            fix().copy(simulated = true))
        bad.forEach { sample ->
            assertThrows(IllegalArgumentException::class.java) { host.createMeeting("alice", sample) }
            assertThrows(IllegalArgumentException::class.java) { host.issue("alice", session, sample) }
            assertThrows(IllegalArgumentException::class.java) { backend().submit("bob", raw, sample) }
        }
        validatePosition(fix().copy(accuracyMeters = 50.0, elapsedMillis = elapsed - 15_000), elapsed, point)
    }

    @Test fun expiredCodeAndFutureCodeAreRejectedAtSubmission() {
        val host = backend()
        val session = host.createMeeting("alice", fix())
        val code = host.issue("alice", session, fix())
        val raw = PrototypeCodeCodec.encode(code)
        now = code.issuedAt - 5_001
        assertThrows(IllegalArgumentException::class.java) { backend().submit("bob", raw, fix()) }
        now = code.expiresAt - 1
        backend().submit("bob", raw, fix())
        now++
        assertThrows(IllegalArgumentException::class.java) { backend().submit("carol", raw, fix()) }
    }

    @Test fun identityAndMockRegistrationAreEnforced() {
        val host = backend()
        assertThrows(IllegalArgumentException::class.java) { host.createMeeting("bob", fix()) }
        val session = host.createMeeting("alice", fix())
        assertThrows(IllegalArgumentException::class.java) { host.issue("bob", session, fix()) }
        val raw = PrototypeCodeCodec.encode(host.issue("alice", session, fix()))
        listOf("emma", "finn", "", "unknown").forEach { user ->
            assertThrows(IllegalArgumentException::class.java) { backend().submit(user, raw, fix()) }
        }
    }

    @Test fun windowEndpointsAreInclusiveButEventEndStillClosesIt() {
        val store = MemoryPrototypeStore()
        val host = backend(store)
        val session = host.createMeeting("alice", fix())
        now = session.startAt - 1_800_001
        assertThrows(IllegalArgumentException::class.java) { host.issue("alice", session, fix()) }
        now++
        host.issue("alice", session, fix()).requireValid(now)
        now = session.startAt + 1_800_000
        host.issue("alice", session, fix()).requireValid(now)
        now++
        assertThrows(IllegalArgumentException::class.java) { host.issue("alice", session, fix()) }
        val short = session.copy(endAt = session.startAt + 100)
        store.hostedSession = short
        now = short.endAt
        assertThrows(IllegalArgumentException::class.java) { host.issue("alice", short, fix()) }
    }

    @Test fun cancellationStopsLocalIssuanceAndSubmissionWithoutExpandingCancellationPermission() {
        val host = backend()
        val session = host.createMeeting("alice", fix())
        val raw = PrototypeCodeCodec.encode(host.issue("alice", session, fix()))
        host.cancel("alice", session)
        assertThrows(IllegalArgumentException::class.java) { host.issue("alice", session, fix()) }
        assertThrows(IllegalArgumentException::class.java) { host.submit("bob", raw, fix()) }
        // Documented mock limitation: an offline second device knows no remote cancellation.
        backend().submit("bob", raw, fix())
        now += 30_000
        assertThrows(IllegalArgumentException::class.java) { backend().submit("carol", raw, fix()) }
        val other = backend()
        val next = other.createMeeting("alice", fix())
        now = next.startAt
        assertThrows(IllegalArgumentException::class.java) { other.cancel("alice", next) }
    }

    @Test fun malformedPayloadsNeverBecomeAnEventOrOpenAnExternalUrl() {
        val bad = listOf("", "https://example.com", "x".repeat(513),
            "GOGETHER-LOCAL-QR-2|x", "GOGETHER-LOCAL-QR-1|${UUID.randomUUID()}|NaN|0|1|2|1|2",
            "GOGETHER-LOCAL-QR-1|${UUID.randomUUID()}|91|0|1|2|1|2")
        bad.forEach { assertThrows(IllegalArgumentException::class.java) { PrototypeCodeCodec.decode(it) } }
        val host = backend()
        val session = host.createMeeting("alice", fix())
        val code = host.issue("alice", session, fix())
        assertEquals(code, PrototypeCodeCodec.decode(PrototypeCodeCodec.encode(code)))
        assertThrows(IllegalArgumentException::class.java) {
            code.copy(expiresAt = code.issuedAt + 30_001).requireValid(now)
        }
    }

    @Test fun startingAnotherSessionDoesNotSilentlyMoveTheOldMeetingPoint() {
        val host = backend()
        val first = host.createMeeting("alice", fix())
        val second = host.createMeeting("alice", fix(500.0))
        assertNotEquals(first.id, second.id)
        assertEquals(point, first.meeting)
        assertThrows(IllegalArgumentException::class.java) { host.issue("alice", first, fix()) }
        host.issue("alice", second, fix(500.0))
    }

    @Test fun refreshProducesNewExpiryAndOldCodeStillExpiresIndependently() {
        val host = backend()
        val session = host.createMeeting("alice", fix())
        val first = host.issue("alice", session, fix())
        now += 10_000
        elapsed += 10_000
        val second = host.issue("alice", session, fix())
        assertNotEquals(PrototypeCodeCodec.encode(first), PrototypeCodeCodec.encode(second))
        now = first.expiresAt
        assertThrows(IllegalArgumentException::class.java) { first.requireValid(now) }
        second.requireValid(now)
    }
}

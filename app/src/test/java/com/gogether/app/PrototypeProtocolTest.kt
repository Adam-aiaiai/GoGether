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
    override fun updateLinkedActivity(info: CheckInActivityInfo) { known[info.session.id] = info.session }
    override fun isCancelled(id: String) = id in cancelled
    override fun cancel(id: String) { cancelled += id }
    override fun receipt(sessionId: String, userId: String) = receipts[sessionId to userId]
    @Synchronized override fun save(receipt: PrototypeReceipt) {
        require(receipts[receipt.sessionId to receipt.userId]?.let { it.registrationKey == receipt.registrationKey } != true) { "You are already checked in." }
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
        val session = host.createMeeting("alice")
        store.save(PrototypeReceipt(session.id, "bob", now))
        store.save(PrototypeReceipt(session.id, "emma", now))
        store.save(PrototypeReceipt(UUID.randomUUID().toString(), "carol", now))
        val people = host.attendance("alice", session)
        assertEquals(listOf("alice", "bob", "carol", "david"), people.map { it.userId })
        assertEquals(listOf("bob"), people.filter { it.checkedInAt != null }.map { it.userId })
        assertThrows(IllegalArgumentException::class.java) { host.attendance("bob", session) }
        val next = host.createMeeting("alice")
        assertTrue(host.attendance("alice", next).all { it.checkedInAt == null })
        now = session.endAt + 1
        assertEquals(people, host.attendance("alice", session))
        store.cancel(session.id)
        assertTrue(host.attendance("alice", session).all { it.checkedInAt == null })
        assertThrows(IllegalArgumentException::class.java) { host.attendance("alice", session.copy(endAt = session.endAt + 1)) }
        assertThrows(IllegalArgumentException::class.java) { host.attendance("alice", session.copy(id = UUID.randomUUID().toString())) }
    }

    @Test fun attendanceDoesNotInventSynchronizationBetweenPhones() {
        val host = backend()
        val session = host.createMeeting("alice")
        val raw = PrototypeCodeCodec.encode(host.issue("alice", session))
        backend().submit("bob", raw)
        assertTrue(host.attendance("alice", session).filterNot { it.organizer }.all { it.checkedInAt == null })
    }

    private var now = 1_800_000_000_000L
    private var elapsed = 100_000L

    private fun backend(store: PrototypeStore = MemoryPrototypeStore()) =
        LocalPrototypeBackend(store, { now })

    @Test fun qrHasNoCoordinatesAndIssueConfirmsOrganizerExactlyOnce() {
        val store = MemoryPrototypeStore()
        val host = backend(store)
        val session = host.createMeeting("alice")
        assertNull(store.receipt(session.id, "alice"))
        val code = host.issue("alice", session)
        val first = store.receipt(session.id, "alice")!!
        assertTrue(first.organizer)
        assertEquals(6, PrototypeCodeCodec.encode(code).split('|').size)
        now += 10_000
        host.issue("alice", session)
        assertEquals(first, store.receipt(session.id, "alice"))
        val attendee = backend()
        assertEquals("bob", attendee.submit("bob", PrototypeCodeCodec.encode(code)).userId)
        assertThrows(IllegalArgumentException::class.java) { host.submit("alice", PrototypeCodeCodec.encode(code)) }
    }

    @Test fun separateDevicesCanScanSameCodeWithoutSharingMemoryOrConsumingItGlobally() {
        val host = backend()
        val session = host.createMeeting("alice")
        val raw = PrototypeCodeCodec.encode(host.issue("alice", session))
        val attendeeStore = MemoryPrototypeStore()
        val attendee = backend(attendeeStore)
        attendee.submit("bob", raw)
        attendee.submit("carol", raw)
        assertEquals(2, attendeeStore.receipts.size)
        // Reconstructing the service with the same durable store must not allow a second record.
        assertThrows(IllegalArgumentException::class.java) { backend(attendeeStore).submit("bob", raw) }
    }

    @Test fun failedIssuanceDoesNotConfirmArrivalAndOldQrIsRejected() {
        val store = MemoryPrototypeStore()
        val host = backend(store)
        val session = host.createMeeting("alice")
        now = session.startAt - PrototypePolicy.HALF_HOUR_MS - 1
        assertThrows(IllegalArgumentException::class.java) { host.issue("alice", session) }
        assertNull(store.receipt(session.id, "alice"))
        val old = "GOGETHER-LOCAL-QR-1|${session.id}|31.2|120.6|${session.startAt}|${session.endAt}|$now|${now+30_000}"
        assertThrows(IllegalArgumentException::class.java) { PrototypeCodeCodec.decode(old) }
    }

    @Test fun expiredCodeAndFutureCodeAreRejectedAtSubmission() {
        val host = backend()
        val session = host.createMeeting("alice")
        val code = host.issue("alice", session)
        val raw = PrototypeCodeCodec.encode(code)
        now = code.issuedAt - 5_001
        assertThrows(IllegalArgumentException::class.java) { backend().submit("bob", raw) }
        now = code.expiresAt - 1
        backend().submit("bob", raw)
        now++
        assertThrows(IllegalArgumentException::class.java) { backend().submit("carol", raw) }
    }

    @Test fun identityAndMockRegistrationAreEnforced() {
        val host = backend()
        assertThrows(IllegalArgumentException::class.java) { host.createMeeting("bob") }
        val session = host.createMeeting("alice")
        assertThrows(IllegalArgumentException::class.java) { host.issue("bob", session) }
        val raw = PrototypeCodeCodec.encode(host.issue("alice", session))
        listOf("emma", "finn", "", "unknown").forEach { user ->
            assertThrows(IllegalArgumentException::class.java) { backend().submit(user, raw) }
        }
    }

    @Test fun windowEndpointsAreInclusiveButEventEndStillClosesIt() {
        val store = MemoryPrototypeStore()
        val host = backend(store)
        val session = host.createMeeting("alice")
        now = session.startAt - 1_800_001
        assertThrows(IllegalArgumentException::class.java) { host.issue("alice", session) }
        now++
        host.issue("alice", session).requireValid(now)
        now = session.startAt + 1_800_000
        host.issue("alice", session).requireValid(now)
        now++
        assertThrows(IllegalArgumentException::class.java) { host.issue("alice", session) }
        val short = session.copy(endAt = session.startAt + 100)
        store.hostedSession = short
        now = short.endAt
        assertThrows(IllegalArgumentException::class.java) { host.issue("alice", short) }
    }

    @Test fun cancellationStopsLocalIssuanceAndSubmissionWithoutExpandingCancellationPermission() {
        val host = backend()
        val session = host.createMeeting("alice")
        val raw = PrototypeCodeCodec.encode(host.issue("alice", session))
        host.cancel("alice", session)
        assertThrows(IllegalArgumentException::class.java) { host.issue("alice", session) }
        assertThrows(IllegalArgumentException::class.java) { host.submit("bob", raw) }
        // Documented mock limitation: an offline second device knows no remote cancellation.
        backend().submit("bob", raw)
        now += 30_000
        assertThrows(IllegalArgumentException::class.java) { backend().submit("carol", raw) }
        val other = backend()
        val next = other.createMeeting("alice")
        now = next.startAt
        assertThrows(IllegalArgumentException::class.java) { other.cancel("alice", next) }
    }

    @Test fun malformedPayloadsNeverBecomeAnEventOrOpenAnExternalUrl() {
        val bad = listOf("", "https://example.com", "x".repeat(513),
            "GOGETHER-LOCAL-QR-2|x", "GOGETHER-LOCAL-QR-1|${UUID.randomUUID()}|NaN|0|1|2|1|2",
            "GOGETHER-LOCAL-QR-1|${UUID.randomUUID()}|91|0|1|2|1|2")
        bad.forEach { assertThrows(IllegalArgumentException::class.java) { PrototypeCodeCodec.decode(it) } }
        val host = backend()
        val session = host.createMeeting("alice")
        val code = host.issue("alice", session)
        assertEquals(code, PrototypeCodeCodec.decode(PrototypeCodeCodec.encode(code)))
        assertThrows(IllegalArgumentException::class.java) {
            code.copy(expiresAt = code.issuedAt + 30_001).requireValid(now)
        }
    }

    @Test fun startingAnotherSessionDoesNotSilentlyMoveTheOldMeetingPoint() {
        val host = backend()
        val first = host.createMeeting("alice")
        val second = host.createMeeting("alice")
        assertNotEquals(first.id, second.id)
        assertEquals(first.startAt, second.startAt)
        assertThrows(IllegalArgumentException::class.java) { host.issue("alice", first) }
        host.issue("alice", second)
    }

    @Test fun refreshProducesNewExpiryAndOldCodeStillExpiresIndependently() {
        val host = backend()
        val session = host.createMeeting("alice")
        val first = host.issue("alice", session)
        now += 10_000
        elapsed += 10_000
        val second = host.issue("alice", session)
        assertNotEquals(PrototypeCodeCodec.encode(first), PrototypeCodeCodec.encode(second))
        now = first.expiresAt
        assertThrows(IllegalArgumentException::class.java) { first.requireValid(now) }
        second.requireValid(now)
    }
}

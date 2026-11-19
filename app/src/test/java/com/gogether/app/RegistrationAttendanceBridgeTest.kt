package com.gogether.app

import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RegistrationAttendanceBridgeTest {
    private var time = 1_800_000_000_000L
    private val fix = PositionFix(MeetingPoint(31.2, 120.6), 5.0, 1000)
    private val seed = DemoData.create(time).let { s -> s.copy(currentUserId = "alice", events = s.events.map {
        if (it.id == "event-1") it.copy(startAt = time + 600_000, endAt = time + 7_800_000,
            registrationDeadline = time + 300_000) else it
    }) }
    private val repo = FakeGogetherRepository(seed, { time })
    private val records = MemoryPrototypeStore()
    private val metadata = mutableMapOf<String, CheckInActivityInfo>()
    private val store = object : PrototypeStore by records {
        override fun activityInfo(id: String) = metadata[id]
        override fun rememberActivityInfo(info: CheckInActivityInfo) { metadata[info.session.id] = info }
    }
    private val bridge = RegistrationAttendanceBridge({ repo.state.value }, store, { time }, { 1000 })
    private fun prepare() = bridge.prepare("alice", "event-1", fix)
    private fun raw(s: PrototypeSession) = PrototypeCodeCodec.encode(bridge.backend.issue("alice", s, fix))
    private fun rejects(action: () -> Unit) { assertThrows(IllegalArgumentException::class.java, action) }

    @Test fun liveCancellationAndPromotionDriveBothRosterAndEligibility() = runTest {
        val s = prepare(); val code = raw(s)
        repo.login("emma").getOrThrow()
        rejects { bridge.backend.submit("emma", code, fix) }
        repo.login("bob").getOrThrow(); repo.cancelRegistration("event-1").getOrThrow()
        assertEquals(CheckInRegistration.NONE, bridge.status(s.id, "bob"))
        assertEquals(CheckInRegistration.CONFIRMED, bridge.status(s.id, "emma"))
        assertEquals(repo.state.value.confirmed("event-1").map { it.userId }.toSet(), bridge.participants(s.id).map { it.userId }.toSet())
        rejects { bridge.backend.submit("bob", code, fix) }
        repo.login("emma").getOrThrow()
        assertEquals("emma", bridge.backend.submit("emma", code, fix).userId)
    }
    @Test fun validScanPersistsProjectsAndRejectsDuplicate() = runTest {
        val s = prepare(); val code = raw(s)
        repo.login("bob").getOrThrow()
        val receipt = bridge.backend.submit("bob", code, fix)
        rejects { bridge.backend.submit("bob", code, fix) }
        assertEquals(1, records.receipts.size)
        assertEquals(receipt.checkedInAt, bridge.withAttendance(repo.state.value).checkIns.single().checkedInAt)
        repo.login("alice").getOrThrow()
        assertEquals(listOf("bob"), bridge.backend.attendance("alice", s).filter { it.checkedInAt != null }.map { it.userId })
        val restored = RegistrationAttendanceBridge({ repo.state.value }, store, { time }, { 1000 })
        assertEquals(receipt.checkedInAt, restored.backend.attendance("alice", s).single { it.userId == "bob" }.checkedInAt)
    }
    @Test fun cancellationStopsIssuedCodeAndClearsCRegistrations() = runTest {
        val s = prepare(); val code = raw(s)
        repo.cancelEvent("event-1").getOrThrow()
        assertTrue(bridge.backend.isCancelled(s))
        rejects { bridge.backend.issue("alice", s, fix) }
        repo.login("bob").getOrThrow(); rejects { bridge.backend.submit("bob", code, fix) }
        assertTrue(repo.state.value.confirmed("event-1").isEmpty())
    }
    @Test fun actualAttendanceSurvivesWithdrawalWithoutInventingConfirmedPlaces() = runTest {
        val s = prepare(); val code = raw(s)
        repo.login("bob").getOrThrow(); bridge.backend.submit("bob", code, fix)
        repo.cancelRegistration("event-1").getOrThrow(); repo.login("alice").getOrThrow()
        assertFalse(bridge.participants(s.id).any { it.userId == "bob" })
        assertNotNull(bridge.backend.attendance("alice", s).single { it.userId == "bob" }.checkedInAt)
        assertEquals(1, bridge.withAttendance(repo.state.value).checkIns.size)
        assertEquals(4, repo.state.value.confirmed("event-1").size)
    }
    @Test fun unknownWrongEventTamperedAndExpiredCodesFailClosed() = runTest {
        val s = prepare(); val code = raw(s)
        repo.login("bob").getOrThrow()
        rejects { bridge.backend.submit("bob", "invalid", fix) }
        rejects { bridge.backend.submit("bob", code.replace(s.id, java.util.UUID.randomUUID().toString()), fix) }
        rejects { bridge.backend.submit("bob", code.replace("31.2", "31.3"), fix) }
        time += 30_001
        rejects { bridge.backend.submit("bob", code, fix) }
        assertTrue(records.receipts.isEmpty())
        assertEquals(CheckInRegistration.NONE, bridge.status(null, "bob"))
        assertTrue(bridge.participants("missing").isEmpty())
    }
    @Test fun noRegistrationWrongIdentityAndNonOwnerOperationsAreRejected() = runTest {
        val s = prepare(); val code = raw(s)
        rejects { bridge.backend.submit("bob", code, fix) } // Alice is actually logged in.
        repo.login("finn").getOrThrow(); rejects { bridge.backend.submit("finn", code, fix) }
        repo.login("bob").getOrThrow()
        rejects { bridge.prepare("bob", "event-1", fix) }
        rejects { bridge.backend.issue("bob", s, fix) }
        rejects { bridge.backend.attendance("bob", s) }
        repo.logout().getOrThrow(); rejects { bridge.backend.history("bob") }
    }
    @Test fun feedbackReadsCurrentCEligibilityAndAllowsExactlyOneAfterEnd() = runTest {
        val s = prepare()
        repo.login("bob").getOrThrow()
        rejects { bridge.backend.submitFeedback("bob", s.id, 5, "", FeedbackDetails()) }
        time = s.endAt
        val review = bridge.backend.submitFeedback("bob", s.id, 4, "不错", FeedbackDetails(anonymous = true,
            visibility = FeedbackVisibility.ORGANIZER_ONLY))
        rejects { bridge.backend.submitFeedback("bob", s.id, 5, "第二次", FeedbackDetails()) }
        assertEquals("不错", review.comment)
        repo.login("emma").getOrThrow(); rejects { bridge.backend.submitFeedback("emma", s.id, 5, "", FeedbackDetails()) }
        repo.login("carol").getOrThrow(); bridge.backend.history("carol")
        assertTrue(bridge.backend.reviews("carol", s).isEmpty())
        repo.login("alice").getOrThrow()
        assertEquals(1, bridge.backend.reviews("alice", s).size)
    }
    @Test fun changedEventIncarnationCannotInheritOldRecords() = runTest {
        val s = prepare(); val code = raw(s)
        repo.login("bob").getOrThrow(); bridge.backend.submit("bob", code, fix)
        val reset = repo.state.value.copy(events = repo.state.value.events.map { it.copy(createdAt = it.createdAt + 1) })
        val other = RegistrationAttendanceBridge({ reset }, store, { time }, { 1000 })
        assertNull(other.session(reset.event("event-1")!!))
        assertTrue(other.withAttendance(reset).checkIns.isEmpty())
        rejects { other.backend.submit("bob", code, fix) }
    }
    @Test fun sameOwnerMultipleEventsDoNotShareRosterOrReceipts() = runTest {
        val s = prepare(); val code = raw(s)
        val event = repo.createEvent(EventDraft("Another game", Category.SPORTS, "Meet another group",
            time + 600_000, "Court 2", "Suzhou", 3, time + 300_000)).getOrThrow()
        val second = bridge.prepare("alice", event.id, fix)
        assertNotEquals(s.id, second.id)
        assertEquals(listOf("alice"), bridge.participants(second.id).map { it.userId })
        repo.login("bob").getOrThrow(); bridge.backend.submit("bob", code, fix)
        repo.login("alice").getOrThrow()
        assertTrue(bridge.backend.attendance("alice", second).all { it.checkedInAt == null })
    }
    @Test fun legacyCheckInAndFeedbackCannotBeSubmittedAgainThroughE() = runTest {
        val s = prepare(); val code = raw(s)
        repo.login("bob").getOrThrow(); repo.checkIn("event-1").getOrThrow()
        rejects { bridge.backend.submit("bob", code, fix) }
        time = s.endAt; repo.submitFeedback("event-1", 3, "older review").getOrThrow()
        rejects { bridge.backend.submitFeedback("bob", s.id, 4, "", FeedbackDetails()) }
    }
    @Test fun repositorySaveFailureRollsBackPromotionAndPublishedState() = runTest {
        var fail = false
        var persisted = seed
        val persistent = FakeGogetherRepository(seed, { time }) { if (fail) error("disk full") else persisted = it }
        persistent.login("bob").getOrThrow()
        val before = persistent.state.value
        fail = true
        assertTrue(persistent.cancelRegistration("event-1").isFailure)
        assertEquals(before, persistent.state.value)
        assertEquals(before, persisted)
        fail = false
        persistent.cancelRegistration("event-1").getOrThrow()
        assertEquals(RegistrationStatus.CONFIRMED, persistent.state.value.registration("event-1", "emma")!!.status)
    }
    @Test fun differentOrganizerCannotIssueReadOrReplaceAnotherEventsMeeting() = runTest {
        val first = prepare()
        repo.login("carol").getOrThrow()
        val event = repo.createEvent(EventDraft("Carol running", Category.OUTDOOR, "Running by the lake",
            time + 600_000, "Lake gate", "Suzhou", 5, time + 300_000)).getOrThrow()
        val second = bridge.prepare("carol", event.id, fix)
        assertEquals("Carol", bridge.activity(second).organizerName)
        rejects { bridge.backend.issue("carol", first, fix) }
        rejects { bridge.backend.attendance("carol", first) }
        repo.login("alice").getOrThrow()
        rejects { bridge.prepare("alice", event.id, fix) }
        rejects { bridge.backend.issue("alice", second, fix) }
        rejects { bridge.backend.attendance("alice", second) }
    }
    @Test fun completedStateRejectsStillUnexpiredCodeAndProfileDoesNotFallBackToMocks() = runTest {
        val session = prepare(); val code = raw(session)
        repo.login("bob").getOrThrow()
        var state = repo.state.value.copy(events = repo.state.value.events.map {
            if (it.id == "event-1") it.copy(status = EventStatus.COMPLETED) else it
        })
        val current = RegistrationAttendanceBridge({ state }, store, { time }, { 1000 })
        val legacy = LocalPrototypeBackend(store, { time }, { 1000 })
        val combined = CombinedCheckInBackend(store, current.backend, legacy)
        val registrations = CombinedCheckInRegistrations(store, current, MockCheckInRegistrations)
        assertTrue(combined.isCheckInClosed(session))
        rejects { combined.submit("bob", code, fix) }
        state = state.copy(events = state.events.filterNot { it.id == "event-1" })
        assertEquals(CheckInRegistration.NONE, registrations.status(session.id, "bob"))
        assertTrue(registrations.participants(session.id).isEmpty())
        assertTrue(combined.history("bob").isEmpty())
        rejects { combined.submit("bob", code, fix) }
    }
}

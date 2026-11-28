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

    @Test fun profilePhotoChangesReachRosterAndExistingReviews() = runTest {
        val s = prepare()
        repo.login("bob").getOrThrow()
        repo.updateProfile(ProfileUpdate("Bob", emptySet(), avatarUri = "file:///first.jpg")).getOrThrow()
        assertEquals("file:///first.jpg", bridge.participants(s.id).single { it.userId == "bob" }.avatarUri)
        time = s.endAt
        bridge.prepareFeedback("bob", "event-1")
        bridge.backend.submitFeedback("bob", s.id, 4, "Friendly group", FeedbackDetails())
        assertEquals("file:///first.jpg", bridge.backend.reviews("bob", s).single().avatarUri)
        repo.updateProfile(ProfileUpdate("Bob", emptySet(), avatarUri = "file:///second.jpg")).getOrThrow()
        assertEquals("file:///second.jpg", bridge.backend.reviews("bob", s).single().avatarUri)
        repo.login("alice").getOrThrow()
        assertEquals("file:///second.jpg", bridge.backend.attendance("alice", s).single { it.userId == "bob" }.avatarUri)
        bridge.prepareFeedback("alice", "event-1")
        assertEquals("file:///second.jpg", bridge.backend.reviews("alice", s).single().avatarUri)
    }

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
        val review = bridge.backend.submitFeedback("bob", s.id, 4, "I enjoyed the event.", FeedbackDetails(anonymous = true,
            visibility = FeedbackVisibility.ORGANIZER_ONLY))
        rejects { bridge.backend.submitFeedback("bob", s.id, 5, "I would join another event like this.", FeedbackDetails()) }
        assertEquals("I enjoyed the event.", review.comment)
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
        // Other completed events now have feedback records even without a prior GPS binding.
        assertTrue(combined.history("bob").none { it.session.id == session.id })
        rejects { combined.submit("bob", code, fix) }
    }

    @Test fun endedUnboundEventUsesFullFeedbackWithoutInventingGpsOrCheckIn() = runTest {
        repo.login("bob").getOrThrow()
        time = seed.event("event-1")!!.endAt
        val session = bridge.prepareFeedback("bob", "event-1")
        assertNull(session.meeting)
        assertNull(bridge.session(repo.state.value.event("event-1")!!))
        assertTrue(bridge.backend.isCheckInClosed(session))
        rejects { PrototypeCodeCodec.encode(PrototypeCode(session, time, time + 30_000)) }
        val inventedPoint = session.copy(meeting = fix.point)
        rejects { bridge.backend.submit("bob", PrototypeCodeCodec.encode(
            PrototypeCode(inventedPoint, session.startAt, session.startAt + 30_000)), fix) }
        val details = FeedbackDetails(5, 4, 3, listOf("welcoming"), true, FeedbackVisibility.ORGANIZER_ONLY)
        assertEquals(details, bridge.backend.submitFeedback("bob", session.id, 4, "A well-organized event with a welcoming group.", details).details)
        rejects { bridge.backend.submitFeedback("bob", session.id, 5, "I would happily take part again.", FeedbackDetails()) }
        assertTrue(records.receipts.isEmpty())
        val restored = RegistrationAttendanceBridge({ repo.state.value }, store, { time }, { 1000 })
        assertEquals(details, restored.backend.history("bob").single { it.session.id == session.id }.feedback!!.details)
    }

    @Test fun explicitCompletionAndCancellationUseSameFeedbackRuleWithAndWithoutGps() = runTest {
        val bound = prepare()
        var state = repo.state.value.copy(currentUserId = "bob", events = repo.state.value.events.map {
            if (it.id == "event-1") it.copy(status = EventStatus.COMPLETED) else it
        })
        val current = RegistrationAttendanceBridge({ state }, store, { time }, { 1000 })
        assertTrue(time < bound.endAt)
        val record = current.backend.history("bob").single { it.session.id == bound.id }
        assertTrue(record.hasEnded(time))
        assertTrue(record.eligible)
        current.backend.submitFeedback("bob", bound.id, 5, "The event finished early.", FeedbackDetails())
        state = state.copy(currentUserId = "carol", events = state.events.map {
            if (it.id == "event-1") it.copy(status = EventStatus.CANCELLED) else it
        })
        current.prepareFeedback("carol", "event-1")
        rejects { current.backend.submitFeedback("carol", bound.id, 5, "", FeedbackDetails()) }
    }

    @Test fun unboundFeedbackRejectsWaitlistNonMemberWrongIdentityAndOtherActivity() = runTest {
        time = seed.event("event-1")!!.endAt
        repo.login("emma").getOrThrow()
        val s = bridge.prepareFeedback("emma", "event-1")
        assertFalse(bridge.backend.history("emma").single { it.session.id == s.id }.eligible)
        rejects { bridge.backend.submitFeedback("emma", s.id, 5, "", FeedbackDetails()) }
        repo.login("finn").getOrThrow(); bridge.prepareFeedback("finn", "event-1")
        rejects { bridge.backend.submitFeedback("finn", s.id, 5, "", FeedbackDetails()) }
        rejects { bridge.backend.submitFeedback("bob", s.id, 5, "", FeedbackDetails()) }
        rejects { bridge.backend.submitFeedback("finn", java.util.UUID.randomUUID().toString(), 5, "", FeedbackDetails()) }
        assertTrue(records.receipts.isEmpty())
    }

    @Test fun reviewsResolveNewUsersAndRenamesFromLiveIdentityButKeepAnonymousPrivate() = runTest {
        time = seed.event("event-1")!!.endAt
        var state = seed.copy(currentUserId = "new-user",
            users = seed.users + seed.users.first().copy(id = "new-user", displayName = "Jordan Lee"),
            registrations = seed.registrations.map { if (it.eventId == "event-1" && it.userId == "bob") it.copy(userId = "new-user") else it })
        val current = RegistrationAttendanceBridge({ state }, store, { time }, { 1000 })
        val s = current.prepareFeedback("new-user", "event-1")
        current.backend.submitFeedback("new-user", s.id, 4, "Hello there", FeedbackDetails())
        state = state.copy(users = state.users.map { if (it.id == "new-user") it.copy(displayName = "Jordan Parker") else it })
        assertEquals("Jordan Parker", current.backend.reviews("new-user", s).single().authorName)
        state = state.copy(currentUserId = "carol")
        current.prepareFeedback("carol", "event-1")
        current.backend.submitFeedback("carol", s.id, 3, "Please make the meeting directions clearer.", FeedbackDetails(anonymous = true, visibility = FeedbackVisibility.ORGANIZER_ONLY))
        state = state.copy(currentUserId = "new-user")
        assertEquals(1, current.backend.reviews("new-user", s).size)
        state = state.copy(currentUserId = "alice")
        current.prepareFeedback("alice", "event-1")
        assertEquals("Anonymous participant", current.backend.reviews("alice", s).single { it.comment == "Please make the meeting directions clearer." }.authorName)
        state = state.copy(events = state.events.map { it.copy(organizerId = "carol") })
        rejects { current.backend.reviews("alice", s) }
        rejects { current.prepareFeedback("alice", "event-1") }
    }

    @Test fun oldFeedbackIsVisibleOnceAndCannotBeResubmittedThroughNewUnboundEntry() = runTest {
        repo.login("bob").getOrThrow(); time = seed.event("event-1")!!.endAt
        repo.submitFeedback("event-1", 3, "I enjoyed our last event.").getOrThrow()
        val s = bridge.prepareFeedback("bob", "event-1")
        assertEquals("I enjoyed our last event.", bridge.backend.history("bob").single { it.session.id == s.id }.feedback!!.comment)
        assertEquals("Bob", bridge.backend.reviews("bob", s).single().authorName)
        rejects { bridge.backend.submitFeedback("bob", s.id, 5, "", FeedbackDetails()) }
    }
}

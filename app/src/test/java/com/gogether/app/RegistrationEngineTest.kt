package com.gogether.app

import com.gogether.app.core.model.*
import com.gogether.app.feature.registration.RegistrationEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RegistrationEngineTest {
    private var time = 1_800_000_000_000L
    private lateinit var engine: RegistrationEngine
    private lateinit var draft: EventDraft
    @Before fun setup() {
        time = 1_800_000_000_000L
        val users = (1..6).map { User("u$it", "User $it", setOf(Category.SPORTS), time) }
        engine = RegistrationEngine(AppSnapshot(users = users), now = { time })
        draft = EventDraft("Badminton together", Category.SPORTS, "Friendly doubles for all levels",
            time + 86_400_000, "Sports centre, Court 3", "Suzhou", 2, time + 3_600_000)
    }
    private fun event(capacity: Int = 2) = engine.createEvent("u1", draft.copy(capacity = capacity))
    private fun failure(message: String? = null, action: () -> Unit) {
        val before = engine.snapshot
        val error = assertThrows(IllegalArgumentException::class.java, action)
        if (message != null) assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(message))
        assertEquals("Rejected operations must not change state", before, engine.snapshot)
    }
    @Test fun creationReservesOneOrganizerPlace() {
        val event = event()
        assertEquals(event, engine.snapshot.event(event.id))
        assertEquals(1, engine.snapshot.confirmed(event.id).size)
        assertEquals("u1", engine.snapshot.confirmed(event.id).single().userId)
        assertEquals(time, event.createdAt)
        assertEquals(event.startAt + RegistrationEngine.TWO_HOURS, event.endAt)
    }
    @Test fun creationPersistsScheduleAndParticipationInformation() {
        val custom = draft.copy(
            endAt = draft.startAt + 3 * 60 * 60 * 1000L,
            experienceRequirement = ExperienceRequirement.BASIC,
            beginnerGuidance = true,
            requiredItems = "  Shoes and water  ",
            spareEquipment = "  Two spare rackets  ",
            imageUri = "  content://event/photo  ",
        )
        val event = engine.createEvent("u1", custom)
        assertEquals(custom.endAt, event.endAt)
        assertEquals(ExperienceRequirement.BASIC, event.experienceRequirement)
        assertTrue(event.beginnerGuidance)
        assertEquals("Shoes and water", event.requiredItems)
        assertEquals("Two spare rackets", event.spareEquipment)
        assertEquals("content://event/photo", event.imageUri)
    }
    @Test fun invalidCreationFieldsAreRejectedAtomically() {
        val invalid = listOf(draft.copy(title = "  "), draft.copy(title = "x".repeat(81)),
            draft.copy(description = "short"), draft.copy(description = "x".repeat(2001)),
            draft.copy(location = " "), draft.copy(city = ""), draft.copy(capacity = 1),
            draft.copy(capacity = 51), draft.copy(startAt = time),
            draft.copy(endAt = draft.startAt),
            draft.copy(endAt = draft.startAt + 24 * 60 * 60 * 1000L + 1),
            draft.copy(registrationDeadline = time), draft.copy(registrationDeadline = draft.startAt + 1),
            draft.copy(notes = "x".repeat(1001)), draft.copy(requiredItems = "x".repeat(501)),
            draft.copy(spareEquipment = "x".repeat(501)), draft.copy(imageUri = "x".repeat(2001)))
        invalid.forEach { invalidDraft -> failure { engine.createEvent("u1", invalidDraft) } }
    }
    @Test fun creationAcceptsDeadlineAtStart() {
        engine.createEvent("u1", draft.copy(registrationDeadline = draft.startAt))
        assertEquals(1, engine.snapshot.events.size)
    }
    @Test fun unknownOrganizerCannotCreate() { failure { engine.createEvent("missing", draft) } }
    @Test fun joinsAvailablePlace() {
        val event = event()
        assertEquals(RegistrationStatus.CONFIRMED, engine.join("u2", event.id).status)
        assertEquals(2, engine.snapshot.confirmed(event.id).size)
    }
    @Test fun fullEventCreatesOrderedWaitlist() {
        val event = event()
        engine.join("u2", event.id)
        assertEquals(RegistrationStatus.WAITLISTED, engine.join("u3", event.id).status)
        engine.join("u4", event.id)
        assertEquals(listOf("u3", "u4"), engine.snapshot.waitlist(event.id).map { it.userId })
        assertEquals(2, engine.snapshot.confirmed(event.id).size)
    }
    @Test fun cancellingConfirmedRegistrationReleasesPlace() {
        val event = event()
        engine.join("u2", event.id)
        engine.cancelRegistration("u2", event.id)
        assertEquals(RegistrationStatus.CANCELLED, engine.snapshot.registration(event.id, "u2")?.status)
        assertEquals(1, engine.snapshot.confirmed(event.id).size)
    }
    @Test fun cancellationPromotesOnlyFirstAndNotifiesThem() {
        val event = event()
        engine.join("u2", event.id); engine.join("u3", event.id); engine.join("u4", event.id)
        engine.cancelRegistration("u2", event.id)
        assertEquals(RegistrationStatus.CONFIRMED, engine.snapshot.registration(event.id, "u3")?.status)
        assertEquals(listOf("u4"), engine.snapshot.waitlist(event.id).map { it.userId })
        assertEquals(2, engine.snapshot.confirmed(event.id).size)
        assertEquals("u3", engine.snapshot.notifications.single().userId)
        assertEquals(NotificationType.PROMOTED, engine.snapshot.notifications.single().type)
    }
    @Test fun waitlistedCancellationDoesNotPromoteOrFreePlace() {
        val event = event()
        engine.join("u2", event.id); engine.join("u3", event.id); engine.join("u4", event.id)
        engine.cancelRegistration("u3", event.id)
        assertEquals(2, engine.snapshot.confirmed(event.id).size)
        assertEquals(listOf("u4"), engine.snapshot.waitlist(event.id).map { it.userId })
        assertTrue(engine.snapshot.notifications.isEmpty())
    }
    @Test fun duplicatesRejectedForOrganizerConfirmedAndWaitlisted() {
        val event = event()
        engine.join("u2", event.id); engine.join("u3", event.id)
        listOf("u1", "u2", "u3").forEach { user -> failure("already") { engine.join(user, event.id) } }
    }
    @Test fun rejoiningGoesToBackOfQueueEvenWithSameTimestamp() {
        val event = event()
        engine.join("u2", event.id); engine.join("u3", event.id); engine.join("u4", event.id)
        engine.cancelRegistration("u3", event.id); engine.join("u3", event.id)
        assertEquals(listOf("u4", "u3"), engine.snapshot.waitlist(event.id).map { it.userId })
        assertEquals(1, engine.snapshot.registrations.count { it.eventId == event.id && it.userId == "u3" })
    }
    @Test fun cancelledEventRejectsJoinsAndCancelsAllPlacesWithoutPromotion() {
        val event = event()
        engine.join("u2", event.id); engine.join("u3", event.id)
        engine.cancelEvent("u1", event.id)
        failure { engine.join("u4", event.id) }
        assertEquals(EventStatus.CANCELLED, engine.snapshot.event(event.id)?.status)
        assertTrue(engine.snapshot.registrations.all { it.status == RegistrationStatus.CANCELLED })
        assertTrue(engine.snapshot.notifications.all { it.type == NotificationType.EVENT_CANCELLED })
        failure { engine.cancelRegistration("u2", event.id) }
    }
    @Test fun deadlineBoundaryRejectsJoining() {
        val event = event(); time = event.registrationDeadline
        failure("closed") { engine.join("u2", event.id) }
    }
    @Test fun oneMillisecondBeforeDeadlineAllowsJoining() {
        val event = event(); time = event.registrationDeadline - 1
        assertEquals(RegistrationStatus.CONFIRMED, engine.join("u2", event.id).status)
    }
    @Test fun cancellationAtDeadlineDoesNotPromote() {
        val event = event(); engine.join("u2", event.id); engine.join("u3", event.id)
        time = event.registrationDeadline
        engine.cancelRegistration("u2", event.id)
        assertEquals(1, engine.snapshot.confirmed(event.id).size)
        assertEquals(RegistrationStatus.WAITLISTED, engine.snapshot.registration(event.id, "u3")?.status)
        assertTrue(engine.snapshot.notifications.isEmpty())
    }
    @Test fun startedAndEndedEventsRejectNewRegistration() {
        val event = event()
        time = event.startAt; failure { engine.join("u2", event.id) }
        time = event.endAt; failure { engine.join("u2", event.id) }
    }
    @Test fun completedEventRejectsRegistration() {
        val event = event()
        engine = RegistrationEngine(engine.snapshot.copy(events = listOf(event.copy(status = EventStatus.COMPLETED))), { time })
        failure { engine.join("u2", event.id) }
    }
    @Test fun organizerCannotCancelOwnReservedPlace() {
        val event = event(); failure("organizer") { engine.cancelRegistration("u1", event.id) }
    }
    @Test fun cancellationAfterStartIsRejected() {
        val event = event(); engine.join("u2", event.id); time = event.startAt
        failure { engine.cancelRegistration("u2", event.id) }
        failure { engine.cancelEvent("u1", event.id) }
    }
    @Test fun onlyOrganizerCanManageEvent() {
        val event = event()
        failure { engine.updateEvent("u2", event.id, draft.copy(title = "Changed title")) }
        failure { engine.cancelEvent("u2", event.id) }
        failure { engine.setDemoCheckInOpen("u2", event.id, true) }
        failure { engine.postAnnouncement("u2", event.id, "Meet at the gate") }
    }
    @Test fun organizerCanEditUpcomingEventAndCannotShrinkBelowConfirmedCount() {
        val event = event(capacity = 3)
        engine.join("u2", event.id)
        val changed = draft.copy(title = "Updated friendly match", capacity = 3,
            experienceRequirement = ExperienceRequirement.NONE, beginnerGuidance = true)
        val updated = engine.updateEvent("u1", event.id, changed)
        assertEquals("Updated friendly match", updated.title)
        assertEquals(3, updated.capacity)
        assertTrue(updated.beginnerGuidance)
        assertEquals(event.createdAt, updated.createdAt)
        engine.join("u3", event.id)
        failure("confirmed") { engine.updateEvent("u1", event.id, changed.copy(capacity = 2)) }
    }
    @Test fun increasingCapacityPromotesWaitlistInOrder() {
        val event = event(capacity = 2)
        engine.join("u2", event.id)
        engine.join("u3", event.id)
        engine.join("u4", event.id)
        engine.updateEvent("u1", event.id, draft.copy(capacity = 3))
        assertEquals(RegistrationStatus.CONFIRMED, engine.snapshot.registration(event.id, "u3")?.status)
        assertEquals(listOf("u4"), engine.snapshot.waitlist(event.id).map { it.userId })
        assertEquals("u3", engine.snapshot.notifications.single().userId)
    }
    @Test fun startedOrCancelledEventCannotBeEdited() {
        val event = event()
        time = event.startAt
        failure { engine.updateEvent("u1", event.id, draft.copy(startAt = time + 100_000, endAt = time + 200_000)) }
        time = event.createdAt
        engine.cancelEvent("u1", event.id)
        failure { engine.updateEvent("u1", event.id, draft) }
    }
    @Test fun checkInRequiresOrganizerOpeningAndConfirmedPlaceAndRejectsDuplicate() {
        val event = event(); engine.join("u2", event.id); engine.join("u3", event.id)
        failure { engine.checkIn("u2", event.id) }
        engine.setDemoCheckInOpen("u1", event.id, true)
        failure { engine.checkIn("u3", event.id) }
        engine.checkIn("u2", event.id)
        failure("already") { engine.checkIn("u2", event.id) }
        assertEquals(CheckInStatus.DEMO_RECORDED, engine.snapshot.checkIns.single().status)
    }
    @Test fun cancelledAndEndedEventsRejectCheckIn() {
        val event = event(); engine.setDemoCheckInOpen("u1", event.id, true)
        time = event.endAt; failure { engine.checkIn("u1", event.id) }
        time = event.createdAt; engine.cancelEvent("u1", event.id)
        failure { engine.checkIn("u1", event.id) }
    }
    @Test fun feedbackRequiresPastParticipationValidRatingAndNoDuplicate() {
        val event = event(); engine.join("u2", event.id)
        failure { engine.submitFeedback("u2", event.id, 5, "Great") }
        time = event.endAt
        failure { engine.submitFeedback("u3", event.id, 5, "") }
        failure { engine.submitFeedback("u2", event.id, 6, "") }
        failure { engine.submitFeedback("u2", event.id, 5, "x".repeat(501)) }
        engine.submitFeedback("u2", event.id, 4, "Enjoyed it")
        failure { engine.submitFeedback("u2", event.id, 5, "Again") }
    }
    @Test fun announcementsTargetActiveParticipantsAndReadIsUserScoped() {
        val event = event(); engine.join("u2", event.id); engine.join("u3", event.id)
        engine.postAnnouncement("u1", event.id, "Meet at the gate")
        assertEquals(setOf("u1", "u2", "u3"), engine.snapshot.notifications.map { it.userId }.toSet())
        val notification = engine.snapshot.notifications.first { it.userId == "u2" }
        failure { engine.markRead("u3", notification.id) }
        engine.markRead("u2", notification.id)
        assertTrue(engine.snapshot.notifications.first { it.id == notification.id }.isRead)
    }
    @Test fun demoProfilesValidateNicknameAndPreserveEventsOnSwitch() {
        val event = event()
        failure { engine.registerUser(" ", emptySet()) }
        failure { engine.registerUser("USER 1", emptySet()) }
        val user = engine.registerUser("New friend", setOf(Category.READING))
        assertEquals(user.id, engine.snapshot.currentUserId)
        engine.login("u2"); assertEquals(event, engine.snapshot.event(event.id))
        engine.logout(); assertNull(engine.snapshot.currentUserId)
    }
}

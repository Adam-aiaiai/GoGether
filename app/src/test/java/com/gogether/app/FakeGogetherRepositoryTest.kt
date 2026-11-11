package com.gogether.app

import com.gogether.app.core.data.EventValidator
import com.gogether.app.core.data.FakeGogetherRepository
import com.gogether.app.core.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeGogetherRepositoryTest {
    private val base = Instant.parse("2027-01-01T00:00:00Z")
    private lateinit var clock: MutableClock
    private lateinit var repository: FakeGogetherRepository

    @Before fun setUp() { clock = MutableClock(base); repository = FakeGogetherRepository(clock); repository.login("u-alex") }

    private fun draft(capacity: Int = 2) = EventDraft("Test meetup", "Testing", "A real description", base.plusSeconds(7200), "Lab 1", capacity, base.plusSeconds(3600), "Bring a friend")
    private fun create(capacity: Int = 2) = repository.createEvent(draft(capacity)).getOrThrow()

    @Test fun createEvent_isStored_andCreatorOccupiesOnePlace() {
        val event = create()
        assertTrue(repository.events.value.any { it.id == event.id })
        assertEquals(1, repository.registrations.value.count { it.eventId == event.id && it.status == RegistrationStatus.CONFIRMED })
    }

    @Test fun eventValidation_rejectsRequiredAndInvalidFields() {
        val errors = EventValidator.validate(draft().copy(title = "", maxParticipants = 1, registrationDeadline = base.plusSeconds(9000)), clock)
        assertTrue(errors.keys.containsAll(listOf("title", "capacity", "deadline")))
    }

    @Test fun joinWithSpace_isConfirmed() {
        val event = create(2); repository.login("u-mia")
        assertTrue(repository.joinEvent(event.id) is RegistrationOutcome.Joined)
    }

    @Test fun fullEvent_joinsWaitlist() {
        val event = create(2); repository.login("u-mia"); repository.joinEvent(event.id); repository.login("u-noah")
        assertTrue(repository.joinEvent(event.id) is RegistrationOutcome.Waitlisted)
    }

    @Test fun cancelConfirmed_promotesFirstWaitlisted() {
        val event = create(2)
        repository.login("u-mia"); repository.joinEvent(event.id)
        repository.login("u-noah"); repository.joinEvent(event.id)
        repository.login("u-mia"); assertTrue(repository.cancelRegistration(event.id).isSuccess)
        assertEquals(RegistrationStatus.CONFIRMED, repository.registrations.value.last { it.eventId == event.id && it.userId == "u-noah" }.status)
    }

    @Test fun cancelWaitlisted_doesNotAffectConfirmedCount() {
        val event = create(2); repository.login("u-mia"); repository.joinEvent(event.id); repository.login("u-noah"); repository.joinEvent(event.id)
        assertTrue(repository.cancelRegistration(event.id).isSuccess)
        assertEquals(2, repository.registrations.value.count { it.eventId == event.id && it.status == RegistrationStatus.CONFIRMED })
    }

    @Test fun duplicateRegistration_isRejected() {
        val event = create(3); repository.login("u-mia"); repository.joinEvent(event.id)
        assertTrue(repository.joinEvent(event.id) is RegistrationOutcome.Rejected)
    }

    @Test fun cancelledEvent_rejectsRegistration() {
        val event = create(); repository.cancelEvent(event.id); repository.login("u-mia")
        assertTrue(repository.joinEvent(event.id) is RegistrationOutcome.Rejected)
    }

    @Test fun registrationAfterDeadline_isRejected() {
        val event = create(); clock.instantValue = base.plusSeconds(3601); repository.login("u-mia")
        assertTrue(repository.joinEvent(event.id) is RegistrationOutcome.Rejected)
    }

    @Test fun noPromotionAfterDeadline() {
        val event = create(2); repository.login("u-mia"); repository.joinEvent(event.id); repository.login("u-noah"); repository.joinEvent(event.id)
        clock.instantValue = base.plusSeconds(3601); repository.login("u-mia"); repository.cancelRegistration(event.id)
        assertEquals(RegistrationStatus.WAITLISTED, repository.registrations.value.last { it.eventId == event.id && it.userId == "u-noah" }.status)
    }
}

private class MutableClock(var instantValue: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = instantValue
}

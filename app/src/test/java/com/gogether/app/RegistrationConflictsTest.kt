package com.gogether.app

import com.gogether.app.core.data.DemoData
import com.gogether.app.core.model.*
import com.gogether.app.feature.registration.registrationConflicts
import org.junit.Assert.*
import org.junit.Test

class RegistrationConflictsTest {
    private val now = 1_800_000_000_000L
    private val seed = DemoData.create(now).copy(currentUserId = "bob")
    private val booked = seed.events.first()
    private val target = booked.copy(id = "target", startAt = booked.startAt + 1, endAt = booked.endAt + 1)
    private fun conflicts(event: Event = target, state: AppSnapshot = seed) =
        state.registrationConflicts(event, now)

    @Test fun detectsPartialIdenticalAndContainedIntervals() {
        listOf(target, target.copy(startAt = booked.startAt, endAt = booked.endAt),
            target.copy(startAt = booked.startAt - 1, endAt = booked.endAt + 1),
            target.copy(startAt = booked.startAt + 1, endAt = booked.endAt - 1),
            target.copy(startAt = booked.startAt - 1, endAt = booked.startAt + 1))
            .forEach { assertEquals(listOf(booked), conflicts(it)) }
    }

    @Test fun adjacentAndSeparateEventsDoNotConflict() {
        assertTrue(conflicts(target.copy(startAt = booked.endAt)).isEmpty())
        assertTrue(conflicts(target.copy(startAt = booked.startAt - 100, endAt = booked.startAt)).isEmpty())
        assertTrue(conflicts(target.copy(startAt = booked.endAt + 100, endAt = booked.endAt + 200)).isEmpty())
    }

    @Test fun excludesSelfOtherUsersAndSignedOutUser() {
        assertTrue(conflicts(booked).isEmpty())
        assertTrue(conflicts(state = seed.copy(currentUserId = "finn")).isEmpty())
        assertTrue(conflicts(state = seed.copy(currentUserId = null)).isEmpty())
    }

    @Test fun includesWaitlistAndHosting() {
        assertEquals(listOf(booked), conflicts(state = seed.copy(currentUserId = "emma")))
        assertEquals(listOf(booked), conflicts(state = seed.copy(currentUserId = "alice", registrations = emptyList())))
    }

    @Test fun ignoresInactiveRegistrationsAndEvents() {
        listOf(RegistrationStatus.CANCELLED, RegistrationStatus.REJECTED).forEach { status ->
            assertTrue(conflicts(state = seed.copy(registrations = seed.registrations.map {
                if (it.userId == "bob") it.copy(status = status) else it
            })).isEmpty())
        }
        listOf(EventStatus.CANCELLED, EventStatus.COMPLETED).forEach { status ->
            assertTrue(conflicts(state = seed.copy(events = listOf(booked.copy(status = status)))).isEmpty())
        }
        assertTrue(seed.registrationConflicts(target, booked.endAt).isEmpty())
    }

    @Test fun includesOngoingEventAndSortsMultipleConflictsWithoutMutation() {
        val second = booked.copy(id = "second", startAt = booked.startAt - 10)
        val state = seed.copy(events = seed.events + second, registrations = seed.registrations +
            seed.registrations.first { it.userId == "bob" }.copy(id = "second-reg", eventId = second.id))
        assertEquals(listOf(second, booked), state.registrationConflicts(target, booked.startAt + 1))
        assertEquals(RegistrationStatus.CONFIRMED, state.registration(booked.id)?.status)
    }
}

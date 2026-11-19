package com.gogether.app

import com.gogether.app.core.data.DemoData
import com.gogether.app.core.data.FakeGogetherRepository
import com.gogether.app.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Submission tests use the repository, so bypassing a disabled UI cannot bypass eligibility. */
class CheckInRepositoryTest {
    private val time = 1_800_000_000_000L
    private fun fixture(): AppSnapshot {
        val seed = DemoData.create(time)
        return seed.copy(currentUserId = "bob", events = seed.events.map {
            if (it.id == "event-1") it.copy(startAt = time, endAt = time + 7_200_000) else it
        })
    }

    @Test fun confirmedParticipantCanCheckInAtEventStart() = runTest {
        val repo = FakeGogetherRepository(fixture(), now = { time })
        val record = repo.checkIn("event-1").getOrThrow()
        assertEquals("bob", record.userId)
        assertEquals("event-1", record.eventId)
        assertEquals(time, record.checkedInAt)
        assertEquals(listOf(record), repo.state.value.checkIns)
    }

    @Test fun allNonConfirmedStatusesRejectWithoutChangingState() = runTest {
        for (status in RegistrationStatus.entries.filter { it != RegistrationStatus.CONFIRMED }) {
            val seed = fixture()
            val initial = seed.copy(registrations = seed.registrations.map {
                if (it.eventId == "event-1" && it.userId == "bob") it.copy(status = status) else it
            })
            val repo = FakeGogetherRepository(initial, now = { time })
            assertTrue("$status cannot check in", repo.checkIn("event-1").isFailure)
            assertEquals(initial, repo.state.value)
        }
    }

    @Test fun unregisteredAndUnknownAndLoggedOutUsersCannotCheckIn() = runTest {
        for (user in listOf("finn", "missing", null)) {
            val initial = fixture().copy(currentUserId = user)
            val repo = FakeGogetherRepository(initial, now = { time })
            assertTrue(repo.checkIn("event-1").isFailure)
            assertEquals(initial, repo.state.value)
        }
    }

    @Test fun anotherEventsRegistrationDoesNotGrantEligibility() = runTest {
        val seed = fixture()
        val initial = seed.copy(registrations = seed.registrations.filterNot {
            it.userId == "bob" && it.eventId == "event-1"
        })
        assertNotNull(initial.registration("event-history", "bob"))
        val repo = FakeGogetherRepository(initial, now = { time })
        assertTrue(repo.checkIn("event-1").isFailure)
        assertEquals(initial, repo.state.value)
    }

    @Test fun repeatedConcurrentSubmissionsCreateExactlyOneRecordInThisProcess() = runTest {
        val repo = FakeGogetherRepository(fixture(), now = { time })
        val results = coroutineScope {
            (1..20).map { async(Dispatchers.Default) { repo.checkIn("event-1") } }.awaitAll()
        }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, repo.state.value.checkIns.size)
    }

    @Test fun switchingAccountsDoesNotReuseAnotherUsersEligibilityOrRecord() = runTest {
        val repo = FakeGogetherRepository(fixture(), now = { time })
        repo.checkIn("event-1").getOrThrow()
        repo.login("emma").getOrThrow()
        assertTrue(repo.checkIn("event-1").isFailure)
        repo.login("carol").getOrThrow()
        repo.checkIn("event-1").getOrThrow()
        assertEquals(setOf("bob", "carol"), repo.state.value.checkIns.map { it.userId }.toSet())
        repo.logout().getOrThrow()
        val before = repo.state.value
        assertTrue(repo.checkIn("event-1").isFailure)
        assertEquals(before, repo.state.value)
    }

    @Test fun organizerCancellingEventIsRecheckedAtSubmission() = runTest {
        val repo = FakeGogetherRepository(fixture(), now = { time - 600_000 })
        repo.checkIn("event-1").getOrThrow()
        repo.login("alice").getOrThrow()
        repo.cancelEvent("event-1").getOrThrow()
        repo.login("carol").getOrThrow()
        val before = repo.state.value
        assertTrue(repo.checkIn("event-1").isFailure)
        assertEquals(before, repo.state.value)
        assertEquals("bob", repo.state.value.checkIns.single().userId)
    }

    @Test fun missingEventCannotCreateRecord() = runTest {
        val initial = fixture()
        val repo = FakeGogetherRepository(initial, now = { time })
        assertTrue(repo.checkIn("missing").isFailure)
        assertEquals(initial, repo.state.value)
    }

    @Test fun submissionEnforcesInclusiveWindowEvenWithoutTheScreen() = runTest {
        for ((offset, allowed) in listOf(-1_800_001L to false, -1_800_000L to true,
                0L to true, 1_800_000L to true, 1_800_001L to false)) {
            val initial = fixture()
            val repo = FakeGogetherRepository(initial, now = { time + offset })
            assertEquals("Offset $offset", allowed, repo.checkIn("event-1").isSuccess)
            if (allowed) assertEquals(time + offset, repo.state.value.checkIns.single().checkedInAt)
            else assertEquals(initial, repo.state.value)
        }
    }

    @Test fun clockIsRecheckedWhenSubmittingAfterWindowExpires() = runTest {
        var current = time
        val initial = fixture()
        val repo = FakeGogetherRepository(initial, now = { current })
        current = time + 1_800_001
        assertTrue(repo.checkIn("event-1").isFailure)
        assertEquals(initial, repo.state.value)
    }

    @Test fun cancellationAndCompletionAndEndTimeRejectSubmission() = runTest {
        val seed = fixture()
        val event = seed.event("event-1")!!
        val closedEvents = listOf(event.copy(status = EventStatus.CANCELLED),
            event.copy(status = EventStatus.COMPLETED), event.copy(endAt = time))
        for (closed in closedEvents) {
            val initial = seed.copy(events = seed.events.map { if (it.id == closed.id) closed else it })
            val repo = FakeGogetherRepository(initial, now = { time })
            assertTrue(repo.checkIn(closed.id).isFailure)
            assertEquals(initial, repo.state.value)
        }
    }

    @Test fun cancellationAndPromotionUseLatestRegistrationAtSubmission() = runTest {
        val seed = fixture()
        val initial = seed.copy(events = seed.events.map {
            if (it.id == "event-1") it.copy(startAt = time + 600_000,
                registrationDeadline = time + 300_000) else it
        })
        val repo = FakeGogetherRepository(initial, now = { time })
        repo.cancelRegistration("event-1").getOrThrow()
        assertTrue(repo.checkIn("event-1").isFailure)
        assertEquals(RegistrationStatus.CONFIRMED, repo.state.value.registration("event-1", "emma")?.status)
        repo.login("emma").getOrThrow()
        assertEquals("emma", repo.checkIn("event-1").getOrThrow().userId)
    }

    @Test fun validationAndTimestampUseOneClockReadingAtTheEndpoint() = runTest {
        var calls = 0
        val repo = FakeGogetherRepository(fixture(), now = { time + 1_800_000 + calls++ })
        assertEquals(time + 1_800_000, repo.checkIn("event-1").getOrThrow().checkedInAt)
        assertEquals(1, calls)
    }
}

package com.gogether.app

import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.event.parseEventDateTime
import com.gogether.app.feature.event.reuseDraft
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryTest {
    @Test fun sharedStatePublishesCreationAndSessionSwitches() = runTest {
        val repo = FakeGogetherRepository()
        repo.login("alice").getOrThrow()
        val time = System.currentTimeMillis()
        val created = repo.createEvent(EventDraft("New test event", Category.OUTDOOR, "A walk around the lake",
            time + 100_000, "The east entrance", "Suzhou", 4, time + 90_000)).getOrThrow()
        assertEquals(created, repo.state.value.event(created.id))
        repo.login("bob").getOrThrow()
        repo.join(created.id).getOrThrow()
        assertEquals(2, repo.state.value.confirmed(created.id).size)
        repo.login("alice").getOrThrow()
        assertEquals(2, repo.state.value.confirmed(created.id).size)
    }
    @Test fun sharedStatePublishesOrganizerEditsAndParticipationInformation() = runTest {
        val repo = FakeGogetherRepository()
        repo.login("alice").getOrThrow()
        val event = repo.state.value.event("event-1")!!
        val updated = repo.updateEvent(event.id, EventDraft(
            title = "Beginner badminton workshop", category = event.category,
            description = event.description, startAt = event.startAt, endAt = event.endAt,
            location = event.location, city = event.city, capacity = event.capacity,
            registrationDeadline = event.registrationDeadline, notes = event.notes,
            experienceRequirement = ExperienceRequirement.NONE, beginnerGuidance = true,
            requiredItems = "Sports shoes and water", spareEquipment = "Two spare rackets",
        )).getOrThrow()
        assertEquals(updated, repo.state.value.event(event.id))
        assertEquals("Sports shoes and water", repo.state.value.event(event.id)?.requiredItems)
    }
    @Test fun promotionAndNotificationArePublishedAsOneSnapshot() = runTest {
        val repo = FakeGogetherRepository()
        repo.login("bob").getOrThrow()
        val seen = mutableListOf<AppSnapshot>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.state.toList(seen) }
        repo.cancelRegistration("event-1").getOrThrow()
        assertEquals(2, seen.size)
        val after = seen.last()
        assertEquals(RegistrationStatus.CANCELLED, after.registration("event-1", "bob")?.status)
        assertEquals(RegistrationStatus.CONFIRMED, after.registration("event-1", "emma")?.status)
        assertEquals("emma", after.notifications.single().userId)
        assertEquals(4, after.confirmed("event-1").size)
    }
    @Test fun repeatedConcurrentTapsOnlyCreateOneRegistrationInThisProcess() = runTest {
        val repo = FakeGogetherRepository()
        repo.login("finn").getOrThrow()
        val results = coroutineScope { (1..30).map { async(Dispatchers.Default) { repo.join("event-1") } }.awaitAll() }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, repo.state.value.registrations.count { it.eventId == "event-1" && it.userId == "finn" })
        assertEquals(4, repo.state.value.confirmed("event-1").size)
    }
    @Test fun mutationsRequireDemoSession() = runTest {
        val repo = FakeGogetherRepository()
        assertTrue(repo.join("event-1").isFailure)
        assertTrue(repo.cancelEvent("event-1").isFailure)
        assertTrue(repo.checkIn("event-1").isFailure)
    }
    @Test fun invalidDatesAndAmbiguousDstTimesAreRejected() {
        val zone = ZoneId.of("UTC")
        assertThrows(java.time.DateTimeException::class.java) { parseEventDateTime("2027-02-30", "12:00", zone) }
        assertThrows(java.time.DateTimeException::class.java) { parseEventDateTime("2027-02-28", "24:30", zone) }
        assertThrows(IllegalArgumentException::class.java) { parseEventDateTime("2027-03-14", "02:30", ZoneId.of("America/New_York")) }
        assertTrue(parseEventDateTime("2027-02-28", "12:00", zone) > 0)
    }
    @Test fun seedDatesAreRelativeToInjectedClock() {
        val time = 1_800_000_000_000L
        val data = DemoData.create(time)
        assertEquals(6, data.users.size)
        assertEquals(5, data.events.count { it.acceptsRegistration(time) })
        assertEquals(1, data.events.count { it.status == EventStatus.COMPLETED })
        assertEquals(listOf("emma"), data.waitlist("event-1").map { it.userId })
    }
    @Test fun reuseDraftCopiesConfigurationButMovesScheduleToTheFuture() {
        val time = 1_800_000_000_000L
        val event = DemoData.create(time).event("event-history")!!.copy(imageUri = "content://event/photo")
        val draft = event.reuseDraft(time, ZoneId.of("UTC"))
        assertEquals(event.title, draft.title)
        assertEquals(event.category, draft.category)
        assertEquals(event.location, draft.location)
        assertEquals(event.capacity, draft.capacity)
        assertEquals(event.requiredItems, draft.requiredItems)
        assertEquals("content://event/photo", draft.imageUri)
        assertTrue(draft.startAt > time)
        assertEquals(event.endAt - event.startAt, draft.endAt - draft.startAt)
        assertEquals(draft.startAt - 60 * 60 * 1000L, draft.registrationDeadline)
    }
}

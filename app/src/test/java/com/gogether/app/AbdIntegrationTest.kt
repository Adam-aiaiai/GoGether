package com.gogether.app

import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.event.reuseDraft
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AbdIntegrationTest {
    private val now = 1_800_000_000_000L
    private fun Event.draft() = EventDraft(title, category, description, startAt, location, city, capacity,
        registrationDeadline, notes, endAt, experienceRequirement, beginnerGuidance, requiredItems, spareEquipment, imageUri)

    @Test fun accountEditAndEventEditPreserveMeetingAndAnnounceExactlyOncePerRecipient() = runTest {
        val repo = FakeGogetherRepository(DemoData.create(now), { now })
        repo.login("alice").getOrThrow()
        val original = repo.state.value.event("event-1")!!
        repo.updateMeeting(original.id, MeetingUpdate(original.startAt - 60_000, "East entrance", "Bring water")).getOrThrow()
        repo.postAnnouncement(original.id, "Meet near the red sign").getOrThrow()
        repo.updateProfile("Alice Updated", setOf(Category.STUDY)).getOrThrow()
        val edited = repo.updateEvent(original.id, original.draft().copy(title = "Updated badminton", capacity = 5,
            imageUri = "content://example/cover", spareEquipment = "Three spare rackets")).getOrThrow()
        assertEquals("alice", edited.organizerId)
        assertEquals("Alice Updated", repo.state.value.userName(edited.organizerId))
        assertEquals("East entrance", edited.meetingLocation)
        assertEquals(original.startAt - 60_000, edited.meetingAt)
        assertEquals("content://example/cover", edited.imageUri)
        assertEquals("Three spare rackets", edited.spareEquipment)
        assertEquals(original.experienceRequirement, edited.experienceRequirement)
        assertEquals(RegistrationStatus.CONFIRMED, repo.state.value.registration(original.id, "emma")?.status)
        val announcement = repo.state.value.announcements.single()
        val messages = repo.state.value.notifications.filter { it.type == NotificationType.ANNOUNCEMENT }
        assertEquals(setOf("alice", "bob", "carol", "david", "emma"), messages.map { it.userId }.toSet())
        assertEquals(5, messages.size)
        assertTrue(messages.all { it.announcementId == announcement.id })
    }

    @Test fun shorteningEventMustAdjustMeetingAndSavesBothTogether() = runTest {
        val repo = FakeGogetherRepository(DemoData.create(now), { now })
        repo.login("alice").getOrThrow()
        val event = repo.state.value.event("event-1")!!
        repo.updateMeeting(event.id, MeetingUpdate(event.endAt - 1_000, "East entrance", "Keep notes")).getOrThrow()
        val before = repo.state.value
        val shorter = event.draft().copy(endAt = event.startAt + 60_000)
        assertTrue(repo.updateEvent(event.id, shorter).isFailure)
        assertEquals(before, repo.state.value)
        val edited = repo.updateEvent(event.id, shorter.copy(meetingAt = event.startAt)).getOrThrow()
        assertEquals(event.startAt, edited.meetingAt)
        assertEquals(shorter.endAt, edited.endAt)
        assertEquals("Keep notes", edited.meetingNotes)
        assertEquals(10, repo.state.value.notifications.count { it.type == NotificationType.MEETING_UPDATED })
    }

    @Test fun reuseKeepsConfigurationButStartsWithNewMeetingTimeAndNoOldRecords() = runTest {
        val repo = FakeGogetherRepository(DemoData.create(now), { now })
        repo.login("alice").getOrThrow()
        val event = repo.state.value.event("event-1")!!
        repo.updateMeeting(event.id, MeetingUpdate(event.startAt - 60_000, "East entrance", "Look for the red sign")).getOrThrow()
        repo.postAnnouncement(event.id, "Original event only").getOrThrow()
        val source = repo.state.value.event(event.id)!!
        val copy = repo.createEvent(source.reuseDraft(now).copy(title = "New badminton")).getOrThrow()
        assertNotEquals(source.id, copy.id)
        assertNull(copy.meetingAt)
        assertEquals(source.meetingLocation, copy.meetingLocation)
        assertEquals(source.meetingNotes, copy.meetingNotes)
        assertEquals(source.requiredItems, copy.requiredItems)
        assertTrue(repo.state.value.announcements.none { it.eventId == copy.id })
        assertTrue(repo.state.value.notifications.none { it.eventId == copy.id })
        assertEquals(listOf("alice"), repo.state.value.confirmed(copy.id).map { it.userId })
    }

    @Test fun closedActivitiesRejectNewCoordinationButKeepReadActions() = runTest {
        var clock = now
        val repo = FakeGogetherRepository(DemoData.create(now), { clock })
        repo.login("alice").getOrThrow()
        repo.postAnnouncement("event-1", "Keep this history").getOrThrow()
        repo.cancelEvent("event-1").getOrThrow()
        val before = repo.state.value
        assertTrue(repo.postAnnouncement("event-1", "Closed").isFailure)
        assertTrue(repo.updateMeeting("event-1", MeetingUpdate(now + 1_000, "East gate", "")).isFailure)
        assertEquals(before, repo.state.value)
        repo.markAllRead().getOrThrow()
        assertEquals(1, repo.state.value.announcements.size)
        clock = repo.state.value.event("event-3")!!.endAt
        assertTrue(repo.postAnnouncement("event-3", "Too late").isFailure)
        assertTrue(repo.updateMeeting("event-3", MeetingUpdate(clock + 1_000, "East gate", "")).isFailure)
    }
}

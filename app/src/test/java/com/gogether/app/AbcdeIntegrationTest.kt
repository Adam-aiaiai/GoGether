package com.gogether.app

import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.feature.profile.participationSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AbcdeIntegrationTest {
    private var now = 1_800_000_000_000L
    private val seed = DemoData.create(now).let { s -> s.copy(currentUserId = "alice", events = s.events.map {
        if (it.id == "event-1") it.copy(startAt = now + 600_000, endAt = now + 7_800_000, registrationDeadline = now + 300_000) else it
    }) }
    private val repo = FakeGogetherRepository(seed, { now })
    private val data = MemoryPrototypeStore()
    private val metadata = mutableMapOf<String, CheckInActivityInfo>()
    private val store = object : PrototypeStore by data {
        override fun activityInfo(id: String) = metadata[id]
        override fun rememberActivityInfo(info: CheckInActivityInfo) { metadata[info.session.id] = info }
        override fun updateLinkedActivity(info: CheckInActivityInfo) {
            metadata[info.session.id] = info; data.updateLinkedActivity(info)
        }
    }
    private val bridge = RegistrationAttendanceBridge({ repo.state.value }, store, { now }, { 1000 })
    private val fix = PositionFix(MeetingPoint(31.2, 120.6), 5.0, 1000)
    private fun draft(e: Event) = EventDraft(e.title, e.category, e.description, e.startAt, e.location,
        e.city, e.capacity, e.registrationDeadline, e.notes, e.endAt, e.experienceRequirement,
        e.beginnerGuidance, e.requiredItems, e.spareEquipment, e.imageUri, e.meetingAt, e.meetingLocation, e.meetingNotes)

    @Test fun capacityPromotionNotificationIdentityAndAttendanceUseOneEvent() = runTest {
        val event = repo.state.value.event("event-1")!!
        repo.updateEvent(event.id, draft(event).copy(capacity = 5, beginnerGuidance = true, requiredItems = "Water", spareEquipment = "Two rackets")).getOrThrow()
        assertEquals(RegistrationStatus.CONFIRMED, repo.state.value.registration(event.id, "emma")!!.status)
        assertTrue(repo.state.value.notifications.any { it.userId == "emma" && it.type == NotificationType.PROMOTED })
        repo.postAnnouncement(event.id, "Meet at the east entrance.").getOrThrow()
        val session = bridge.prepare("alice", event.id, fix)
        val code = PrototypeCodeCodec.encode(bridge.backend.issue("alice", session, fix))
        repo.login("emma").getOrThrow()
        repo.updateProfile("Emma Parker", setOf(Category.SPORTS)).getOrThrow()
        bridge.backend.submit("emma", code, fix)
        repo.login("alice").getOrThrow()
        val person = bridge.backend.attendance("alice", session).single { it.userId == "emma" }
        assertEquals("Emma Parker", person.name); assertNotNull(person.checkedInAt)
        assertEquals(5, bridge.participants(session.id).size)
        assertEquals(1, bridge.withAttendance(repo.state.value).checkIns.size)
    }

    @Test fun meetingEditInvalidatesOldCodeAndRebindingKeepsAttendance() = runTest {
        val first = bridge.prepare("alice", "event-1", fix)
        val oldCode = PrototypeCodeCodec.encode(bridge.backend.issue("alice", first, fix))
        repo.login("bob").getOrThrow(); bridge.backend.submit("bob", oldCode, fix)
        repo.login("alice").getOrThrow()
        repo.updateMeeting("event-1", MeetingUpdate(now + 100_000, "East entrance", "Meet by the sign.")).getOrThrow()
        assertNull(bridge.session(repo.state.value.event("event-1")!!))
        assertThrows(IllegalArgumentException::class.java) { bridge.backend.issue("alice", first, fix) }
        val movedFix = fix.copy(point = MeetingPoint(31.21, 120.61))
        val second = bridge.prepare("alice", "event-1", movedFix)
        assertEquals(first.id, second.id)
        assertTrue(bridge.backend.activityInfo(second).meetingName.contains("East entrance"))
        repo.login("carol").getOrThrow()
        assertThrows(IllegalArgumentException::class.java) { bridge.backend.submit("carol", oldCode, fix) }
        repo.login("alice").getOrThrow()
        val newCode = PrototypeCodeCodec.encode(bridge.backend.issue("alice", second, movedFix))
        repo.login("carol").getOrThrow(); bridge.backend.submit("carol", newCode, movedFix)
        repo.login("alice").getOrThrow()
        assertEquals(setOf("bob", "carol"), bridge.backend.attendance("alice", second).filter { it.checkedInAt != null }.map { it.userId }.toSet())
    }

    @Test fun timeEditRetainsRecordKeyAndFullPrivateReviewProjectsOnlyOwnRating() = runTest {
        val first = bridge.prepare("alice", "event-1", fix)
        val e = repo.state.value.event("event-1")!!
        repo.updateEvent(e.id, draft(e).copy(startAt = e.startAt + 60_000, endAt = e.endAt + 60_000)).getOrThrow()
        assertThrows(IllegalArgumentException::class.java) { bridge.backend.issue("alice", first, fix) }
        now = e.endAt + 60_000
        repo.login("bob").getOrThrow()
        val current = bridge.prepareFeedback("bob", e.id)
        assertEquals(first.id, current.id)
        bridge.backend.submitFeedback("bob", current.id, 4, "Please improve the directions.", FeedbackDetails(anonymous = true, visibility = FeedbackVisibility.ORGANIZER_ONLY))
        val personal = bridge.withAttendance(repo.state.value)
        assertTrue(participationSummary(personal, personal.event(e.id)!!, now).contains("Your rating: 4/5"))
        assertEquals("", personal.feedback.single().comment)
        repo.login("carol").getOrThrow()
        assertTrue(bridge.withAttendance(repo.state.value).feedback.isEmpty())
        bridge.prepareFeedback("carol", e.id)
        assertTrue(bridge.backend.reviews("carol", current).isEmpty())
        repo.login("alice").getOrThrow(); bridge.prepareFeedback("alice", e.id)
        assertEquals("Anonymous participant", bridge.backend.reviews("alice", current).single().authorName)
    }

    private class Saved(var value: AppSnapshot? = null) : SnapshotStore {
        override fun load() = value
        override fun save(snapshot: AppSnapshot) { value = snapshot }
    }
    @Test fun migratingEitherBranchPreservesEventInstanceAndExistingDestinationWins() {
        for (fromAbd in listOf(true, false)) {
            val target = Saved()
            val migration = IntegratedSnapshotStore(target, Saved(if (fromAbd) seed else null)) { if (fromAbd) null else seed }
            assertEquals(seed, migration.load()); assertEquals(seed, target.value)
            assertEquals(bridge.key(seed.events.first()), bridge.key(target.value!!.events.first()))
            assertEquals(seed, IntegratedSnapshotStore(target, Saved(DemoData.create(now + 1))) { DemoData.create(now + 2) }.load())
        }
    }
    @Test fun conflictingOldDatasetsAreNotSilentlyOverwritten() {
        val target = Saved()
        assertThrows(IllegalArgumentException::class.java) {
            IntegratedSnapshotStore(target, Saved(seed)) { seed.copy(events = emptyList()) }.load()
        }
        assertNull(target.value)
    }
}

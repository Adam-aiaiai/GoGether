package com.gogether.app

import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AbdPersistenceTest {
    @Test fun actualAbV1SaveUpgradesWithoutLosingAnyExistingRecords() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val raw = instrumentation.context.assets.open("ab-snapshot-v1.json").bufferedReader().use { it.readText() }
        val original = SnapshotJson.decode(raw)
        assertEquals(6, original.events.size)
        assertEquals(12, original.registrations.size)
        assertEquals("alice", original.currentUserId)
        assertTrue(original.announcements.isEmpty())
        assertTrue(original.events.all { it.meetingAt == null })
        val file = File.createTempFile("ab-upgrade-", ".json", instrumentation.targetContext.cacheDir)
        try {
            file.writeText(raw)
            val store = FileSnapshotStore(file)
            val repo = FakeGogetherRepository(initial = store.load()!!, store = store)
            repo.markAllRead().getOrThrow()
            assertEquals(original, store.load())
            assertEquals(SnapshotJson.CURRENT_VERSION, org.json.JSONObject(file.readText()).getInt("version"))
        } finally { file.delete() }
    }

    @Test fun newCoordinationAndLegacyNotificationsRoundTripWithAllAbFields() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("abd-roundtrip-", ".json", context.cacheDir).apply { delete() }
        try {
            val store = FileSnapshotStore(file)
            val initial = DemoData.create().copy(currentUserId = "alice", notifications = listOf(
                Notification("legacy", "bob", "event-1", NotificationType.ANNOUNCEMENT, "Existing AB notice", 1, true)))
            val repo = FakeGogetherRepository(initial = initial, store = store)
            val event = repo.state.value.event("event-1")!!
            repo.updateMeeting(event.id, MeetingUpdate(event.startAt - 60_000, "East entrance", "Keep these notes")).getOrThrow()
            repo.postAnnouncement(event.id, "New D announcement").getOrThrow()
            val restored = FakeGogetherRepository(initial = store.load()!!, store = store).state.value
            assertEquals(repo.state.value, restored)
            assertEquals(initial.notifications.single(), restored.notifications.single { it.id == "legacy" })
            assertEquals(event.experienceRequirement, restored.event(event.id)?.experienceRequirement)
            assertEquals(event.spareEquipment, restored.event(event.id)?.spareEquipment)
            assertEquals("East entrance", restored.event(event.id)?.meetingLocation)
            assertEquals(1, restored.announcements.size)
        } finally { file.delete() }
    }
}

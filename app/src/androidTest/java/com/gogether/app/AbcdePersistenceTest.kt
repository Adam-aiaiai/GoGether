package com.gogether.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class AbcdePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun integratedFileRetainsAllMembersFieldsAndRestoresSession() {
        val file = File(context.cacheDir, "abcde-${UUID.randomUUID()}.json")
        try {
            val initial = DemoData.create().let { state -> state.copy(currentUserId = "bob",
                events = state.events.map { it.copy(beginnerGuidance = true, requiredItems = "Water",
                    spareEquipment = "Two rackets", meetingLocation = "East entrance", meetingNotes = "Meet by the sign.") },
                announcements = listOf(Announcement("notice", "event-1", "alice", "Bring water.", 123))) }
            val store = IntegratedSnapshotStore(FileSnapshotStore(file), object : SnapshotStore {
                override fun load(): AppSnapshot? = null
                override fun save(snapshot: AppSnapshot) = Unit
            }) { initial }
            assertEquals(initial, store.load())
            assertEquals(initial, FileSnapshotStore(file).load())
            assertEquals("bob", FakeGogetherRepository(FileSnapshotStore(file).load()!!).state.value.currentUserId)
        } finally { file.delete() }
    }

    @Test fun meetingRebindingPersistsNewSessionWithoutRemovingReceiptsOrPrivateDrafts(): Unit = runBlocking {
        var now = System.currentTimeMillis()
        val initial = DemoData.create(now).let { state -> state.copy(currentUserId = "alice", events = state.events.map {
            if (it.id == "event-1") it.copy(createdAt = System.nanoTime(), startAt = now + 600_000,
                endAt = now + 7_800_000, registrationDeadline = now + 300_000) else it
        }) }
        val repo = FakeGogetherRepository(initial, { now })
        val bridge = RegistrationAttendanceBridge({ repo.state.value }, PreferencesPrototypeStore(context), { now }, { 1000 })
        val fix = PositionFix(MeetingPoint(31.2, 120.6), 5.0, 1000)
        val first = bridge.prepare("alice", "event-1", fix)
        val drafts = FeedbackDraftStore(context)
        val draft = FeedbackDraft(4, "I will finish this review later.", FeedbackDetails(anonymous = true,
            visibility = FeedbackVisibility.ORGANIZER_ONLY))
        try {
            val oldCode = PrototypeCodeCodec.encode(bridge.backend.issue("alice", first, fix))
            repo.login("bob").getOrThrow(); bridge.backend.submit("bob", oldCode, fix)
            drafts.save("bob", first.id, draft)
            repo.login("alice").getOrThrow()
            now += 1000
            repo.updateMeeting("event-1", MeetingUpdate(now + 100_000, "East entrance", "Meet by the sign.")).getOrThrow()
            val second = bridge.prepare("alice", "event-1", fix.copy(point = MeetingPoint(31.21, 120.61)))
            val restored = PreferencesPrototypeStore(context)
            val reopened = RegistrationAttendanceBridge({ repo.state.value }, restored, { now }, { 1000 })
            assertEquals(first.id, second.id)
            assertEquals(second, restored.sessions("bob").single { it.id == first.id })
            assertEquals(second, reopened.session(repo.state.value.event("event-1")!!))
            assertEquals("bob", reopened.backend.attendance("alice", second).single { it.checkedInAt != null }.userId)
            assertEquals(draft, FeedbackDraftStore(context).load("bob", first.id))
            repo.login("carol").getOrThrow()
            assertEquals(FeedbackDraft(), drafts.load("carol", first.id))
            assertThrows(IllegalArgumentException::class.java) { reopened.backend.submit("carol", oldCode, fix) }
        } finally {
            val prefs = context.getSharedPreferences("checkin_prototype_v1", Context.MODE_PRIVATE)
            val edit = prefs.edit()
            prefs.all.keys.filter { it.contains(first.id) }.forEach(edit::remove)
            listOf("bob", "carol").forEach { edit.remove(FeedbackDraftStore.key(it, first.id)) }
            edit.commit()
        }
    }
}

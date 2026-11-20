package com.gogether.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.gogether.app.core.data.*
import com.gogether.app.core.model.RegistrationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LocalSnapshotStoreTest {
    @Test fun roundTripPreservesAllBusinessFieldsWithoutRestoringDemoLogin() = runBlocking {
        val source = FakeGogetherRepository(DemoData.create())
        source.login("bob").getOrThrow()
        source.cancelRegistration("event-1").getOrThrow()
        val original = source.state.value
        val restored = LocalSnapshotStore.decode(LocalSnapshotStore.encode(original))
        assertEquals(original.copy(currentUserId = null), restored)
    }
    @Test fun recreatedRepositoryRetainsPromotionAndEventIdentity() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val name = "ce-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String, mode: Int) = base.getSharedPreferences(name, mode)
        }
        try {
            val store = LocalSnapshotStore(context)
            val repo = FakeGogetherRepository(DemoData.create(), save = store::save)
            repo.login("bob").getOrThrow(); repo.cancelRegistration("event-1").getOrThrow()
            val loaded = LocalSnapshotStore(context).load()!!
            assertNull(loaded.currentUserId)
            assertEquals(repo.state.value.events, loaded.events)
            val recreated = FakeGogetherRepository(loaded, save = store::save)
            recreated.login("emma").getOrThrow()
            assertEquals(RegistrationStatus.CONFIRMED, recreated.state.value.registration("event-1")!!.status)
        } finally { base.deleteSharedPreferences(name) }
    }
    @Test fun originalCeVersionOneStillRestoresStableKeysAndAttendance() {
        val old = """{"version":1,"users":[{"id":"bob","name":"Bob","created":10,"interests":["SPORTS"]}],
            "events":[{"id":"old-event","owner":"bob","title":"A friendly game","category":"SPORTS",
            "description":"Meet at the court","start":100,"end":200,"location":"Court 1","city":"Suzhou",
            "capacity":4,"deadline":90,"notes":"Bring water","created":10,"updated":11,"status":"COMPLETED"}],
            "registrations":[{"id":"registration","event":"old-event","user":"bob","status":"CONFIRMED",
            "joined":20,"updated":20,"order":1}],"checkIns":[{"id":"receipt","event":"old-event","user":"bob","time":105}],
            "notifications":[],"feedback":[{"id":"review","event":"old-event","user":"bob","rating":4,"comment":"Enjoyed it","created":210}]}"""
        val restored = LocalSnapshotStore.decode(old)
        assertEquals(10L, restored.events.single().createdAt)
        assertEquals("old-event", restored.checkIns.single().eventId)
        assertEquals("bob", restored.checkIns.single().userId)
        assertEquals(4, restored.feedback.single().rating)
        assertNull(restored.currentUserId)
        assertEquals(restored, LocalSnapshotStore.decode(LocalSnapshotStore.encode(restored)))
    }
    @Test fun invalidVersionIsNotSilentlyReplacedWithSeedData() {
        assertThrows(IllegalArgumentException::class.java) { LocalSnapshotStore.decode("{\"version\":99}") }
    }
}

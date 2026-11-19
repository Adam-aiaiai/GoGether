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
    @Test fun invalidVersionIsNotSilentlyReplacedWithSeedData() {
        assertThrows(IllegalArgumentException::class.java) { LocalSnapshotStore.decode("{\"version\":2}") }
    }
}

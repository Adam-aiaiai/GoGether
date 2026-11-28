package com.gogether.app

import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PendingCheckInStoreTest {
    @Test fun pendingRequestsSurviveRecreationAndStayIsolatedByAccountAndActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PreferencesPendingCheckInStore(context)
        val session = PrototypeSession(UUID.randomUUID().toString(), 1000, 5000)
        val bob = CheckInAttempt(UUID.randomUUID().toString(), "bob", session)
        val carol = bob.copy(id = UUID.randomUUID().toString(), actor = "carol")
        val other = bob.copy(id = UUID.randomUUID().toString(), session = session.copy(id = UUID.randomUUID().toString()))
        try {
            store.save(bob); store.save(carol); store.save(other)
            val restored = PreferencesPendingCheckInStore(context)
            assertEquals(bob, restored.get("bob", session.id))
            assertEquals(carol, restored.get("carol", session.id))
            assertEquals(other, restored.get("bob", other.session.id))
            assertNull(restored.get("alice", session.id))
            restored.remove(bob.copy(id = "outdated-response"))
            assertEquals(bob, restored.get("bob", session.id))
            assertTrue(runCatching { restored.save(bob.copy(id = "another-request")) }.isFailure)
            restored.remove(bob)
            assertNull(store.get("bob", session.id))
            assertEquals(carol, store.get("carol", session.id))
            assertEquals(other, store.get("bob", other.session.id))
        } finally { store.remove(bob); store.remove(carol); store.remove(other) }
    }
}

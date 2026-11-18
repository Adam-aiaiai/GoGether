package com.gogether.app

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.gogether.app.core.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CoordinationPersistenceTest {
    @Test fun androidPrivateFileRestoresAnnouncementAndReadState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("coordination-test-", ".json", context.cacheDir)
        file.delete()
        try {
            val store = FileSnapshotStore(file)
            val repo = FakeGogetherRepository(store = store)
            repo.login("alice").getOrThrow()
            repo.postAnnouncement("event-1", "Meet at east gate").getOrThrow()
            repo.login("bob").getOrThrow()
            val notification = repo.state.value.notifications.single { it.userId == "bob" }
            repo.markRead(notification.id).getOrThrow()
            val reopened = FakeGogetherRepository(store = store)
            assertEquals(repo.state.value, reopened.state.value)
            assertTrue(reopened.state.value.notifications.single { it.id == notification.id }.isRead)
        } finally { file.delete() }
    }
}

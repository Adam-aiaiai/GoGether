package com.gogether.app

import com.gogether.app.core.data.*
import com.gogether.app.core.data.cloud.CloudDto
import com.gogether.app.core.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProfileDetailsTest {
    private class Store : SnapshotStore {
        var saved: AppSnapshot? = null
        var fail = false
        override fun load() = saved
        override fun save(snapshot: AppSnapshot) {
            if (fail) throw java.io.IOException("Disk full")
            saved = snapshot
        }
    }
    @Test fun photosAndPrivacySurviveRestartWithoutChangingActivityIdentity() = runTest {
        val store = Store()
        val repo = FakeGogetherRepository(store = store)
        repo.login("alice").getOrThrow()
        val before = repo.state.value
        val update = ProfileUpdate("Alice Park", Category.entries.toSet(), "file:///avatar.jpg", "file:///cover.jpg",
            Gender.NON_BINARY, false, "  See you outdoors!  ")
        val saved = repo.updateProfile(update).getOrThrow()
        assertEquals("alice", saved.id)
        assertEquals("See you outdoors!", saved.signature)
        assertFalse(saved.showGender)
        assertEquals(before.events, repo.state.value.events)
        assertEquals(before.registrations, repo.state.value.registrations)
        val restored = FakeGogetherRepository(initial = store.load()!!, store = store).state.value.users.single { it.id == "alice" }
        assertEquals(saved, restored)
        store.fail = true
        assertTrue(repo.updateProfile(update.copy(signature = "Changed")).isFailure)
        assertEquals(saved, repo.state.value.currentUser)
    }
    @Test fun undisclosedGenderCannotBeShownAndLongSignaturesAreRejected() = runTest {
        val repo = FakeGogetherRepository()
        repo.login("alice").getOrThrow()
        val update = ProfileUpdate("Alice", emptySet(), showGender = true)
        assertFalse(repo.updateProfile(update).getOrThrow().showGender)
        assertTrue(repo.updateProfile(update.copy(signature = "a".repeat(101))).isFailure)
    }
    @Test fun oldCloudProfilesHaveSafeDefaults() {
        val user = CloudDto.user(mapOf("id" to "alice", "displayName" to "Alice", "createdAt" to 1L))
        assertEquals("", user.avatarUri)
        assertEquals("", user.coverUri)
        assertEquals(Gender.UNDISCLOSED, user.gender)
        assertFalse(user.showGender)
    }
}

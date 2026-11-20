package com.gogether.app

import com.gogether.app.core.account.*
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AccountRepositoryTest {
    private class MemoryStore : SnapshotStore {
        var saved: AppSnapshot? = null
        var fail = false
        override fun load() = saved
        override fun save(snapshot: AppSnapshot) {
            if (fail) throw IOException("Storage full")
            saved = snapshot
        }
    }
    private class TestBackend : AccountBackend {
        var user: User? = null
        var fail = false
        var calls = 0
        override suspend fun restoreSession(): User? {
            if (fail) throw IOException("Offline")
            return user
        }
        override suspend fun signIn(email: String, password: String): User {
            calls++
            if (fail) throw IllegalArgumentException("Unable to sign in")
            return User("remote-user", "Remote user", setOf(Category.STUDY), 1,
                UserStatus.ACCOUNT, email).also { user = it }
        }
        override suspend fun signUp(email: String, password: String, name: String, interests: Set<Category>) =
            signIn(email, password).copy(displayName = name, interests = interests).also { user = it }
        override suspend fun updateProfile(name: String, interests: Set<Category>): User {
            if (fail) throw IOException("Offline")
            return requireNotNull(user).copy(displayName = name, interests = interests).also { user = it }
        }
        override suspend fun signOut() { user = null }
    }

    @Test fun editsKeepIdentityAndAllActivityRecordsAndSurviveReconstruction() = runTest {
        val store = MemoryStore()
        val repo = FakeGogetherRepository(store = store)
        repo.login("alice").getOrThrow()
        val before = repo.state.value
        repo.updateProfile("  Alice Li  ", setOf(Category.STUDY, Category.LANGUAGE)).getOrThrow()
        val restored = FakeGogetherRepository(initial = store.load()!!, store = store)
        assertEquals("alice", restored.state.value.currentUserId)
        assertEquals("Alice Li", restored.state.value.currentUser?.displayName)
        assertEquals(setOf(Category.STUDY, Category.LANGUAGE), restored.state.value.currentUser?.interests)
        assertEquals(before.events, restored.state.value.events)
        assertEquals(before.registrations, restored.state.value.registrations)
    }
    @Test fun logoutIsSavedAndDoesNotRemoveProfileOrPlans() = runTest {
        val store = MemoryStore()
        val repo = FakeGogetherRepository(store = store)
        repo.login("bob").getOrThrow()
        val before = repo.state.value
        repo.logout().getOrThrow()
        assertNull(FakeGogetherRepository(initial = store.load()!!).state.value.currentUser)
        assertEquals(before.users, store.saved?.users)
        assertEquals(before.registrations, store.saved?.registrations)
    }
    @Test fun failedDiskWriteDoesNotPublishOrKeepAnUnreportedChange() = runTest {
        val store = MemoryStore()
        val repo = FakeGogetherRepository(store = store)
        repo.login("alice").getOrThrow()
        val before = repo.state.value
        store.fail = true
        assertTrue(repo.updateProfile("Unsaved name", emptySet()).isFailure)
        assertEquals(before, repo.state.value)
        store.fail = false
        repo.logout().getOrThrow()
        assertEquals("Alice", store.saved!!.users.single { it.id == "alice" }.displayName)
    }
    @Test fun editingRequiresSessionAndRejectsBlankDuplicateAndControlNames() = runTest {
        val repo = FakeGogetherRepository()
        assertTrue(repo.updateProfile("Tester", emptySet()).isFailure)
        repo.login("bob").getOrThrow()
        assertTrue(repo.updateProfile("  ", emptySet()).isFailure)
        assertTrue(repo.updateProfile("ALICE", emptySet()).isFailure)
        assertTrue(repo.updateProfile("Bob\nTest", emptySet()).isFailure)
        assertTrue(repo.updateProfile("Bob", emptySet()).isSuccess)
    }
    @Test fun missingBackendDoesNotPretendToAuthenticate() = runTest {
        val repo = FakeGogetherRepository()
        assertEquals(AccountMode.LOCAL_DEMO, repo.accountMode)
        assertTrue(repo.signIn("test@example.com", "password").isFailure)
        assertNull(repo.state.value.currentUser)
    }
    @Test fun connectedBackendRequiresTrustedSessionAndDisablesDemoIdentitySelection() = runTest {
        val backend = TestBackend()
        val repo = FakeGogetherRepository(initial = DemoData.create().copy(currentUserId = "alice"), accountBackend = backend)
        assertNull(repo.state.value.currentUser)
        assertTrue(repo.state.value.sessionRestoring)
        assertTrue(repo.login("alice").isFailure)
        assertTrue(repo.register("Test", emptySet()).isFailure)
        repo.restoreAccountSession().getOrThrow()
        assertFalse(repo.state.value.sessionRestoring)
        repo.signIn(" test@example.com ", "password").getOrThrow()
        assertEquals("remote-user", repo.state.value.currentUserId)
        assertEquals("test@example.com", repo.state.value.currentUser?.email)
        repo.updateProfile("Updated user", setOf(Category.SOCIAL)).getOrThrow()
        assertEquals("Updated user", backend.user?.displayName)
        repo.logout().getOrThrow()
        assertNull(backend.user)
        assertNull(repo.state.value.currentUserId)
    }
    @Test fun validationRunsBeforeSendingCredentials() = runTest {
        val backend = TestBackend()
        val repo = FakeGogetherRepository(accountBackend = backend)
        repo.restoreAccountSession().getOrThrow()
        assertTrue(repo.signIn("broken-email", "pass").isFailure)
        assertTrue(repo.signUp("test@example.com", "short", "Test user", emptySet()).isFailure)
        assertEquals(0, backend.calls)
        assertTrue(repo.signUp("test@example.com", "long-password", " Test user ", emptySet()).isSuccess)
        assertEquals("Test user", repo.state.value.currentUser?.displayName)
    }
    @Test fun restoreFailureLeavesSignInAvailableAndCanRetry() = runTest {
        val backend = TestBackend().apply { fail = true }
        val repo = FakeGogetherRepository(accountBackend = backend)
        assertTrue(repo.restoreAccountSession().isFailure)
        assertFalse(repo.state.value.sessionRestoring)
        assertNull(repo.state.value.currentUser)
        assertNotNull(repo.state.value.sessionError)
        backend.fail = false
        backend.signIn("test@example.com", "password")
        repo.restoreAccountSession().getOrThrow()
        assertEquals("remote-user", repo.state.value.currentUserId)
        assertNull(repo.state.value.sessionError)
    }
    @Test fun remoteProfileFailureKeepsExistingProfile() = runTest {
        val backend = TestBackend()
        val repo = FakeGogetherRepository(accountBackend = backend)
        repo.signIn("test@example.com", "password").getOrThrow()
        val before = repo.state.value
        backend.fail = true
        assertTrue(repo.updateProfile("Unsaved", emptySet()).isFailure)
        assertEquals(before, repo.state.value)
    }
    @Test fun aQueuedActionCannotRunUnderAnotherUsersSession() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val backend = object : AccountBackend {
            override suspend fun restoreSession(): User? = null
            override suspend fun signIn(email: String, password: String): User {
                entered.complete(Unit)
                release.await()
                return User("remote", "Remote user", emptySet(), 1, UserStatus.ACCOUNT, email)
            }
            override suspend fun signUp(email: String, password: String, name: String, interests: Set<Category>): User = error("unused")
            override suspend fun updateProfile(name: String, interests: Set<Category>): User = error("queued request must be rejected")
            override suspend fun signOut() = Unit
        }
        val repo = FakeGogetherRepository(accountBackend = backend)
        val login = async { repo.signIn("test@example.com", "password") }
        entered.await()
        val edit = async(start = CoroutineStart.UNDISPATCHED) { repo.updateProfile("Wrong user", emptySet()) }
        release.complete(Unit)
        assertTrue(login.await().isSuccess)
        assertTrue(edit.await().isFailure)
        assertEquals("Remote user", repo.state.value.currentUser?.displayName)
    }
}

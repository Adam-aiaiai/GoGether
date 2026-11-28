package com.gogether.app.core.data

import com.gogether.app.core.model.*
import com.gogether.app.core.account.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.gogether.app.feature.registration.RegistrationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes operations in ONE process. This is not cross-device concurrency control. */
class FakeGogetherRepository(
    initial: AppSnapshot = DemoData.create(),
    now: () -> Long = System::currentTimeMillis,
    private val store: SnapshotStore? = null,
    private val accountBackend: AccountBackend? = null,
    override val storageDescription: String = if (store == null) "This test session is temporary." else
        "Demo profiles and activities are saved on this device. Other devices have separate data.",
    private val save: (AppSnapshot) -> Unit = {},
) : GogetherRepository {
    init { require(accountBackend == null || store == null) { "A connected account backend owns its own persistence." } }
    override val accountMode = if (accountBackend == null) AccountMode.LOCAL_DEMO else AccountMode.CONNECTED
    private val startingState = if (accountBackend == null) initial.copy(
        currentUserId = initial.currentUserId?.takeIf { id -> initial.users.any { it.id == id && it.status == UserStatus.DEMO } },
        sessionRestoring = false, sessionError = null,
    ) else initial.copy(currentUserId = null, sessionRestoring = true, sessionError = null)
    private val engine = RegistrationEngine(startingState, now)
    private val mutex = Mutex()
    @Volatile private var sessionGeneration = 0L
    private val mutableState = MutableStateFlow(startingState)
    override val state = mutableState.asStateFlow()
    private fun actor() = requireNotNull(engine.snapshot.currentUserId) { "Sign in or choose a Demo profile first." }

    private suspend fun <T> mutate(
        failureState: ((AppSnapshot) -> AppSnapshot)? = null,
        action: suspend () -> T,
    ): Result<T> {
        val expectedSession = sessionGeneration
        return mutex.withLock {
            val before = engine.snapshot
            try {
                require(expectedSession == sessionGeneration) { "The account changed. Please try the action again." }
                val result = action()
                // Persist before publishing success. A failed write leaves the visible and in-memory
                // state unchanged, so a failed registration cannot silently occupy a place.
                withContext(NonCancellable) {
                    fun persistAndPublish() {
                        synchronized(LocalDataLock.monitor) {
                            store?.save(engine.snapshot.copy(sessionRestoring = false, sessionError = null))
                            save(engine.snapshot)
                            if (before.currentUserId != engine.snapshot.currentUserId) sessionGeneration++
                            mutableState.value = engine.snapshot
                        }
                    }
                    if (store != null) withContext(Dispatchers.IO) { persistAndPublish() }
                    else persistAndPublish()
                }
                Result.success(result)
            } catch (cancelled: CancellationException) {
                engine.replaceSnapshot(before)
                throw cancelled
            } catch (error: Exception) {
                val next = if (expectedSession == sessionGeneration && failureState != null) failureState(before) else before
                engine.replaceSnapshot(next)
                synchronized(LocalDataLock.monitor) {
                    if (before.currentUserId != next.currentUserId) sessionGeneration++
                    if (next != before) mutableState.value = next
                }
                Result.failure(IllegalStateException(error.message ?: "The operation could not be completed.", error))
            }
        }
    }

    override suspend fun restoreAccountSession(): Result<Unit> {
        if (accountBackend == null) return Result.success(Unit)
        return mutate(failureState = { it.copy(currentUserId = null, sessionRestoring = false,
            sessionError = "We couldn't restore your account. Try again or sign in.") }) {
            val user = accountBackend.restoreSession()
            engine.replaceSnapshot(if (user == null) engine.snapshot.copy(currentUserId = null,
                sessionRestoring = false, sessionError = null) else AccountState.accept(engine.snapshot, user))
        }
    }

    override suspend fun login(userId: String) = mutate {
        require(accountBackend == null) { "Use email and password to sign in." }
        require(engine.snapshot.users.any { it.id == userId && it.status == UserStatus.DEMO }) { "Test profile not found." }
        engine.login(userId)
    }
    override suspend fun register(name: String, interests: Set<Category>) = mutate {
        require(accountBackend == null) { "Use account registration." }
        engine.registerUser(AccountRules.name(name), interests)
    }
    override suspend fun signIn(email: String, password: String) = mutate {
        val backend = requireNotNull(accountBackend) { "Team account sign-in is not connected yet. Use a Demo profile." }
        val cleanEmail = AccountRules.email(email)
        AccountRules.password(password)
        engine.replaceSnapshot(AccountState.accept(engine.snapshot, backend.signIn(cleanEmail, password)))
    }
    override suspend fun signUp(email: String, password: String, name: String, interests: Set<Category>) = mutate {
        val backend = requireNotNull(accountBackend) { "Team account sign-up is not connected yet. Use a Demo profile." }
        val cleanEmail = AccountRules.email(email)
        val cleanName = AccountRules.name(name)
        AccountRules.password(password, creating = true)
        backend.signUp(cleanEmail, password, cleanName, interests.toSet()).also {
            engine.replaceSnapshot(AccountState.accept(engine.snapshot, it))
        }
    }
    override suspend fun updateProfile(name: String, interests: Set<Category>) = mutate {
        val currentId = actor()
        if (accountBackend == null) {
            val (next, updated) = AccountState.edit(engine.snapshot, name, interests)
            engine.replaceSnapshot(next)
            updated
        } else {
            val updated = accountBackend.updateProfile(AccountRules.name(name), interests.toSet())
            require(updated.id == currentId) { "The account changed. Sign in again before editing." }
            engine.replaceSnapshot(AccountState.accept(engine.snapshot, updated))
            updated
        }
    }
    override suspend fun logout() = mutate {
        accountBackend?.signOut()
        engine.logout()
    }
    override suspend fun updateProfile(profile: ProfileUpdate) = mutate {
        val currentId = actor()
        if (accountBackend == null) {
            val (next, updated) = AccountState.edit(engine.snapshot, profile)
            engine.replaceSnapshot(next)
            updated
        } else {
            val updated = accountBackend.updateProfile(profile.copy(name = AccountRules.name(profile.name)))
            require(updated.id == currentId) { "The account changed. Sign in again before editing." }
            engine.replaceSnapshot(AccountState.accept(engine.snapshot, updated))
            updated
        }
    }
    override suspend fun createEvent(draft: EventDraft) = mutate { engine.createEvent(actor(), draft) }
    override suspend fun updateEvent(eventId: String, draft: EventDraft) = mutate {
        engine.updateEvent(actor(), eventId, draft)
    }
    override suspend fun join(eventId: String) = mutate { engine.join(actor(), eventId) }
    override suspend fun cancelRegistration(eventId: String) = mutate { engine.cancelRegistration(actor(), eventId) }
    override suspend fun cancelEvent(eventId: String) = mutate { engine.cancelEvent(actor(), eventId) }
    override suspend fun checkIn(eventId: String) = mutate { engine.checkIn(actor(), eventId) }
    override suspend fun postAnnouncement(eventId: String, content: String) = mutate { engine.postAnnouncement(actor(), eventId, content) }
    override suspend fun askQuestion(eventId: String, content: String) = mutate { engine.askQuestion(actor(), eventId, content) }
    override suspend fun answerQuestion(questionId: String, content: String) = mutate { engine.answerQuestion(actor(), questionId, content) }
    override suspend fun updateMeeting(eventId: String, update: MeetingUpdate) = mutate { engine.updateMeeting(actor(), eventId, update) }
    override suspend fun markAllRead() = mutate { engine.markAllRead(actor()) }
    override suspend fun markRead(notificationId: String) = mutate { engine.markRead(actor(), notificationId) }
    override suspend fun submitFeedback(eventId: String, rating: Int, comment: String) = mutate {
        engine.submitFeedback(actor(), eventId, rating, comment)
    }
}

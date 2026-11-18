package com.gogether.app.core.data

import com.gogether.app.core.model.*
import com.gogether.app.feature.registration.RegistrationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes operations in ONE process. This is not cross-device concurrency control. */
class FakeGogetherRepository(
    initial: AppSnapshot = DemoData.create(),
    private val now: () -> Long = System::currentTimeMillis,
    private val store: SnapshotStore? = null,
) : GogetherRepository {
    private var engine = RegistrationEngine(store?.load() ?: initial, now)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(engine.snapshot)
    override val state = mutableState.asStateFlow()
    private fun actor() = requireNotNull(engine.snapshot.currentUserId) { "Choose a Demo user first." }

    private suspend fun <T> mutate(action: RegistrationEngine.() -> T): Result<T> = mutex.withLock {
        try {
            val candidate = RegistrationEngine(engine.snapshot, now)
            val result = candidate.action()
            withContext(NonCancellable + Dispatchers.IO) {
                store?.save(candidate.snapshot)
                engine = candidate
                mutableState.value = candidate.snapshot
            }
            Result.success(result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    override suspend fun login(userId: String) = mutate { login(userId) }
    override suspend fun register(name: String, interests: Set<Category>) = mutate { registerUser(name, interests) }
    override suspend fun logout() = mutate { logout() }
    override suspend fun createEvent(draft: EventDraft) = mutate { createEvent(actor(), draft) }
    override suspend fun join(eventId: String) = mutate { join(actor(), eventId) }
    override suspend fun cancelRegistration(eventId: String) = mutate { cancelRegistration(actor(), eventId) }
    override suspend fun cancelEvent(eventId: String) = mutate { cancelEvent(actor(), eventId) }
    override suspend fun setDemoCheckInOpen(eventId: String, open: Boolean) = mutate { setDemoCheckInOpen(actor(), eventId, open) }
    override suspend fun checkIn(eventId: String) = mutate { checkIn(actor(), eventId) }
    override suspend fun postAnnouncement(eventId: String, content: String) = mutate { postAnnouncement(actor(), eventId, content) }
    override suspend fun updateMeeting(eventId: String, update: MeetingUpdate) = mutate { updateMeeting(actor(), eventId, update) }
    override suspend fun markAllRead() = mutate { markAllRead(actor()) }
    override suspend fun markRead(notificationId: String) = mutate { markRead(actor(), notificationId) }
    override suspend fun submitFeedback(eventId: String, rating: Int, comment: String) = mutate {
        submitFeedback(actor(), eventId, rating, comment)
    }
}

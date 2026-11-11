package com.gogether.app.core.data

import com.gogether.app.core.model.*
import com.gogether.app.feature.registration.RegistrationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes operations in ONE process. This is not cross-device concurrency control. */
class FakeGogetherRepository(
    initial: AppSnapshot = DemoData.create(),
    now: () -> Long = System::currentTimeMillis,
) : GogetherRepository {
    private val engine = RegistrationEngine(initial, now)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(initial)
    override val state = mutableState.asStateFlow()
    private fun actor() = requireNotNull(engine.snapshot.currentUserId) { "Choose a Demo user first." }

    private suspend fun <T> mutate(action: () -> T): Result<T> = mutex.withLock {
        try {
            val result = action()
            mutableState.value = engine.snapshot
            Result.success(result)
        } catch (error: IllegalArgumentException) {
            Result.failure(error)
        }
    }
    override suspend fun login(userId: String) = mutate { engine.login(userId) }
    override suspend fun register(name: String, interests: Set<Category>) = mutate { engine.registerUser(name, interests) }
    override suspend fun logout() = mutate { engine.logout() }
    override suspend fun createEvent(draft: EventDraft) = mutate { engine.createEvent(actor(), draft) }
    override suspend fun join(eventId: String) = mutate { engine.join(actor(), eventId) }
    override suspend fun cancelRegistration(eventId: String) = mutate { engine.cancelRegistration(actor(), eventId) }
    override suspend fun cancelEvent(eventId: String) = mutate { engine.cancelEvent(actor(), eventId) }
    override suspend fun setDemoCheckInOpen(eventId: String, open: Boolean) = mutate { engine.setDemoCheckInOpen(actor(), eventId, open) }
    override suspend fun checkIn(eventId: String) = mutate { engine.checkIn(actor(), eventId) }
    override suspend fun postAnnouncement(eventId: String, content: String) = mutate { engine.postAnnouncement(actor(), eventId, content) }
    override suspend fun markRead(notificationId: String) = mutate { engine.markRead(actor(), notificationId) }
    override suspend fun submitFeedback(eventId: String, rating: Int, comment: String) = mutate {
        engine.submitFeedback(actor(), eventId, rating, comment)
    }
}

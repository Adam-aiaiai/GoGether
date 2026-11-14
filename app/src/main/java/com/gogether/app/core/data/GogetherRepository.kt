package com.gogether.app.core.data

import com.gogether.app.core.model.*
import kotlinx.coroutines.flow.StateFlow

/** Replace this local implementation with a trusted backend; the UI observes the same contract. */
interface GogetherRepository {
    val state: StateFlow<AppSnapshot>
    suspend fun login(userId: String): Result<Unit>
    suspend fun register(name: String, interests: Set<Category>): Result<User>
    suspend fun logout(): Result<Unit>
    suspend fun createEvent(draft: EventDraft): Result<Event>
    suspend fun updateEvent(eventId: String, draft: EventDraft): Result<Event>
    suspend fun join(eventId: String): Result<Registration>
    suspend fun cancelRegistration(eventId: String): Result<Unit>
    suspend fun cancelEvent(eventId: String): Result<Unit>
    suspend fun setDemoCheckInOpen(eventId: String, open: Boolean): Result<Unit>
    suspend fun checkIn(eventId: String): Result<CheckIn>
    suspend fun postAnnouncement(eventId: String, content: String): Result<Unit>
    suspend fun markRead(notificationId: String): Result<Unit>
    suspend fun submitFeedback(eventId: String, rating: Int, comment: String): Result<Feedback>
}

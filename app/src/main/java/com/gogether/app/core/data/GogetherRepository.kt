package com.gogether.app.core.data

import com.gogether.app.core.model.*
import com.gogether.app.core.account.AccountMode
import kotlinx.coroutines.flow.StateFlow

/** Replace this local implementation with a trusted backend; the UI observes the same contract. */
interface GogetherRepository {
    val state: StateFlow<AppSnapshot>
    val accountMode: AccountMode
    val storageDescription: String
    suspend fun restoreAccountSession(): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signUp(email: String, password: String, name: String, interests: Set<Category>): Result<User>
    suspend fun updateProfile(name: String, interests: Set<Category>): Result<User>
    suspend fun login(userId: String): Result<Unit>
    suspend fun register(name: String, interests: Set<Category>): Result<User>
    suspend fun logout(): Result<Unit>
    suspend fun createEvent(draft: EventDraft): Result<Event>
    suspend fun updateEvent(eventId: String, draft: EventDraft): Result<Event>
    suspend fun join(eventId: String): Result<Registration>
    suspend fun cancelRegistration(eventId: String): Result<Unit>
    suspend fun cancelEvent(eventId: String): Result<Unit>
    suspend fun checkIn(eventId: String): Result<CheckIn>
    suspend fun postAnnouncement(eventId: String, content: String): Result<Unit>
    suspend fun markRead(notificationId: String): Result<Unit>
    suspend fun markAllRead(): Result<Unit>
    suspend fun updateMeeting(eventId: String, update: MeetingUpdate): Result<Unit>
    suspend fun submitFeedback(eventId: String, rating: Int, comment: String): Result<Feedback>
}

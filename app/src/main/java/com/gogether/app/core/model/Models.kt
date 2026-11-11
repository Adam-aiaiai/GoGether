package com.gogether.app.core.model

import java.time.Instant

data class User(
    val id: String,
    val nickname: String,
    val interests: List<String>,
    val isOrganizer: Boolean = false,
)

enum class RegistrationStatus { CONFIRMED, WAITLISTED, CANCELLED, REJECTED }
enum class EventStatus { OPEN, CANCELLED, COMPLETED }

data class Event(
    val id: String,
    val creatorId: String,
    val title: String,
    val category: String,
    val description: String,
    val startAt: Instant,
    val meetingLocation: String,
    val maxParticipants: Int,
    val registrationDeadline: Instant,
    val additionalNotes: String = "",
    val status: EventStatus = EventStatus.OPEN,
    val createdAt: Instant,
)

data class Registration(
    val id: String,
    val eventId: String,
    val userId: String,
    val status: RegistrationStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CheckIn(
    val id: String,
    val eventId: String,
    val userId: String,
    val checkedInAt: Instant,
    val demo: Boolean = true,
)

data class Notification(
    val id: String,
    val userId: String,
    val message: String,
    val createdAt: Instant,
    val read: Boolean = false,
)

data class Feedback(
    val id: String,
    val eventId: String,
    val userId: String,
    val rating: Int,
    val comment: String,
    val createdAt: Instant,
)

data class EventDraft(
    val title: String,
    val category: String,
    val description: String,
    val startAt: Instant,
    val meetingLocation: String,
    val maxParticipants: Int,
    val registrationDeadline: Instant,
    val additionalNotes: String = "",
)

data class EventCounts(val confirmed: Int, val waitlisted: Int)

sealed interface RegistrationOutcome {
    data class Joined(val registration: Registration) : RegistrationOutcome
    data class Waitlisted(val registration: Registration) : RegistrationOutcome
    data class Rejected(val reason: String) : RegistrationOutcome
}

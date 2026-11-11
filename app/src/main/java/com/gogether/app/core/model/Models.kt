package com.gogether.app.core.model

enum class Category(val label: String, val symbol: String) {
    SPORTS("Sports", "↗"), OUTDOOR("Outdoor", "△"), PHOTOGRAPHY("Photography", "◎"),
    BOARD_GAMES("Board Games", "▦"), READING("Reading", "≋"), OTHERS("Others", "+")
}
enum class RegistrationStatus { CONFIRMED, WAITLISTED, CANCELLED, REJECTED }
enum class EventStatus { OPEN, CANCELLED, COMPLETED }
enum class UserStatus { DEMO }
enum class CheckInStatus { DEMO_RECORDED }
enum class NotificationType { PROMOTED, EVENT_CANCELLED, ANNOUNCEMENT }
enum class FeedbackStatus { SUBMITTED }

data class User(
    val id: String, val displayName: String, val interests: Set<Category>, val createdAt: Long,
    val status: UserStatus = UserStatus.DEMO,
)
data class Event(
    val id: String, val organizerId: String, val title: String, val category: Category,
    val description: String, val startAt: Long, val endAt: Long, val location: String,
    val city: String, val capacity: Int, val registrationDeadline: Long, val notes: String,
    val createdAt: Long, val updatedAt: Long, val status: EventStatus = EventStatus.OPEN,
    val demoCheckInOpen: Boolean = false,
) {
    fun acceptsRegistration(now: Long) = status == EventStatus.OPEN &&
        now < registrationDeadline && now < startAt && now < endAt
}
data class Registration(
    val id: String, val eventId: String, val userId: String, val status: RegistrationStatus,
    val joinedAt: Long, val updatedAt: Long, val queueOrder: Long,
)
data class CheckIn(
    val id: String, val eventId: String, val userId: String, val checkedInAt: Long,
    val status: CheckInStatus = CheckInStatus.DEMO_RECORDED,
)
data class Notification(
    val id: String, val userId: String, val eventId: String, val type: NotificationType,
    val content: String, val createdAt: Long, val isRead: Boolean = false,
)
data class Feedback(
    val id: String, val eventId: String, val userId: String, val rating: Int,
    val comment: String, val createdAt: Long, val status: FeedbackStatus = FeedbackStatus.SUBMITTED,
)
data class EventDraft(
    val title: String, val category: Category, val description: String, val startAt: Long,
    val location: String, val city: String, val capacity: Int,
    val registrationDeadline: Long, val notes: String = "",
)

/** One immutable snapshot is published for every transaction, including promotions and notifications. */
data class AppSnapshot(
    val users: List<User> = emptyList(), val currentUserId: String? = null,
    val events: List<Event> = emptyList(), val registrations: List<Registration> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(), val notifications: List<Notification> = emptyList(),
    val feedback: List<Feedback> = emptyList(),
) {
    val currentUser: User? get() = users.find { it.id == currentUserId }
    fun event(id: String) = events.find { it.id == id }
    fun registration(eventId: String, userId: String? = currentUserId) =
        registrations.find { it.eventId == eventId && it.userId == userId }
    fun confirmed(eventId: String) = registrations.filter {
        it.eventId == eventId && it.status == RegistrationStatus.CONFIRMED
    }
    fun waitlist(eventId: String) = registrations.filter {
        it.eventId == eventId && it.status == RegistrationStatus.WAITLISTED
    }.sortedBy { it.queueOrder }
    fun userName(id: String) = users.find { it.id == id }?.displayName ?: "Demo user"
}

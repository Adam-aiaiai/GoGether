package com.gogether.app.core.model

enum class Category(val label: String, val symbol: String) {
    SPORTS("Sports", "🏸"), STUDY("Study", "📚"), SOCIAL("Social", "🎲"),
    OUTDOOR("Outdoor", "🌿"), LANGUAGE("Language", "💬"), PHOTOGRAPHY("Photography", "📷"),
    BOARD_GAMES("Board Games", "♟"), READING("Reading", "📖"), OTHERS("Others", "✨")
}
enum class RegistrationStatus { CONFIRMED, WAITLISTED, CANCELLED, REJECTED }
enum class EventStatus { OPEN, CANCELLED, COMPLETED }
enum class ExperienceRequirement(val label: String) {
    NONE("No experience needed"), BASIC("Basic familiarity"), EXPERIENCED("Experienced participants")
}
enum class UserStatus { DEMO, ACCOUNT }
enum class Gender(val label: String, val symbol: String) {
    UNDISCLOSED("Prefer not to say", ""), WOMAN("Woman", "♀"), MAN("Man", "♂"), NON_BINARY("Non-binary", "⚧")
}
const val DefaultSignature = "Good moments start with getting together."
enum class CheckInStatus { DEMO_RECORDED, QR_RECORDED }
enum class NotificationType { PROMOTED, EVENT_CANCELLED, ANNOUNCEMENT, MEETING_UPDATED, EVENT_UPDATED, QUESTION_ASKED, QUESTION_ANSWERED }
enum class FeedbackStatus { SUBMITTED }

data class User(
    val id: String, val displayName: String, val interests: Set<Category>, val createdAt: Long,
    val status: UserStatus = UserStatus.DEMO,
    val email: String? = null,
    val avatarUri: String = "",
    val coverUri: String = "",
    val gender: Gender = Gender.UNDISCLOSED,
    val showGender: Boolean = false,
    val signature: String = "",
)

data class ProfileUpdate(val name: String, val interests: Set<Category>, val avatarUri: String = "",
    val coverUri: String = "", val gender: Gender = Gender.UNDISCLOSED,
    val showGender: Boolean = false, val signature: String = "")
data class Event(
    val id: String, val organizerId: String, val title: String, val category: Category,
    val description: String, val startAt: Long, val endAt: Long, val location: String,
    val city: String, val capacity: Int, val registrationDeadline: Long, val notes: String,
    val createdAt: Long, val updatedAt: Long, val status: EventStatus = EventStatus.OPEN,
    val demoCheckInOpen: Boolean = false,
    val experienceRequirement: ExperienceRequirement = ExperienceRequirement.NONE,
    val beginnerGuidance: Boolean = false,
    val requiredItems: String = "",
    val spareEquipment: String = "",
    val imageUri: String = "",
    val meetingAt: Long? = null,
    val meetingLocation: String? = null,
    val meetingNotes: String = "",
) {
    fun acceptsRegistration(now: Long) = status == EventStatus.OPEN &&
        now < registrationDeadline && now < startAt && now < endAt
    fun statusAt(now: Long) = if (status == EventStatus.OPEN && now >= endAt) EventStatus.COMPLETED else status
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
    val announcementId: String? = null,
    val questionId: String? = null,
)
data class Feedback(
    val id: String, val eventId: String, val userId: String, val rating: Int,
    val comment: String, val createdAt: Long, val status: FeedbackStatus = FeedbackStatus.SUBMITTED,
)
data class Announcement(val id: String, val eventId: String, val authorId: String,
    val content: String, val createdAt: Long)
/** A public event question with one official organizer answer, which may be revised. */
data class EventQuestion(
    val id: String, val eventId: String, val authorId: String,
    val content: String, val createdAt: Long,
    val answer: String? = null, val answeredBy: String? = null, val answeredAt: Long? = null,
)

data class MeetingUpdate(val meetingAt: Long, val location: String, val notes: String)
data class EventDraft(
    val title: String, val category: Category, val description: String, val startAt: Long,
    val location: String, val city: String, val capacity: Int,
    val registrationDeadline: Long, val notes: String = "",
    val endAt: Long = startAt + 2 * 60 * 60 * 1000L,
    val experienceRequirement: ExperienceRequirement = ExperienceRequirement.NONE,
    val beginnerGuidance: Boolean = false,
    val requiredItems: String = "",
    val spareEquipment: String = "",
    val imageUri: String = "",
    val meetingAt: Long? = null,
    val meetingLocation: String? = null,
    val meetingNotes: String = "",
)

/** One immutable snapshot is published for every transaction, including promotions and notifications. */
data class AppSnapshot(
    val users: List<User> = emptyList(), val currentUserId: String? = null,
    val events: List<Event> = emptyList(), val registrations: List<Registration> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(), val notifications: List<Notification> = emptyList(),
    val feedback: List<Feedback> = emptyList(),
    val sessionRestoring: Boolean = false,
    val sessionError: String? = null,
    val announcements: List<Announcement> = emptyList(),
    val questions: List<EventQuestion> = emptyList(),
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

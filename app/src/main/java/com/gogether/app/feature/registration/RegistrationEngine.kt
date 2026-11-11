package com.gogether.app.feature.registration

import com.gogether.app.core.model.*
import java.util.UUID

/** Android-free, deterministic with an injected clock. Called under the fake repository's mutex. */
class RegistrationEngine(
    initial: AppSnapshot = AppSnapshot(),
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    var snapshot: AppSnapshot = initial
        private set

    fun login(userId: String) {
        requireUser(userId)
        snapshot = snapshot.copy(currentUserId = userId)
    }

    fun logout() { snapshot = snapshot.copy(currentUserId = null) }

    fun registerUser(name: String, interests: Set<Category>): User {
        require(name.trim().length in 2..30) { "Use a nickname of 2–30 characters." }
        require(snapshot.users.none { it.displayName.equals(name.trim(), ignoreCase = true) }) {
            "This demo nickname already exists. Choose it on the login screen."
        }
        val user = User(newId(), name.trim(), interests.toSet(), now())
        snapshot = snapshot.copy(users = snapshot.users + user, currentUserId = user.id)
        return user
    }

    fun createEvent(actor: String, draft: EventDraft): Event {
        requireUser(actor)
        val time = now()
        validateDraft(draft, time)
        val event = Event(newId(), actor, draft.title.trim(), draft.category, draft.description.trim(),
            draft.startAt, draft.startAt + TWO_HOURS, draft.location.trim(), draft.city.trim(),
            draft.capacity, draft.registrationDeadline, draft.notes.trim(), time, time)
        val organizer = Registration(newId(), event.id, actor, RegistrationStatus.CONFIRMED,
            time, time, nextOrder())
        snapshot = snapshot.copy(events = snapshot.events + event,
            registrations = snapshot.registrations + organizer)
        return event
    }

    fun join(actor: String, eventId: String): Registration {
        requireUser(actor)
        val event = requireEvent(eventId)
        val time = now()
        require(event.status == EventStatus.OPEN) { "This event is ${event.status.name.lowercase()}." }
        require(time < event.startAt && time < event.endAt) { "This event has already started or ended." }
        require(time < event.registrationDeadline) { "Registration has closed." }
        val existing = snapshot.registration(eventId, actor)
        require(existing?.status !in ACTIVE) { "You already have a place or a waitlist entry." }
        val status = if (snapshot.confirmed(eventId).size < event.capacity)
            RegistrationStatus.CONFIRMED else RegistrationStatus.WAITLISTED
        val registration = Registration(existing?.id ?: newId(), eventId, actor, status,
            time, time, nextOrder())
        snapshot = snapshot.copy(registrations = snapshot.registrations.filterNot {
            it.eventId == eventId && it.userId == actor
        } + registration)
        return registration
    }

    fun cancelRegistration(actor: String, eventId: String) {
        requireUser(actor)
        val event = requireEvent(eventId)
        val time = now()
        require(actor != event.organizerId) { "The organizer keeps a place. Cancel the event from Manage instead." }
        require(event.status == EventStatus.OPEN && time < event.startAt) {
            "Registration cannot be changed after the event starts or closes."
        }
        val registration = snapshot.registration(eventId, actor)
        require(registration?.status in ACTIVE) { "There is no active registration to cancel." }
        val promoted = if (registration?.status == RegistrationStatus.CONFIRMED && event.acceptsRegistration(time))
            snapshot.waitlist(eventId).firstOrNull() else null
        val updated = snapshot.registrations.map {
            when (it.id) {
                registration?.id -> it.copy(status = RegistrationStatus.CANCELLED, updatedAt = time)
                promoted?.id -> it.copy(status = RegistrationStatus.CONFIRMED, updatedAt = time)
                else -> it
            }
        }
        val notifications = if (promoted != null) snapshot.notifications + Notification(
            newId(), promoted.userId, eventId, NotificationType.PROMOTED,
            "You're in! A place opened up: ${event.title}", time,
        ) else snapshot.notifications
        snapshot = snapshot.copy(registrations = updated, notifications = notifications)
    }

    fun cancelEvent(actor: String, eventId: String) {
        val event = requireOrganizer(actor, eventId)
        val time = now()
        require(event.status == EventStatus.OPEN && time < event.startAt) { "Only upcoming open events can be cancelled." }
        val recipients = snapshot.registrations.filter { it.eventId == eventId && it.status in ACTIVE }
        snapshot = snapshot.copy(
            events = snapshot.events.map { if (it.id == eventId) it.copy(
                status = EventStatus.CANCELLED, demoCheckInOpen = false, updatedAt = time) else it },
            registrations = snapshot.registrations.map {
                if (it.eventId == eventId && it.status in ACTIVE)
                    it.copy(status = RegistrationStatus.CANCELLED, updatedAt = time) else it
            },
            notifications = snapshot.notifications + recipients.map {
                Notification(newId(), it.userId, eventId, NotificationType.EVENT_CANCELLED,
                    "${event.title} has been cancelled by the organizer.", time)
            },
        )
    }

    fun setDemoCheckInOpen(actor: String, eventId: String, open: Boolean) {
        val event = requireOrganizer(actor, eventId)
        val time = now()
        require(event.status == EventStatus.OPEN && time < event.endAt) { "Check-in is closed for this event." }
        snapshot = snapshot.copy(events = snapshot.events.map {
            if (it.id == eventId) it.copy(demoCheckInOpen = open, updatedAt = time) else it
        })
    }

    fun checkIn(actor: String, eventId: String): CheckIn {
        requireUser(actor)
        val event = requireEvent(eventId)
        require(event.status == EventStatus.OPEN && now() < event.endAt && event.demoCheckInOpen) {
            "The organizer must open Demo check-in for an active event."
        }
        require(snapshot.registration(eventId, actor)?.status == RegistrationStatus.CONFIRMED) {
            "Only confirmed participants can check in."
        }
        require(snapshot.checkIns.none { it.eventId == eventId && it.userId == actor }) {
            "You have already checked in."
        }
        val checkIn = CheckIn(newId(), eventId, actor, now())
        snapshot = snapshot.copy(checkIns = snapshot.checkIns + checkIn)
        return checkIn
    }

    fun postAnnouncement(actor: String, eventId: String, content: String) {
        val event = requireOrganizer(actor, eventId)
        require(event.status == EventStatus.OPEN && now() < event.endAt) { "This event is closed." }
        require(content.trim().length in 1..500) { "Write an announcement of 1–500 characters." }
        val recipients = snapshot.registrations.filter { it.eventId == eventId && it.status in ACTIVE }
        val time = now()
        snapshot = snapshot.copy(notifications = snapshot.notifications + recipients.map {
            Notification(newId(), it.userId, eventId, NotificationType.ANNOUNCEMENT, content.trim(), time)
        })
    }

    fun markRead(actor: String, notificationId: String) {
        requireUser(actor)
        require(snapshot.notifications.any { it.id == notificationId && it.userId == actor }) { "Notification not found." }
        snapshot = snapshot.copy(notifications = snapshot.notifications.map {
            if (it.id == notificationId) it.copy(isRead = true) else it
        })
    }

    fun submitFeedback(actor: String, eventId: String, rating: Int, comment: String): Feedback {
        val event = requireEvent(eventId)
        requireUser(actor)
        require(event.status == EventStatus.COMPLETED ||
            (event.status == EventStatus.OPEN && now() >= event.endAt)) { "Feedback opens after the event ends." }
        require(snapshot.registration(eventId, actor)?.status == RegistrationStatus.CONFIRMED) { "Join this event to leave feedback." }
        require(rating in 1..5 && comment.length <= 500) { "Choose 1–5 stars and use up to 500 characters." }
        require(snapshot.feedback.none { it.eventId == eventId && it.userId == actor }) { "Feedback has already been submitted." }
        val feedback = Feedback(newId(), eventId, actor, rating, comment.trim(), now())
        snapshot = snapshot.copy(feedback = snapshot.feedback + feedback)
        return feedback
    }

    private fun requireUser(id: String) = requireNotNull(snapshot.users.find { it.id == id }) { "Choose a Demo user first." }
    private fun requireEvent(id: String) = requireNotNull(snapshot.event(id)) { "Event not found." }
    private fun requireOrganizer(actor: String, eventId: String): Event {
        requireUser(actor)
        return requireEvent(eventId).also { require(it.organizerId == actor) { "Only the organizer can do this." } }
    }
    private fun nextOrder() = (snapshot.registrations.maxOfOrNull { it.queueOrder } ?: 0) + 1

    companion object {
        const val TWO_HOURS = 2 * 60 * 60 * 1000L
        val ACTIVE = setOf(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
        fun validateDraft(draft: EventDraft, now: Long) {
            require(draft.title.trim().length in 3..80) { "Title must contain 3–80 characters." }
            require(draft.description.trim().length in 10..2000) { "Description must contain 10–2000 characters." }
            require(draft.location.trim().length in 3..150) { "Meeting location must contain 3–150 characters." }
            require(draft.city.trim().length in 2..60) { "City must contain 2–60 characters." }
            require(draft.capacity in 2..50) { "Capacity must be 2–50, including the organizer." }
            require(draft.startAt > now) { "Choose a future event date and time." }
            require(draft.registrationDeadline > now && draft.registrationDeadline <= draft.startAt) {
                "Registration deadline must be in the future and no later than the start."
            }
            require(draft.notes.length <= 1000) { "Additional notes must be at most 1,000 characters." }
        }
    }
}

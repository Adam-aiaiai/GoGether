package com.gogether.app.feature.registration

import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.CheckInEligibility
import com.gogether.app.feature.checkin.checkInEligibility
import java.util.UUID

/** Android-free, deterministic with an injected clock. Called under the fake repository's mutex. */
class RegistrationEngine(
    initial: AppSnapshot = AppSnapshot(),
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val registrationBlocked: (String, String) -> Boolean = { _, _ -> false },
    private val interactionBlocked: (String, String) -> Boolean = { _, _ -> false },
    private val contentDeleted: (String) -> Boolean = { false },
) {
    var snapshot: AppSnapshot = initial
        private set

    /** Repository holds the single mutation lock when restoring or applying account state. */
    internal fun replaceSnapshot(value: AppSnapshot) { snapshot = value }

    fun login(userId: String) {
        requireUser(userId)
        snapshot = snapshot.copy(currentUserId = userId)
    }

    fun logout() { snapshot = snapshot.copy(currentUserId = null) }

    fun registerUser(name: String, interests: Set<Category>): User {
        com.gogether.app.core.account.AccountRules.name(name)
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
        require(draft.meetingAt == null || draft.meetingAt > time) { "Choose a future meeting time." }
        val event = Event(newId(), actor, draft.title.trim(), draft.category, draft.description.trim(),
            draft.startAt, draft.endAt, draft.location.trim(), draft.city.trim(),
            draft.capacity, draft.registrationDeadline, draft.notes.trim(), time, time,
            experienceRequirement = draft.experienceRequirement,
            beginnerGuidance = draft.beginnerGuidance,
            requiredItems = draft.requiredItems.trim(),
            spareEquipment = draft.spareEquipment.trim(),
            imageUri = draft.imageUri.trim(), meetingAt = draft.meetingAt,
            meetingLocation = draft.meetingLocation?.trim(), meetingNotes = draft.meetingNotes.trim())
        val organizer = Registration(newId(), event.id, actor, RegistrationStatus.CONFIRMED,
            time, time, nextOrder())
        snapshot = snapshot.copy(events = snapshot.events + event,
            registrations = snapshot.registrations + organizer)
        return event
    }

    fun updateEvent(actor: String, eventId: String, draft: EventDraft): Event {
        val event = requireOrganizer(actor, eventId)
        val time = now()
        require(event.status == EventStatus.OPEN && time < event.startAt) {
            "Only upcoming open events can be edited."
        }
        validateDraft(draft, time, allowClosedDeadline = true)
        val meetingAt = draft.meetingAt ?: event.meetingAt
        require(meetingAt == null || meetingAt <= draft.endAt) {
            "Meeting time is after the new end time. Adjust the meeting time before saving."
        }
        require(meetingAt == event.meetingAt || meetingAt == null || meetingAt > time) { "Choose a future meeting time." }
        val confirmedCount = snapshot.confirmed(eventId).size
        require(draft.capacity >= confirmedCount) {
            "Capacity cannot be lower than the $confirmedCount confirmed participants."
        }
        val updatedEvent = event.copy(
            title = draft.title.trim(), category = draft.category,
            description = draft.description.trim(), startAt = draft.startAt, endAt = draft.endAt,
            location = draft.location.trim(), city = draft.city.trim(), capacity = draft.capacity,
            registrationDeadline = draft.registrationDeadline, notes = draft.notes.trim(),
            updatedAt = time, experienceRequirement = draft.experienceRequirement,
            beginnerGuidance = draft.beginnerGuidance, requiredItems = draft.requiredItems.trim(),
            spareEquipment = draft.spareEquipment.trim(), imageUri = draft.imageUri.trim(),
            meetingAt = meetingAt,
        )
        val changes = eventChanges(event, updatedEvent)
        val changeNotifications = if (changes.isEmpty()) emptyList() else {
            val type = if (meetingAt != event.meetingAt) NotificationType.MEETING_UPDATED else NotificationType.EVENT_UPDATED
            snapshot.registrations.filter { it.eventId == eventId && it.status in ACTIVE }
                .map { it.userId }.distinct().map { recipient ->
                    Notification(newId(), recipient, eventId, type,
                        "Activity updated: ${updatedEvent.title}\n${changes.joinToString("\n")}", time)
                }
        }
        val promotable = if (updatedEvent.acceptsRegistration(time)) {
            snapshot.waitlist(eventId).filterNot { registrationBlocked(actor, it.userId) }.take((draft.capacity - confirmedCount).coerceAtLeast(0))
        } else emptyList()
        val promotedIds = promotable.mapTo(mutableSetOf()) { it.id }
        snapshot = snapshot.copy(
            events = snapshot.events.map { if (it.id == eventId) updatedEvent else it },
            registrations = snapshot.registrations.map {
                if (it.id in promotedIds) it.copy(status = RegistrationStatus.CONFIRMED, updatedAt = time) else it
            },
            notifications = snapshot.notifications + promotable.map {
                Notification(newId(), it.userId, eventId, NotificationType.PROMOTED,
                    "You're in! More places are available: ${updatedEvent.title}", time)
            } + changeNotifications,
        )
        return updatedEvent
    }

    fun join(actor: String, eventId: String): Registration {
        requireUser(actor)
        val event = requireEvent(eventId)
        val time = now()
        require(event.status == EventStatus.OPEN) { "This event is ${event.status.name.lowercase()}." }
        require(time < event.startAt && time < event.endAt) { "This event has already started or ended." }
        require(time < event.registrationDeadline) { "Registration has closed." }
        require(!registrationBlocked(event.organizerId, actor)) { "The organizer has restricted new registrations from this account." }
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
            snapshot.waitlist(eventId).firstOrNull { !registrationBlocked(event.organizerId, it.userId) } else null
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
                status = EventStatus.CANCELLED, updatedAt = time) else it },
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

    fun checkIn(actor: String, eventId: String): CheckIn {
        requireUser(actor)
        val event = requireEvent(eventId)
        // Read the clock once, under the repository mutex, and validate the latest snapshot.
        val time = now()
        val eligibility = checkInEligibility(event, snapshot, time, actor)
        require(eligibility == CheckInEligibility.READY) { eligibility.message }
        require(actor != event.organizerId) { "Confirm your arrival when showing the check-in code." }
        val checkIn = CheckIn(newId(), eventId, actor, time,
            registrationKey = snapshot.registration(eventId, actor)!!.attendanceKey)
        snapshot = snapshot.copy(checkIns = snapshot.checkIns + checkIn)
        return checkIn
    }

    fun postAnnouncement(actor: String, eventId: String, content: String) {
        val event = requireOrganizer(actor, eventId)
        require(event.status == EventStatus.OPEN && now() < event.endAt) { "This event is closed." }
        require(content.trim().length in 1..100) { "Write an announcement of 1–100 characters." }
        val recipients = snapshot.registrations.filter { it.eventId == eventId && it.status in ACTIVE }.map { it.userId }.distinct()
        val time = now()
        val announcement = Announcement(newId(), eventId, actor, content.trim(), time)
        snapshot = snapshot.copy(
            announcements = snapshot.announcements + announcement,
            notifications = snapshot.notifications + recipients.map {
            Notification(newId(), it, eventId, NotificationType.ANNOUNCEMENT, content.trim(), time, announcementId = announcement.id)
        })
    }

    fun askQuestion(actor: String, eventId: String, content: String) {
        requireUser(actor)
        val event = requireEvent(eventId)
        val time = now()
        require(event.statusAt(time) == EventStatus.OPEN) { "Questions are closed for this event." }
        require(actor != event.organizerId) { "Use an announcement to share information as the organizer." }
        require(!interactionBlocked(actor, event.organizerId)) { "This interaction is blocked." }
        val text = content.trim()
        require(text.length in 1..100) { "Write a question of 1–100 characters." }
        require(snapshot.questions.none { it.eventId == eventId && it.authorId == actor && it.content == text }) {
            "You have already posted this question. Check the questions below."
        }
        val question = EventQuestion(newId(), eventId, actor, text, time)
        snapshot = snapshot.copy(questions = snapshot.questions + question,
            notifications = snapshot.notifications + Notification(newId(), event.organizerId, eventId,
                NotificationType.QUESTION_ASKED, "New question about ${event.title}: $text", time, questionId = question.id, contentId = question.id, rootId = question.id))
    }

    fun answerQuestion(actor: String, questionId: String, content: String) {
        val question = requireNotNull(snapshot.questions.find { it.id == questionId }) { "Question not found." }
        val event = requireOrganizer(actor, question.eventId)
        require(!interactionBlocked(actor, question.authorId) && !contentDeleted(question.id)) { "This conversation is unavailable." }
        val time = now()
        require(event.statusAt(time) == EventStatus.OPEN) { "Replies are closed for this event." }
        val text = content.trim()
        require(text.length in 1..100) { "Write an answer of 1–100 characters." }
        if (question.answer == text) return
        val history = question.replyHistory.ifEmpty { question.answer?.let {
            listOf(QuestionReply("answer-${question.id}", question.answeredBy ?: event.organizerId, it, question.answeredAt ?: question.createdAt))
        } ?: emptyList() }
        val reply = QuestionReply(if(history.isEmpty()) "answer-${question.id}" else newId(), actor, text, time)
        val answered = question.copy(answer = text, answeredBy = actor, answeredAt = time, replyHistory = history + reply)
        snapshot = snapshot.copy(questions = snapshot.questions.map { if (it.id == questionId) answered else it },
            notifications = snapshot.notifications + Notification(newId(), question.authorId, event.id,
                NotificationType.QUESTION_ANSWERED,
                "${if (question.answer == null) "Organizer answered" else "Organizer updated an answer"}: ${event.title}\n$text",
                time, questionId = question.id, contentId = reply.id, rootId = question.id))
    }

    fun updateMeeting(actor: String, eventId: String, update: MeetingUpdate) {
        val event = requireOrganizer(actor, eventId)
        val time = now()
        require(event.status == EventStatus.OPEN && time < event.endAt) { "This event is closed." }
        require(update.meetingAt > time && update.meetingAt <= event.endAt) { "Choose a future meeting time before the event ends." }
        require(update.location.trim().length in 3..150) { "Meeting location must contain 3–150 characters." }
        require(update.notes.length <= 100) { "Use up to 100 characters for meeting notes." }
        snapshot = snapshot.copy(events = snapshot.events.map {
            if (it.id == eventId) it.copy(meetingAt = update.meetingAt, meetingLocation = update.location.trim(),
                meetingNotes = update.notes.trim(), updatedAt = time) else it
        }, notifications = snapshot.notifications + meetingNotifications(event, time))
    }

    private fun meetingNotifications(event: Event, time: Long) = snapshot.registrations
        .filter { it.eventId == event.id && it.status in ACTIVE }.map { it.userId }.distinct().map {
            Notification(newId(), it, event.id, NotificationType.MEETING_UPDATED,
                "Meeting information updated: ${event.title}. Open the event for the latest time and location.", time)
        }

    fun markAllRead(actor: String) {
        requireUser(actor)
        snapshot = snapshot.copy(notifications = snapshot.notifications.map {
            if (it.userId == actor && !it.isRead) it.copy(isRead = true) else it
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
        require(snapshot.activeCheckIn(eventId, actor) != null) { "Check in at this event before leaving a review." }
        require(rating in 1..5 && comment.length <= 100) { "Choose 1–5 stars and use up to 100 characters." }
        require(snapshot.feedback.none { it.eventId == eventId && it.userId == actor && !contentDeleted(com.gogether.app.feature.social.LocalSocialStore.legacyReviewId(it.id)) }) { "Feedback has already been submitted." }
        val feedback = Feedback(newId(), eventId, actor, rating, comment.trim(), now())
        val contentId = com.gogether.app.feature.social.LocalSocialStore.legacyReviewId(feedback.id)
        val notices = if(actor == event.organizerId || interactionBlocked(actor,event.organizerId)) emptyList() else listOf(
            Notification(newId(),event.organizerId,event.id,NotificationType.REVIEW_POSTED,"Reviewed your event.",feedback.createdAt,contentId=contentId,rootId=contentId))
        snapshot = snapshot.copy(feedback = snapshot.feedback + feedback, notifications = snapshot.notifications + notices)
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
        fun validateDraft(draft: EventDraft, now: Long, allowClosedDeadline: Boolean = false) {
            require(draft.title.trim().length in 3..80) { "Title must contain 3–80 characters." }
            require(draft.description.trim().length in 10..100) { "Description must contain 10–100 characters." }
            require(draft.location.trim().length in 3..150) { "Meeting location must contain 3–150 characters." }
            require(draft.city.trim().length in 2..60) { "City must contain 2–60 characters." }
            require(draft.capacity in 2..50) { "Capacity must be 2–50, including the organizer." }
            require(draft.startAt > now) { "Choose a future event date and time." }
            require(draft.endAt > draft.startAt) { "End time must be after the start time." }
            require(draft.endAt - draft.startAt <= 24 * 60 * 60 * 1000L) {
                "An event can last at most 24 hours."
            }
            require((allowClosedDeadline || draft.registrationDeadline > now) &&
                draft.registrationDeadline <= draft.startAt) {
                "Registration deadline must be no later than the start${if (allowClosedDeadline) "." else " and must be in the future."}"
            }
            require(draft.notes.length <= 100) { "Additional notes must be at most 100 characters." }
            require(draft.requiredItems.length <= 100) { "Items to bring must be at most 100 characters." }
            require(draft.spareEquipment.length <= 100) { "Spare equipment must be at most 100 characters." }
            require(draft.imageUri.length <= 2_000) { "Event image reference is too long." }
            require(draft.meetingAt == null || draft.meetingAt <= draft.endAt) {
                "Meeting time is after the new end time. Adjust the meeting time before saving."
            }
            require(draft.meetingLocation == null || draft.meetingLocation.trim().length in 3..150) { "Meeting location must contain 3–150 characters." }
            require(draft.meetingNotes.length <= 100) { "Use up to 100 characters for meeting notes." }
        }
    }
}

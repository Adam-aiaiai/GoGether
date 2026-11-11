package com.gogether.app.core.data

import com.gogether.app.core.model.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeGogetherRepository(private val clock: Clock = Clock.systemUTC()) : GogetherRepository {
    private val now get() = clock.instant()
    private val _users = MutableStateFlow(demoUsers())
    override val users: StateFlow<List<User>> = _users.asStateFlow()
    private val _events = MutableStateFlow(demoEvents(now))
    override val events: StateFlow<List<Event>> = _events.asStateFlow()
    private val _registrations = MutableStateFlow(seedRegistrations(_events.value, now))
    override val registrations: StateFlow<List<Registration>> = _registrations.asStateFlow()
    private val _checkIns = MutableStateFlow<List<CheckIn>>(emptyList())
    override val checkIns: StateFlow<List<CheckIn>> = _checkIns.asStateFlow()
    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    override fun login(userId: String) { _currentUser.value = users.value.firstOrNull { it.id == userId } }
    override fun logout() { _currentUser.value = null }

    @Synchronized
    override fun createEvent(draft: EventDraft): Result<Event> {
        val user = currentUser.value ?: return Result.failure(IllegalStateException("Log in first"))
        val errors = EventValidator.validate(draft, clock)
        if (errors.isNotEmpty()) return Result.failure(IllegalArgumentException(errors.values.joinToString(". ")))
        val event = Event(
            id = UUID.randomUUID().toString(), creatorId = user.id, title = draft.title.trim(),
            category = draft.category.trim(), description = draft.description.trim(), startAt = draft.startAt,
            meetingLocation = draft.meetingLocation.trim(), maxParticipants = draft.maxParticipants,
            registrationDeadline = draft.registrationDeadline, additionalNotes = draft.additionalNotes.trim(), createdAt = now,
        )
        _events.update { listOf(event) + it }
        _registrations.update { it + confirmedRegistration(event.id, user.id, now) }
        return Result.success(event)
    }

    @Synchronized
    override fun joinEvent(eventId: String): RegistrationOutcome {
        val user = currentUser.value ?: return RegistrationOutcome.Rejected("Log in first")
        val event = events.value.firstOrNull { it.id == eventId } ?: return RegistrationOutcome.Rejected("Event not found")
        if (event.status != EventStatus.OPEN) return RegistrationOutcome.Rejected("Event is not open")
        if (!now.isBefore(event.registrationDeadline)) return RegistrationOutcome.Rejected("Registration deadline has passed")
        if (!now.isBefore(event.startAt)) return RegistrationOutcome.Rejected("Event has ended")
        val duplicate = registrations.value.any { it.eventId == eventId && it.userId == user.id && it.status in activeStatuses }
        if (duplicate) return RegistrationOutcome.Rejected("You are already registered")
        val confirmed = registrations.value.count { it.eventId == eventId && it.status == RegistrationStatus.CONFIRMED }
        val status = if (confirmed < event.maxParticipants) RegistrationStatus.CONFIRMED else RegistrationStatus.WAITLISTED
        val registration = Registration(UUID.randomUUID().toString(), eventId, user.id, status, now, now)
        _registrations.update { it + registration }
        return if (status == RegistrationStatus.CONFIRMED) RegistrationOutcome.Joined(registration) else RegistrationOutcome.Waitlisted(registration)
    }

    @Synchronized
    override fun cancelRegistration(eventId: String): Result<Unit> {
        val user = currentUser.value ?: return Result.failure(IllegalStateException("Log in first"))
        val target = registrations.value.firstOrNull { it.eventId == eventId && it.userId == user.id && it.status in activeStatuses }
            ?: return Result.failure(IllegalStateException("No active registration"))
        val cancelledAt = now
        _registrations.update { list -> list.map { if (it.id == target.id) it.copy(status = RegistrationStatus.CANCELLED, updatedAt = cancelledAt) else it } }
        if (target.status == RegistrationStatus.CONFIRMED) promoteFirstWaitlisted(eventId)
        return Result.success(Unit)
    }

    @Synchronized
    override fun cancelEvent(eventId: String): Result<Unit> {
        val user = currentUser.value ?: return Result.failure(IllegalStateException("Log in first"))
        val event = events.value.firstOrNull { it.id == eventId } ?: return Result.failure(IllegalArgumentException("Event not found"))
        if (event.creatorId != user.id) return Result.failure(IllegalAccessException("Only the organizer can cancel"))
        _events.update { list -> list.map { if (it.id == eventId) it.copy(status = EventStatus.CANCELLED) else it } }
        _registrations.update { list -> list.map { if (it.eventId == eventId && it.status in activeStatuses) it.copy(status = RegistrationStatus.CANCELLED, updatedAt = now) else it } }
        return Result.success(Unit)
    }

    @Synchronized
    override fun checkIn(eventId: String): Result<CheckIn> {
        val user = currentUser.value ?: return Result.failure(IllegalStateException("Log in first"))
        val confirmed = registrations.value.any { it.eventId == eventId && it.userId == user.id && it.status == RegistrationStatus.CONFIRMED }
        if (!confirmed) return Result.failure(IllegalStateException("A confirmed registration is required"))
        _checkIns.value.firstOrNull { it.eventId == eventId && it.userId == user.id }?.let { return Result.success(it) }
        val record = CheckIn(UUID.randomUUID().toString(), eventId, user.id, now)
        _checkIns.update { it + record }
        return Result.success(record)
    }

    private fun promoteFirstWaitlisted(eventId: String) {
        val event = events.value.firstOrNull { it.id == eventId } ?: return
        if (event.status != EventStatus.OPEN || !now.isBefore(event.registrationDeadline) || !now.isBefore(event.startAt)) return
        val next = registrations.value.filter { it.eventId == eventId && it.status == RegistrationStatus.WAITLISTED }.minByOrNull { it.createdAt } ?: return
        _registrations.update { list -> list.map { if (it.id == next.id) it.copy(status = RegistrationStatus.CONFIRMED, updatedAt = now) else it } }
    }

    companion object {
        private val activeStatuses = setOf(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
        private fun confirmedRegistration(eventId: String, userId: String, at: Instant) =
            Registration(UUID.randomUUID().toString(), eventId, userId, RegistrationStatus.CONFIRMED, at, at)

        private fun demoUsers() = listOf(
            User("u-alex", "Alex · Organizer", listOf("Badminton", "Hiking"), true),
            User("u-mia", "Mia · Participant", listOf("Board Games", "Books")),
            User("u-noah", "Noah · Waitlist demo", listOf("Photography", "Hiking")),
            User("u-zoe", "Zoe · Participant", listOf("Badminton", "Photography")),
        )

        private fun demoEvents(at: Instant): List<Event> {
            fun event(id: String, title: String, category: String, days: Long, capacity: Int, location: String, creator: String = "u-alex") = Event(
                id, creator, title, category, "A friendly $category meetup. Beginners are welcome.",
                at.plus(Duration.ofDays(days)), location, capacity, at.plus(Duration.ofDays(days - 1)),
                "Please arrive 10 minutes early.", EventStatus.OPEN, at,
            )
            return listOf(
                event("event-badminton", "Weekend Badminton", "Badminton", 7, 4, "Riverside Sports Hall"),
                event("event-boardgames", "Cozy Board Game Night", "Board Games", 10, 2, "Maple Cafe"),
                event("event-photo", "Golden Hour Photo Walk", "Photography", 14, 8, "Old Town Gate"),
                event("event-books", "Monthly Book Club", "Books", 18, 6, "Central Library"),
                event("event-hiking", "Lakeside Hiking Trail", "Hiking", 21, 10, "North Trail Entrance"),
            )
        }

        private fun seedRegistrations(events: List<Event>, at: Instant): List<Registration> = buildList {
            events.forEach { add(confirmedRegistration(it.id, it.creatorId, at)) }
            add(confirmedRegistration("event-boardgames", "u-zoe", at.plusSeconds(1)))
        }
    }
}

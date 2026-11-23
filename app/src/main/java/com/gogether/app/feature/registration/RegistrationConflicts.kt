package com.gogether.app.feature.registration

import com.gogether.app.core.model.*

/** Advisory only: existing join eligibility, capacity and FIFO promotion rules stay authoritative. */
fun AppSnapshot.registrationConflicts(event: Event, now: Long): List<Event> {
    val actor = currentUserId ?: return emptyList()
    val activeEventIds = registrations.filter {
        it.userId == actor && it.status in setOf(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
    }.mapTo(mutableSetOf()) { it.eventId }
    return events.filter {
        it.id != event.id && (it.id in activeEventIds || it.organizerId == actor) &&
            it.statusAt(now) == EventStatus.OPEN &&
            it.startAt < event.endAt && event.startAt < it.endAt
    }.sortedWith(compareBy<Event> { it.startAt }.thenBy { it.id })
}

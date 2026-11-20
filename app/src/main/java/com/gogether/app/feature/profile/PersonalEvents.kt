package com.gogether.app.feature.profile

import com.gogether.app.core.model.*

enum class PersonalEventTab(val label: String) {
    JOINED("Joined"), WAITLISTED("Waitlisted"), CREATED("Created"), HISTORY("History")
}

/** Uses B's event status and C/E's canonical records; it never owns a second attendee list. */
fun personalEvents(state: AppSnapshot, tab: PersonalEventTab, now: Long): List<Event> {
    val userId = state.currentUserId ?: return emptyList()
    val matching = state.events.filter { event ->
        val registration = state.registration(event.id, userId)
        val active = event.statusAt(now) == EventStatus.OPEN
        when (tab) {
            PersonalEventTab.JOINED -> active && registration?.status == RegistrationStatus.CONFIRMED
            PersonalEventTab.WAITLISTED -> active && registration?.status == RegistrationStatus.WAITLISTED
            PersonalEventTab.CREATED -> event.organizerId == userId
            PersonalEventTab.HISTORY -> (event.organizerId == userId || registration != null) &&
                (!active || registration?.status in setOf(RegistrationStatus.CANCELLED, RegistrationStatus.REJECTED))
        }
    }
    return if (tab == PersonalEventTab.JOINED || tab == PersonalEventTab.WAITLISTED)
        matching.sortedBy { it.startAt } else matching.sortedByDescending { it.startAt }
}

fun participationSummary(state: AppSnapshot, event: Event, now: Long): String {
    val userId = state.currentUserId ?: return ""
    val registration = state.registration(event.id, userId)
    val attendance = state.checkIns.find { it.eventId == event.id && it.userId == userId }
    val feedback = state.feedback.find { it.eventId == event.id && it.userId == userId }
    val status = when {
        event.status == EventStatus.CANCELLED -> "Cancelled by organizer"
        registration?.status == RegistrationStatus.CANCELLED -> "You withdrew"
        registration?.status == RegistrationStatus.REJECTED -> "Registration declined"
        event.statusAt(now) == EventStatus.COMPLETED -> "Ended"
        registration?.status == RegistrationStatus.WAITLISTED -> "Waitlist position ${state.waitlist(event.id).indexOfFirst { it.userId == userId } + 1}"
        now >= event.startAt -> "In progress"
        else -> "Upcoming"
    }
    return buildList {
        add(status)
        if (event.organizerId == userId) add("Organizer")
        if (attendance != null) add(if (attendance.status == CheckInStatus.DEMO_RECORDED) "Demo check-in recorded" else "Checked in")
        if (feedback != null) add("Your rating: ${feedback.rating}/5")
    }.joinToString(" · ")
}

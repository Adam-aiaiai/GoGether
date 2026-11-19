package com.gogether.app.feature.checkin

import com.gogether.app.core.model.AppSnapshot
import com.gogether.app.core.model.Event
import com.gogether.app.core.model.RegistrationStatus

enum class CheckInEligibility(val title: String, val message: String) {
    SIGN_IN_REQUIRED("Sign in required", "Sign in to check your registration and check in."),
    EVENT_CANCELLED("Event cancelled", "Check-in and feedback are unavailable."),
    EVENT_ENDED("Event ended", "Check-in is closed because this event has ended."),
    ALREADY_CHECKED_IN("You're checked in · Demo", "You have already checked in."),
    WAITLISTED("Waitlisted", "Only confirmed participants can check in. A waitlist place is not eligible."),
    REGISTRATION_CANCELLED("Registration cancelled", "Your registration is cancelled. You cannot check in."),
    REGISTRATION_REJECTED("Registration not accepted", "Only confirmed participants can check in."),
    NOT_REGISTERED("Confirmed place required", "Register and obtain a confirmed place before checking in."),
    NOT_STARTED("Check-in has not started", "Check-in opens 30 minutes before the event starts."),
    EXPIRED("Check-in window closed", "Check-in closes 30 minutes after the event starts."),
    READY("Ready to check in", "Your place is confirmed and the check-in window is open."),
}

/** Evaluates the current snapshot without changing registrations, attendance or feedback. */
fun checkInEligibility(event: Event, state: AppSnapshot, now: Long,
                       userId: String? = state.currentUserId): CheckInEligibility {
    if (userId == null || state.users.none { it.id == userId }) return CheckInEligibility.SIGN_IN_REQUIRED
    val window = CheckInWindow.state(event, now)
    if (window == CheckInWindowState.EVENT_CANCELLED) return CheckInEligibility.EVENT_CANCELLED
    if (window == CheckInWindowState.EVENT_ENDED) return CheckInEligibility.EVENT_ENDED
    if (state.checkIns.any { it.eventId == event.id && it.userId == userId }) {
        return CheckInEligibility.ALREADY_CHECKED_IN
    }
    when (state.registration(event.id, userId)?.status) {
        RegistrationStatus.CONFIRMED -> Unit
        RegistrationStatus.WAITLISTED -> return CheckInEligibility.WAITLISTED
        RegistrationStatus.CANCELLED -> return CheckInEligibility.REGISTRATION_CANCELLED
        RegistrationStatus.REJECTED -> return CheckInEligibility.REGISTRATION_REJECTED
        null -> return CheckInEligibility.NOT_REGISTERED
    }
    return when (window) {
        CheckInWindowState.NOT_STARTED -> CheckInEligibility.NOT_STARTED
        CheckInWindowState.EXPIRED -> CheckInEligibility.EXPIRED
        else -> CheckInEligibility.READY
    }
}

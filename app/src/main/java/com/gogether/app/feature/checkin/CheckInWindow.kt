package com.gogether.app.feature.checkin

import com.gogether.app.core.model.Event
import com.gogether.app.core.model.EventStatus

enum class CheckInWindowState { NOT_STARTED, OPEN, EXPIRED, EVENT_CANCELLED, EVENT_ENDED }

/** Shared by the page and submission validation. Both 30-minute endpoints are inclusive. */
object CheckInWindow {
    const val HALF_HOUR = 30 * 60 * 1000L

    fun opensAt(event: Event): Long = event.startAt - HALF_HOUR
    fun closesAt(event: Event): Long = event.startAt + HALF_HOUR

    fun state(event: Event, now: Long): CheckInWindowState = when {
        event.status == EventStatus.CANCELLED -> CheckInWindowState.EVENT_CANCELLED
        event.status == EventStatus.COMPLETED || now >= event.endAt -> CheckInWindowState.EVENT_ENDED
        now < opensAt(event) -> CheckInWindowState.NOT_STARTED
        now > closesAt(event) -> CheckInWindowState.EXPIRED
        else -> CheckInWindowState.OPEN
    }
}

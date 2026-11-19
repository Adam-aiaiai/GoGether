package com.gogether.app

import com.gogether.app.core.data.DemoData
import com.gogether.app.core.model.EventStatus
import com.gogether.app.feature.checkin.CheckInWindow
import com.gogether.app.feature.checkin.CheckInWindowState
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckInWindowTest {
    private val event = DemoData.create(1_800_000_000_000L).events.first()

    @Test fun windowIncludesBothEndpointsAndRejectsOneMillisecondOutside() {
        val cases = listOf(
            event.startAt - 1_800_001 to CheckInWindowState.NOT_STARTED,
            event.startAt - 1_800_000 to CheckInWindowState.OPEN,
            event.startAt to CheckInWindowState.OPEN,
            event.startAt + 1_800_000 to CheckInWindowState.OPEN,
            event.startAt + 1_800_001 to CheckInWindowState.EXPIRED,
        )
        cases.forEach { (time, expected) -> assertEquals(expected, CheckInWindow.state(event, time)) }
    }

    @Test fun cancelledAndCompletedEventsAreClosedInsideTheWindow() {
        assertEquals(CheckInWindowState.EVENT_CANCELLED,
            CheckInWindow.state(event.copy(status = EventStatus.CANCELLED), event.startAt))
        assertEquals(CheckInWindowState.EVENT_ENDED,
            CheckInWindow.state(event.copy(status = EventStatus.COMPLETED), event.startAt))
    }

    @Test fun existingEndTimeCutoffStillAppliesForShortEvents() {
        val shortEvent = event.copy(endAt = event.startAt + 600_000)
        assertEquals(CheckInWindowState.OPEN, CheckInWindow.state(shortEvent, shortEvent.endAt - 1))
        assertEquals(CheckInWindowState.EVENT_ENDED, CheckInWindow.state(shortEvent, shortEvent.endAt))
    }

    @Test fun windowCanCrossMidnightWithoutCalendarSpecialCases() {
        val start = java.time.Instant.parse("2026-11-15T00:10:00Z").toEpochMilli()
        val overnight = event.copy(startAt = start, endAt = start + 7_200_000)
        assertEquals(java.time.Instant.parse("2026-11-14T23:40:00Z").toEpochMilli(),
            CheckInWindow.opensAt(overnight))
        assertEquals(java.time.Instant.parse("2026-11-15T00:40:00Z").toEpochMilli(),
            CheckInWindow.closesAt(overnight))
    }
}

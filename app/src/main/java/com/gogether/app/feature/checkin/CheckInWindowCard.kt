package com.gogether.app.feature.checkin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.gogether.app.core.model.Event
import com.gogether.app.core.ui.InfoCard
import com.gogether.app.core.ui.displayTime

@Composable
fun CheckInWindowCard(event: Event, now: Long) {
    val status = when (CheckInWindow.state(event, now)) {
        CheckInWindowState.NOT_STARTED -> "Not open yet"
        CheckInWindowState.OPEN -> "Open now"
        CheckInWindowState.EXPIRED -> "Window closed"
        CheckInWindowState.EVENT_CANCELLED -> "Closed · event cancelled"
        CheckInWindowState.EVENT_ENDED -> "Closed · event ended"
    }
    InfoCard("Check-in window · $status",
        "${displayTime(CheckInWindow.opensAt(event))}\nTo ${displayTime(CheckInWindow.closesAt(event))}\n" +
            "From 30 minutes before to 30 minutes after the start, including both endpoints. " +
            "Check-in stops if the event ends or is cancelled.",
        Modifier.testTag("checkin_window"))
}

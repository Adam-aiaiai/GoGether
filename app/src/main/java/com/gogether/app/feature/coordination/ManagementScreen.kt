package com.gogether.app.feature.coordination

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.event.MeetingMapButton
import com.gogether.app.feature.checkin.CheckInWindowCard

@Composable
fun ManagementScreen(event: Event, state: AppSnapshot, now: Long, busy: Boolean,
                     cancel: () -> Unit, announce: (String) -> Unit, attendance: (() -> Unit)? = null) {
    var confirmation by rememberSaveable { mutableStateOf(false) }
    var announcement by rememberSaveable { mutableStateOf("") }
    if (event.organizerId != state.currentUserId) {
        Page { InfoCard("Organizer access", "Only this event's organizer can manage it.") }
        return
    }
    Page {
        SectionTitle("Manage your plan", event.title)
        StatusPill(event.status.name.lowercase())
        InfoCard("At a glance", "${state.confirmed(event.id).size} / ${event.capacity} confirmed\n${state.waitlist(event.id).size} waitlisted\n${state.checkIns.count { it.eventId == event.id }} checked in")
        attendance?.let { open -> OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth().testTag("manage_attendance")) {
            Text("View check-in and attendance")
        } }
        InfoCard("Meeting information", "${displayTime(event.startAt)}\n${event.city} · ${event.location}\n${event.notes}")
        MeetingMapButton("${event.city} ${event.location}")
        CheckInWindowCard(event, now)
        Text("Confirmed participants", style = MaterialTheme.typography.titleLarge)
        state.confirmed(event.id).forEach { registration ->
            val checked = state.checkIns.any { it.eventId == event.id && it.userId == registration.userId }
            InfoCard(state.userName(registration.userId),
                (if (registration.userId == event.organizerId) "Organizer · " else "") + if (checked) "Checked in" else "Confirmed")
        }
        Text("Waitlist · first come, first served", style = MaterialTheme.typography.titleLarge)
        val queue = state.waitlist(event.id)
        if (queue.isEmpty()) Text("Nobody is waiting.")
        queue.forEachIndexed { index, registration -> Text("${index + 1}. ${state.userName(registration.userId)}") }
        if (event.status == EventStatus.OPEN && now < event.endAt) {
            InfoCard("Automatic check-in", "Confirmed participants can check in during the window without a manual switch. Cancelling the event also stops check-in.")
            OutlinedTextField(announcement, { announcement = it }, label = { Text("Organizer announcement") },
                supportingText = { Text("Local in-app inbox · up to 500 characters") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { announce(announcement); announcement = "" }, enabled = !busy && announcement.trim().length in 1..500,
                modifier = Modifier.fillMaxWidth()) { Text("Post announcement") }
            if (now < event.startAt) OutlinedButton(onClick = { confirmation = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("cancel_event")) { Text("Cancel event") }
        }
    }
    if (confirmation) ConfirmDialog("Cancel this event?", "All active registrations will be cancelled. Everyone receives an in-app Demo notification.",
        "Yes, cancel event", { confirmation = false }, cancel)
}

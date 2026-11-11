package com.gogether.app.feature.event

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*

@Composable
fun EventDetailScreen(event: Event, state: AppSnapshot, now: Long, busy: Boolean,
                      onJoin: () -> Unit, onCancel: () -> Unit, onManage: () -> Unit, onCheckIn: () -> Unit) {
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val registration = state.registration(event.id)
    val organizer = state.currentUserId == event.organizerId
    val active = registration?.status == RegistrationStatus.CONFIRMED || registration?.status == RegistrationStatus.WAITLISTED
    Page {
        StatusPill(event.category.label)
        SectionTitle(event.title, "Hosted by ${state.userName(event.organizerId)}")
        Text(event.description, style = MaterialTheme.typography.bodyLarge)
        InfoCard("The plan", "${displayTime(event.startAt)}\nEnds ${displayTime(event.endAt)}\n${event.city} · ${event.location}")
        MeetingMapButton("${event.city} ${event.location}")
        InfoCard("Who's coming", "${state.confirmed(event.id).size} / ${event.capacity} confirmed (includes organizer)\n${state.waitlist(event.id).size} on the waitlist")
        InfoCard("Registration", "Closes ${displayTime(event.registrationDeadline)}\nEvent: ${event.status.name.lowercase()}\nYour status: ${registration?.status?.name?.lowercase() ?: "not registered"}")
        if (registration?.status == RegistrationStatus.WAITLISTED) {
            Text("Your waitlist position: ${state.waitlist(event.id).indexOfFirst { it.userId == state.currentUserId } + 1}",
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("waitlist_position"))
        }
        if (event.notes.isNotBlank()) InfoCard("Good to know", event.notes)
        val notices = state.notifications.filter { it.eventId == event.id && it.userId == state.currentUserId && it.type == NotificationType.ANNOUNCEMENT }
        notices.takeLast(3).forEach { InfoCard("Organizer update · ${displayTime(it.createdAt)}", it.content) }
        when {
            organizer -> Button(onClick = onManage, modifier = Modifier.fillMaxWidth().testTag("manage_event")) { Text("Manage event") }
            active && event.status == EventStatus.OPEN && now < event.startAt -> OutlinedButton(
                onClick = { confirmCancel = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("cancel_registration")) { Text("Cancel Registration") }
            !active && event.acceptsRegistration(now) -> Button(onClick = onJoin, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("join_event")) {
                Text(if (state.confirmed(event.id).size >= event.capacity) "Join Waitlist" else "Join Event")
            }
            else -> InfoCard("Registration closed", "New places and waitlist promotions stop at the deadline or when the event starts or closes.")
        }
        if (registration?.status == RegistrationStatus.CONFIRMED && event.status != EventStatus.CANCELLED) {
            Button(onClick = onCheckIn, modifier = Modifier.fillMaxWidth().testTag("open_checkin")) {
                Text(if (event.status == EventStatus.COMPLETED || now >= event.endAt) "Leave feedback" else "Check-in & meeting")
            }
        }
    }
    if (confirmCancel) ConfirmDialog("Cancel your registration?", "Your place may go to the first person on the waitlist.",
        "Yes, cancel registration", { confirmCancel = false }, onCancel)
}

@Composable
fun MeetingMapButton(location: String) {
    val context = LocalContext.current
    var error by remember { mutableStateOf(false) }
    OutlinedButton(onClick = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(location)}".toUri()))
        } catch (_: ActivityNotFoundException) { error = true }
    }, modifier = Modifier.fillMaxWidth()) { Text("Open meeting location in Maps") }
    if (error) Text("No maps app is installed. Use the meeting address shown above.", color = MaterialTheme.colorScheme.error)
}

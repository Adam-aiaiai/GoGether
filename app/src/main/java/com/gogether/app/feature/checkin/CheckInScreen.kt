package com.gogether.app.feature.checkin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.event.MeetingMapButton

@Composable
fun CheckInScreen(event: Event, state: AppSnapshot, now: Long, busy: Boolean,
                  checkIn: () -> Unit, feedback: (Int, String) -> Unit) {
    val confirmed = state.registration(event.id)?.status == RegistrationStatus.CONFIRMED
    val checkedIn = state.checkIns.find { it.eventId == event.id && it.userId == state.currentUserId }
    var rating by rememberSaveable { mutableIntStateOf(5) }
    var comment by rememberSaveable { mutableStateOf("") }
    val ended = event.status == EventStatus.COMPLETED || now >= event.endAt
    val submitted = state.feedback.find { it.eventId == event.id && it.userId == state.currentUserId }
    Page {
        SectionTitle(if (ended) "How was your plan?" else "See you there", event.title)
        InfoCard("Meeting point", "${displayTime(event.startAt)}\n${event.city} · ${event.location}\n${event.notes}")
        MeetingMapButton("${event.city} ${event.location}")
        if (event.status == EventStatus.CANCELLED) InfoCard("Event cancelled", "Check-in and feedback are unavailable.")
        else if (ended) {
            when {
                submitted != null -> InfoCard("Thanks for sharing", "Your rating: ${submitted.rating} / 5\n${submitted.comment}")
                !confirmed -> InfoCard("Feedback for participants", "Only confirmed participants can leave feedback.")
                else -> {
                    Text("Your rating", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        (1..5).forEach { score -> FilterChip(rating == score, { rating = score }, label = { Text("$score") }) }
                    }
                    OutlinedTextField(comment, { comment = it }, label = { Text("Comment (optional)") }, minLines = 3,
                        supportingText = { Text("Up to 500 characters") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { feedback(rating, comment) }, enabled = !busy && comment.length <= 500,
                        modifier = Modifier.fillMaxWidth()) { Text("Submit feedback") }
                }
            }
        } else {
            InfoCard("Demo Check-in", "This button records a local test check-in. It does not scan a QR code or verify your identity or physical presence.")
            when {
                checkedIn != null -> InfoCard("You're checked in · Demo", displayTime(checkedIn.checkedInAt))
                !confirmed -> InfoCard("Confirmed place required", "Waitlisted or unregistered users cannot check in.")
                !event.demoCheckInOpen -> InfoCard("Waiting for the organizer", "The organizer can open Demo check-in from Manage event.")
            }
            Button(onClick = checkIn, enabled = !busy && confirmed && event.demoCheckInOpen && checkedIn == null,
                modifier = Modifier.fillMaxWidth().testTag("demo_checkin")) { Text("Demo Check-in") }
        }
    }
}

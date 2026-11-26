package com.gogether.app.feature.checkin

import com.gogether.app.core.ui.PageActionButton
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
    val eligibility = checkInEligibility(event, state, now)
    var rating by rememberSaveable(event.id, state.currentUserId) { mutableIntStateOf(0) }
    var comment by rememberSaveable(event.id, state.currentUserId) { mutableStateOf("") }
    val ended = event.status == EventStatus.COMPLETED || now >= event.endAt
    val submitted = state.feedback.find { it.eventId == event.id && it.userId == state.currentUserId }
    Page {
        SectionTitle(if (ended) "How was your plan?" else "See you there", event.title)
        InfoCard("Meeting point", "${displayTime(event.meetingAt ?: event.startAt)}\n${event.city} · ${event.meetingLocation ?: event.location}\n${event.meetingNotes.ifBlank { event.notes }}")
        MeetingMapButton("${event.city} ${event.meetingLocation ?: event.location}")
        CheckInWindowCard(event, now)
        InfoCard(eligibility.title,
            if (eligibility == CheckInEligibility.ALREADY_CHECKED_IN && checkedIn != null)
                displayTime(checkedIn.checkedInAt) else eligibility.message,
            Modifier.testTag("checkin_status"))
        if (event.status != EventStatus.CANCELLED && ended) {
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
                    PageActionButton(onClick = { feedback(rating, comment) }, enabled = !busy && FeedbackInput.valid(rating, comment),
                        modifier = Modifier.fillMaxWidth()) { Text("Submit feedback") }
                }
            }
        } else if (event.status != EventStatus.CANCELLED) {
            InfoCard("Demo Check-in", "This button records a local test check-in. It does not scan a QR code or verify your identity or physical presence.")
            PageActionButton(onClick = checkIn, enabled = !busy && eligibility == CheckInEligibility.READY,
                modifier = Modifier.fillMaxWidth().testTag("demo_checkin")) { Text("Demo Check-in") }
        }
    }
}

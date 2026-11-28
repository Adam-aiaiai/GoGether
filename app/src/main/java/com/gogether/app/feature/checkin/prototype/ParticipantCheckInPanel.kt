package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppDivider
import com.gogether.app.core.ui.AppPageHeader
import com.gogether.app.core.ui.PageActionButton
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun eventTime(time: Long, pattern: String) = SimpleDateFormat(pattern, Locale.ENGLISH).format(Date(time))

/** Event context scrolls on small displays; the primary action stays within reach. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParticipantCheckInPanel(actor: String, state: PrototypeUiState, now: Long, back: () -> Unit,
    scan: () -> Unit, history: () -> Unit, settings: () -> Unit, record: (String) -> Unit, scanOther: () -> Unit,
    resolveCheckIn: () -> Unit = {}) {
    val session = state.participantSession ?: state.scanned?.session
    val activity = LocalCheckInActivities.current.infoOrNull(session)
    var sheet by rememberSaveable(actor, session?.id) { mutableStateOf<String?>(null) }
    val registration = state.registration ?: MockCheckInRegistrations.status(session?.id, actor)
    val confirmed = registration == CheckInRegistration.CONFIRMED
    val waitlisted = registration == CheckInRegistration.WAITLISTED
    val participants = state.participants ?: MockCheckInRegistrations.participants(session?.id)
    val name = participants.find { it.userId == actor }?.name ?: actor.replaceFirstChar { it.uppercase() }
    val entry = state.history.find { it.session == session }
    val receipt = (entry?.receipt ?: state.receipt)?.takeIf {
        it.userId == actor && (session == null || it.sessionId == session.id)
    }
    val resolving = state.scanStage == ParticipantScanStage.RESOLVING
    val needsResolution = state.pendingSubmission?.let { it.actor == actor && it.session == session } == true
    val busy = state.busy || state.scanStage == ParticipantScanStage.VERIFYING || resolving
    val attention = !busy && state.scanStage == ParticipantScanStage.ATTENTION
    val showReceipt = receipt != null && !busy && !attention && !needsResolution
    val cancelled = entry?.cancelled == true || state.cancelled
    val ended = session != null && (now >= session.endAt || (state.checkInClosed && !cancelled))
    val early = session != null && now < session.startAt - PrototypePolicy.HALF_HOUR_MS
    val late = session != null && now > session.startAt + PrototypePolicy.HALF_HOUR_MS
    val canOpenRecord = entry != null && (showReceipt || ended) && !attention
    val status = when {
        resolving -> "Checking your check-in result"
        busy -> "Verifying check-in"
        needsResolution -> "Check-in unconfirmed"
        attention -> "Check-in notice"
        cancelled -> "Event cancelled"
        ended -> if (entry?.feedback != null) "Reviewed" else "Event ended"
        showReceipt -> if (state.scanStage == ParticipantScanStage.SUCCESS) "Checked in successfully" else "Checked in"
        !confirmed -> if (waitlisted) "Waitlisted" else "Not registered"
        early -> "Check-in not open yet"
        late -> "Check-in closed"
        session == null -> "Scan to check in on arrival"
        else -> "Ready to check in"
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("participant_panel")) {
        AppPageHeader("My event", horizontalInset = 20.dp, onBack = back, actions = {
            TextButton(onClick = history, modifier = Modifier.testTag("open_history")) { Text("Records") }
        })
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .testTag("participant_status_area"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Taking part", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(activity?.title ?: "Choose an event", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.testTag("participant_event_title"))
                Text("$name  " + if (confirmed) "Registered" else if (waitlisted) "Waitlisted" else "Not registered",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.gogether.app.feature.profile.ParticipantAvatar(activity?.organizerName ?: "?",
                    activity?.let { LocalCheckInActivities.current.avatarUri(it.organizerId) }.orEmpty(), Modifier.size(36.dp))
                Column {
                    Text(activity?.let { "Hosted by ${it.organizerName}" } ?: "Scan to see the organizer", style = MaterialTheme.typography.titleSmall)
                    Text("Organizer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AppDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EventInfoRow("Time", if (session == null) "Scan to see event times" else
                    "${eventTime(session.startAt, "MMM d, HH:mm")}–${eventTime(session.endAt, "HH:mm")}")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { EventInfoRow("Meeting point", if (session == null) "Scan the organizer's code when you arrive" else (activity?.meetingName ?: "Meeting point not confirmed")) }

                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("People", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    participants.take(3).forEach { com.gogether.app.feature.profile.ParticipantAvatar(it.name, it.avatarUri, Modifier.size(26.dp)) }
                    Text("${participants.size} registered", style = MaterialTheme.typography.bodySmall)
                }
            }
            Surface(shape = CheckInStyle.shape, color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().testTag("prototype_status")) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(status, style = MaterialTheme.typography.titleMedium,
                        color = if (attention) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("participant_status_title"))
                    when {
                        busy -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(state.message.ifBlank { "Checking eligibility…" }, style = MaterialTheme.typography.bodySmall)
                        }
                        needsResolution -> Text(state.message, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("participant_issue"))
                        attention -> Text(state.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("participant_issue"))
                        cancelled -> Text("Check-in and reviews are closed for this event.", style = MaterialTheme.typography.bodySmall)
                        showReceipt -> Text("Checked in ${eventTime(receipt!!.checkedInAt, "MMM d, HH:mm:ss")}",
                            Modifier.testTag("prototype_receipt"), style = MaterialTheme.typography.bodyMedium)
                        ended -> Text(if (entry?.canReview(now) == true) "Leave a rating and share your experience in the event record." else "Only checked-in participants can review this event.", style = MaterialTheme.typography.bodySmall)
                        !confirmed -> Text(if (waitlisted) "You can check in once your place is confirmed." else "A confirmed place is required to check in.", style = MaterialTheme.typography.bodySmall)
                        session != null -> Text("${eventTime(session.startAt - PrototypePolicy.HALF_HOUR_MS, "HH:mm")}–${eventTime(minOf(session.endAt, session.startAt + PrototypePolicy.HALF_HOUR_MS), "HH:mm")}  Check-in window", style = MaterialTheme.typography.bodySmall)
                        else -> Text("Scan the organizer's code to confirm the event and meeting point.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            TextButton(shape = CheckInStyle.shape, onClick = { sheet = "about" }, contentPadding = PaddingValues(0.dp),
                modifier = Modifier.testTag("participant_about")) {
                Text("About this event >", style = MaterialTheme.typography.bodySmall)
            }
        }
        PageActionButton(enabled = !busy && (needsResolution || (!cancelled &&
            (canOpenRecord || (!ended && !early && !late && confirmed)))),
            onClick = { if (needsResolution) resolveCheckIn() else if (canOpenRecord) record(entry!!.session.id)
                else scan() },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("prototype_scan")) {
            Text(when {
                resolving -> "Checking…"
                busy -> "Verifying…"
                needsResolution -> "Check result"
                cancelled -> "Event cancelled"
                canOpenRecord && ended -> if (entry?.canReview(now) == true) "Review event" else "View event record"
                canOpenRecord -> "View check-in record"
                ended -> "Event ended"
                early -> "Check-in not open yet"
                late -> "Check-in closed"
                !confirmed -> "Not eligible to check in"
                attention -> "Scan again"
                else -> "Scan to check in"
            })
        }
        TextButton(shape = CheckInStyle.shape, onClick = { sheet = "help" },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp).testTag("participant_help")) {
            Text("Check-in guide and permissions", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (sheet != null) ModalBottomSheet(onDismissRequest = { sheet = null }, shape = CheckInStyle.sheetShape,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (sheet) {
                "about" -> {
                    Text("About the event", style = MaterialTheme.typography.titleLarge)
                    Text((activity?.introduction ?: "Load an event to see its description"))
                    Text("How it works", style = MaterialTheme.typography.titleMedium)
                    Text("Register → Check in → Review after the event")
                    Text("Ask the organizer to confirm the venue and plans when you meet.")
                    Text("After the event, you can submit one rating and comment from its record.")
                }
                else -> {
                    Text("Check-in guide", style = MaterialTheme.typography.titleLarge)
                    Text("Confirmed participants can check in from 30 minutes before to 30 minutes after the start. Check-in closes if the event is cancelled or ends.")
                    Text("Scan the organizer's latest code with camera access enabled. Cancelling and rejoining requires a new check-in.")
                    TextButton(shape = CheckInStyle.shape, onClick = { sheet = null; settings() },
                        modifier = Modifier.testTag("participant_settings")) { Text("Open permission settings") }
                    TextButton(shape = CheckInStyle.shape, enabled = !busy,
                        onClick = { sheet = null; scanOther() }, modifier = Modifier.testTag("participant_scan_other")) {
                        Text("Scan another event")
                    }
                }
            }
        }
    }
}

@Composable
private fun EventInfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

package com.gogether.app.feature.checkin.cloud

import com.gogether.app.core.ui.AppDivider
import com.gogether.app.core.ui.AppPageHeader
import com.gogether.app.core.ui.AppUi
import com.gogether.app.core.ui.PageActionButton
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.gogether.app.core.model.Event
import com.gogether.app.core.model.RegistrationStatus
import com.gogether.app.feature.checkin.CheckInWindow
import com.gogether.app.feature.checkin.CheckInWindowState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Presentation only. The caller retains permissions, scanning, persistence and server validation. */
@Composable
internal fun CloudParticipantCheckInContent(
    event: Event,
    participantName: String,
    organizerName: String,
    registration: RegistrationStatus?,
    now: Long,
    checkInReady: Boolean,
    checkedInAt: Long?,
    loading: Boolean,
    hasInformation: Boolean,
    busy: Boolean,
    pending: Boolean,
    unreadableAttempt: Boolean,
    message: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onReviews: () -> Unit,
    onScan: () -> Unit,
    onResolve: () -> Unit,
    onRetryAttempt: () -> Unit,
) {
    // Dismissing the receipt is navigation only; it never clears the saved check-in.
    var dismissedReceipt by rememberSaveable(event.id) { mutableStateOf<Long?>(null) }
    if (checkedInAt != null && dismissedReceipt != checkedInAt) {
        CloudCheckInResultContent(event, participantName, organizerName, checkedInAt,
            onDone = { dismissedReceipt = checkedInAt })
        return
    }
    val window = CheckInWindow.state(event, now)
    val confirmed = registration == RegistrationStatus.CONFIRMED
    val ready = hasInformation && !loading && checkInReady && confirmed &&
        window == CheckInWindowState.OPEN && !pending && !unreadableAttempt
    val title = when {
        checkedInAt != null -> "You're checked in"
        unreadableAttempt -> "Check-in needs attention"
        pending -> "Confirming your check-in"
        loading -> "Checking availability"
        !hasInformation -> "Couldn't load check-in"
        window == CheckInWindowState.EVENT_CANCELLED -> "Event cancelled"
        window == CheckInWindowState.EVENT_ENDED -> "Event ended"
        registration == RegistrationStatus.WAITLISTED -> "You're on the waitlist"
        !confirmed -> "A confirmed place is required"
        window == CheckInWindowState.NOT_STARTED -> "Check-in opens soon"
        window == CheckInWindowState.EXPIRED -> "Check-in has closed"
        !checkInReady -> "Waiting for the organizer"
        busy -> "Checking your request"
        else -> "Ready to check in"
    }
    val explanation = when {
        checkedInAt != null -> "$participantName, your attendance is recorded."
        unreadableAttempt -> "Your saved attempt couldn't be read. Reopen the app or ask the organizer to verify your attendance before scanning again."
        pending -> "Your attempt is saved. Check its result before scanning again."
        loading -> "Getting the latest information for this event."
        !hasInformation -> "Check your connection and try again."
        window == CheckInWindowState.EVENT_CANCELLED -> "Check-in is unavailable for this event."
        window == CheckInWindowState.EVENT_ENDED -> "Check-in is closed. You can still view this event's reviews."
        registration == RegistrationStatus.WAITLISTED -> "You can check in once your place is confirmed."
        !confirmed -> "Only confirmed participants can check in."
        window == CheckInWindowState.NOT_STARTED -> "You can scan from ${checkInDate(CheckInWindow.opensAt(event), "d MMM, HH:mm")}."
        window == CheckInWindowState.EXPIRED -> "Ask the organizer if you need help with your attendance."
        !checkInReady -> "The organizer needs to confirm arrival and display the code."
        else -> "$participantName, your place is confirmed. Scan the code on your organizer's phone."
    }

    Column(Modifier.fillMaxSize().imePadding().testTag("cloud_participant_checkin")) {
        AppPageHeader("Check in", onBack = onBack,
            backModifier = Modifier.testTag("cloud_checkin_back"), backDescription = "Back to event",
            actions = {
                IconButton(onClick = onRefresh, enabled = !busy && !loading, modifier = Modifier.testTag("cloud_checkin_refresh")) {
                    Icon(Icons.Default.Refresh, "Refresh check-in")
                }
            })

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(event.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("cloud_checkin_event"))
                Text(checkInDate(event.startAt, "EEE, d MMM") + "  " + checkInDate(event.startAt, "HH:mm") +
                    "–" + checkInDate(event.endAt, "HH:mm"), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hosted by $organizerName", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onReviews, modifier = Modifier.testTag("cloud_tab_reviews")) { Text("Reviews") }
                }
            }

            MeetingSummary(event)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("cloud_checkin_loading"))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (checkedInAt != null) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("cloud_participant_status"))
                }
                Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (checkedInAt != null) {
                    Text("Checked in at ${checkInDate(checkedInAt, "HH:mm, d MMM yyyy")}",
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("cloud_checkin_receipt"))
                } else if (hasInformation && !pending && !unreadableAttempt && confirmed && window == CheckInWindowState.OPEN) {
                    AppDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    Text("Check-in window  ${checkInDate(CheckInWindow.opensAt(event), "HH:mm")}–${checkInDate(CheckInWindow.closesAt(event).coerceAtMost(event.endAt), "HH:mm")}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Scan the organizer's latest code during the check-in window.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (message.isNotBlank()) {
                    // Keep server errors and uncertain results visible; they must never look like a receipt.
                    Surface(shape = AppUi.CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Text(message, Modifier.fillMaxWidth().padding(12.dp).testTag("cloud_attendance_message"),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    checkedInAt != null -> PageActionButton(onClick = { dismissedReceipt = null }, modifier = Modifier.fillMaxWidth()
                        .testTag("cloud_checkin_view_result")) { Text("View check-in result") }
                    pending && !unreadableAttempt -> {
                        PageActionButton(onClick = onResolve, enabled = !busy, modifier = Modifier.fillMaxWidth()
                            .testTag("cloud_resolve_checkin")) { Text("Check result") }
                        TextButton(onClick = onRetryAttempt, enabled = !busy, modifier = Modifier.testTag("cloud_retry_checkin")) {
                            Text("Retry saved attempt")
                        }
                    }
                    !loading && !hasInformation && !unreadableAttempt -> PageActionButton(onClick = onRefresh, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("cloud_checkin_retry_load")) { Text("Try again") }
                    else -> PageActionButton(onClick = onScan, enabled = ready && !busy,
                        modifier = Modifier.fillMaxWidth().testTag("cloud_scan_checkin")) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(Modifier.align(Alignment.CenterStart)) { ScanMark() }
                            Text(if (busy) "Please wait…" else "Scan QR code", Modifier.padding(horizontal = 28.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeetingSummary(event: Event) {
    val address = event.meetingLocation ?: event.location
    Surface(shape = AppUi.CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Meeting point", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(checkInDate(event.meetingAt ?: event.startAt, "HH:mm"), style = MaterialTheme.typography.titleSmall)
            }
            Text(address, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(event.city, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MeetingActions(event)
        }
    }
}

@Composable
internal fun MeetingActions(event: Event, notesColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    var showNotes by rememberSaveable(event.id) { mutableStateOf(false) }
    if (event.meetingNotes.isNotBlank()) Column {
        TextButton(onClick = { showNotes = !showNotes }, modifier = Modifier.testTag("cloud_checkin_meeting_notes")) {
            Text(if (showNotes) "Hide notes" else "Meeting notes")
        }
        if (showNotes) Text(event.meetingNotes, style = MaterialTheme.typography.bodyMedium, color = notesColor)
    }
}

@Composable
private fun ScanMark() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2
        val end = size.width - inset
        val corner = size.width * .3f
        listOf(
            Offset(inset, corner) to Offset(inset, inset), Offset(inset, inset) to Offset(corner, inset),
            Offset(end - corner, inset) to Offset(end, inset), Offset(end, inset) to Offset(end, corner),
            Offset(end, end - corner) to Offset(end, end), Offset(end, end) to Offset(end - corner, end),
            Offset(corner, end) to Offset(inset, end), Offset(inset, end) to Offset(inset, end - corner),
            Offset(inset, size.height / 2) to Offset(end, size.height / 2),
        ).forEach { (start, finish) -> drawLine(color, start, finish, strokeWidth = stroke) }
    }
}

internal fun checkInDate(value: Long, pattern: String): String = Instant.ofEpochMilli(value)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))

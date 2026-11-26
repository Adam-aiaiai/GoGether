package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppPageHeader
import com.gogether.app.core.ui.PageActionButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gogether.app.core.ui.CheckInQr
import com.journeyapps.barcodescanner.BarcodeEncoder

/** A bounded phone screen: QR stays visible while only the selected attendance grid scrolls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrganizerCheckInPanel(state: PrototypeUiState, now: Long, back: () -> Unit,
    show: () -> Unit, cancel: () -> Unit, newMeeting: () -> Unit, settings: () -> Unit, history: () -> Unit,
    refreshAttendance: (PrototypeSession) -> Unit = {}, integrated: Boolean = false) {
    val session = requireNotNull(state.session)
    var more by rememberSaveable(session.id) { mutableStateOf(false) }
    var preview by rememberSaveable(session.id) { mutableStateOf(false) }
    var searchOpen by rememberSaveable(session.id) { mutableStateOf(false) }
    val previewPeople = remember(session.id) {
        attendanceLayoutExample.mapIndexed { index, person ->
            person.copy(checkedInAt = person.checkedInAt?.let { minOf(now, session.startAt) - index * 60_000L })
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("organizer_panel")) {
        AppPageHeader(LocalCheckInActivities.current.infoOrNull(session)?.title ?: "Event check-in", onBack = back, actions = {
            IconButton(onClick = { searchOpen = true }, enabled = preview ||
                (state.attendance.session == session && state.attendance.loaded), modifier = Modifier.testTag("attendance_search_open")) {
                Icon(Icons.Default.Search, contentDescription = "Find participants")
            }
            TextButton(shape = CheckInStyle.shape, onClick = { more = true }, modifier = Modifier.testTag("organizer_more")) { Text("More") }
        })
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp)) {
            val people = if (preview) previewPeople else state.attendees
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OrganizerCode(state, now, show, Modifier.weight(1f).fillMaxHeight())
                    key(session.id, preview) {
                        if (preview) PrototypeAttendance(people, Modifier.weight(1f).fillMaxHeight(), searchOpen) { searchOpen = false }
                        else AttendancePanel(state, session, Modifier.weight(1f).fillMaxHeight(), searchOpen,
                            { refreshAttendance(session) }, { searchOpen = false })
                    }
                }
            } else {
                val codeWeight = if (LocalDensity.current.fontScale > 1.3f) 0.48f else 0.55f
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrganizerCode(state, now, show, Modifier.weight(codeWeight).fillMaxWidth())
                    key(session.id, preview) {
                        if (preview) PrototypeAttendance(people, Modifier.weight(1f - codeWeight).fillMaxWidth(), searchOpen) { searchOpen = false }
                        else AttendancePanel(state, session, Modifier.weight(1f - codeWeight).fillMaxWidth(), searchOpen,
                            { refreshAttendance(session) }, { searchOpen = false })
                    }
                }
            }
        }
    }
    if (more) ModalBottomSheet(shape = CheckInStyle.sheetShape, onDismissRequest = { more = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Event details and actions", style = MaterialTheme.typography.titleLarge)
            TextButton(shape = CheckInStyle.shape, onClick = { more = false; history() }, modifier = Modifier.testTag("open_history")) { Text("My records") }
            CheckInInfoCard("Status", state.message)
            MeetingSummary(session)
            Text("Organizers and participants must be within 200 m. Precise location is used only for check-in actions.")
            TextButton(shape = CheckInStyle.shape, onClick = { more = false; settings() }) { Text("Open permission settings") }
            if (!integrated) TextButton(shape = CheckInStyle.shape, onClick = { preview = !preview; more = false }, modifier = Modifier.testTag("attendance_preview")) {
                Text(if (preview) "Back to current attendance" else "Preview larger attendance list")
            }
            if (!integrated && !state.cancelled && now < session.startAt) PageActionButton(onClick = { more = false; cancel() }, enabled = !state.busy) { Text("Cancel event") }
            if (!integrated) TextButton(shape = CheckInStyle.shape, onClick = { more = false; newMeeting() }, enabled = !state.busy) { Text("Create another event") }
            else Text("Cancel from event management to update registrations and the waitlist together.")
        }
    }
}

@Composable
private fun OrganizerCode(state: PrototypeUiState, now: Long, show: () -> Unit, modifier: Modifier) {
    val session = requireNotNull(state.session)
    val available = !state.cancelled && !state.checkInClosed && now < session.endAt &&
        now >= session.startAt - PrototypePolicy.HALF_HOUR_MS && now <= session.startAt + PrototypePolicy.HALF_HOUR_MS
    val code = state.code?.takeIf { available && now >= it.issuedAt - 5_000 && now < it.expiresAt }
    val status = when {
        state.cancelled -> "Event cancelled. Codes are unavailable."
        !available -> "Outside the check-in window"
        code != null -> "Refreshes in ${(code.expiresAt - now + 999) / 1000}s"
        state.busy -> "Checking location…"
        else -> state.message
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight, 280.dp)
            if (code != null) {
                val bitmap = remember(code) { BarcodeEncoder().createBitmap(
                    CheckInQr.matrix(PrototypeCodeCodec.encode(code))).asImageBitmap() }
                Image(bitmap, "Refreshing check-in QR code", Modifier.size(side).background(Color.White).testTag("prototype_qr"))
            } else Surface(Modifier.size(side), shape = CheckInStyle.shape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(if (state.busy) "Locating…" else "Waiting for a code", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(status, Modifier.testTag("prototype_status"), style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        PageActionButton(onClick = show, enabled = !state.busy && available,
            modifier = Modifier.fillMaxWidth().testTag("prototype_show")) {
            Text(if (code != null) "Refresh code" else "Get location and show code")
        }
    }
}

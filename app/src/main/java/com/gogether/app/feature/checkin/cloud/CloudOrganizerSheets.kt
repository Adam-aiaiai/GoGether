package com.gogether.app.feature.checkin.cloud

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.gogether.app.core.model.Event
import com.gogether.app.core.ui.*
import com.gogether.app.feature.checkin.CheckInWindow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganizerSheet(title: String, tag: String, onClose: () -> Unit,
    footer: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag(tag)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            CompositionLocalProvider(LocalPageActionWidth provides maxWidth * .8f) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(.86f)) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                        IconButton(onClose, modifier = Modifier.testTag("${tag}_close")) { Icon(Icons.Default.Close, "Close $title") }
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
                    Box(Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 8.dp)) { footer() }
                }
            }
        }
    }
}

@Composable
internal fun OrganizerMeetingSheet(event: Event, onClose: () -> Unit) {
    OrganizerSheet("Meeting details", "cloud_meeting_sheet", onClose) {
        MeetingField("Meet at", displayTime(event.meetingAt ?: event.startAt))
        MeetingField("Address", "${event.meetingLocation ?: event.location}\n${event.city}")
        if (event.meetingNotes.isNotBlank()) MeetingField("Instructions", event.meetingNotes)
    }
}

@Composable
internal fun OrganizerRulesSheet(event: Event, onClose: () -> Unit) {
    OrganizerSheet("Check-in rules", "cloud_rules_sheet", onClose, footer = {
        PageActionButton(onClose, modifier = Modifier.testTag("cloud_rules_done")) { Text("Done") }
    }) {
        Text(event.title, style = MaterialTheme.typography.titleMedium)
        Text("${displayTime(event.startAt)}–${attendanceTime(event.endAt)}", style = MaterialTheme.typography.bodyMedium)
        MeetingField("When", "${displayTime(CheckInWindow.opensAt(event))}–${attendanceTime(minOf(CheckInWindow.closesAt(event), event.endAt))}\n" +
            "Opens 30 minutes before the start and closes 30 minutes after. Ends earlier if the activity ends or is cancelled.")
        MeetingField("Who can check in", "Confirmed participants only. Waitlisted participants need a confirmed place first.")
        MeetingField("Your code", "The code refreshes automatically. Scan the latest code with camera access enabled.")
        MeetingField("One check-in per person", "Each confirmed registration can check in once. Rejoining requires a new check-in.")
    }
}

@Composable
private fun MeetingField(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

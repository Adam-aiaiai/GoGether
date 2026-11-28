package com.gogether.app.feature.coordination

import com.gogether.app.core.ui.AppOutlinedTextField
import com.gogether.app.core.ui.AppCommentField
import com.gogether.app.core.ui.PageActionButton
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.event.parseEventDateTime
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun MeetingEditor(event: Event, now: Long, busy: Boolean, save: (MeetingUpdate, () -> Unit) -> Unit) {
    val context = LocalContext.current
    var editing by rememberSaveable(event.id) { mutableStateOf(false) }
    var date by rememberSaveable(event.id) { mutableStateOf("") }
    var time by rememberSaveable(event.id) { mutableStateOf("") }
    var location by rememberSaveable(event.id) { mutableStateOf("") }
    var notes by rememberSaveable(event.id) { mutableStateOf("") }
    var submitted by rememberSaveable(event.id) { mutableStateOf(false) }
    var dateDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    val parsed = runCatching { parseEventDateTime(date, time) }.getOrNull()
    val timeError = when {
        parsed == null -> "Choose a valid date and time."
        parsed <= now -> "Meeting time must be in the future."
        parsed > event.endAt -> "Meeting time must be before the event ends."
        else -> null
    }
    val locationError = if (location.trim().length !in 3..150) "Use 3–150 characters for the location." else null
    val notesError = if (notes.length > 100) "Use up to 100 characters." else null
    InfoCard("Meeting point", "${displayTime(event.meetingAt ?: event.startAt)}\n${event.meetingLocation ?: event.location}\n${event.meetingNotes}")
    if (!editing) {
        PageActionButton(enabled = !busy && event.status == EventStatus.OPEN && now < event.endAt,
            modifier = Modifier.testTag("edit_meeting"), onClick = {
            val local = Instant.ofEpochMilli(event.meetingAt ?: event.startAt).atZone(ZoneId.systemDefault())
            date = local.toLocalDate().toString(); time = local.format(DateTimeFormatter.ofPattern("HH:mm"))
            location = event.meetingLocation ?: event.location; notes = event.meetingNotes
            submitted = false; editing = true
        }) { Text("Edit meeting information") }
    } else {
        Text("Meeting time · ${ZoneId.systemDefault()}")
        AppOutlinedPickerField("Meeting date", date, "date", Modifier.fillMaxWidth().testTag("meeting_date"),
            enabled = !busy, onClick = { dateDialog = true })
        AppOutlinedPickerField("Meeting time", time, "time", Modifier.fillMaxWidth().testTag("meeting_time"),
            enabled = !busy, onClick = { timeDialog = true })
        if (submitted && timeError != null) Text(timeError, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("meeting_time_error"))
        AppOutlinedTextField(location, { location = it }, label = { Text("Meeting location") }, enabled = !busy,
            isError = submitted && locationError != null,
            supportingText = { Text(if (submitted && locationError != null) locationError else "${location.length}/150") },
            modifier = Modifier.fillMaxWidth().testTag("meeting_location"))
        AppCommentField(notes, { notes = it }, label = { Text("Meeting notes") }, enabled = !busy,
            isError = submitted && notesError != null,
            supportingText = notesError?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth().testTag("meeting_notes"))
        PageActionButton(enabled = !busy, modifier = Modifier.testTag("save_meeting"), onClick = {
            submitted = true
            if (parsed != null && timeError == null && locationError == null && notesError == null)
                save(MeetingUpdate(parsed, location, notes)) { editing = false }
        }) { Text(if (busy) "Saving…" else "Save meeting information") }
        OutlinedPageActionButton(enabled = !busy, onClick = { editing = false }) { Text("Cancel editing") }
    }
    if (dateDialog) DisposableEffect(Unit) {
        val initial = LocalDate.parse(date)
        val dialog = AppDatePickerDialog(context, { _, y, m, d ->
            date = LocalDate.of(y, m + 1, d).toString(); dateDialog = false
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth)
        dialog.setOnDismissListener { dateDialog = false }; dialog.show()
        onDispose { dialog.dismiss() }
    }
    if (timeDialog) DisposableEffect(Unit) {
        val initial = LocalTime.parse(time)
        val dialog = AppTimePickerDialog(context, { _, h, m ->
            time = LocalTime.of(h, m).format(DateTimeFormatter.ofPattern("HH:mm")); timeDialog = false
        }, initial.hour, initial.minute, true)
        dialog.setOnDismissListener { timeDialog = false }; dialog.show()
        onDispose { dialog.dismiss() }
    }
}

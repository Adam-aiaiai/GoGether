package com.gogether.app.feature.event

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import com.gogether.app.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.registration.RegistrationEngine
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CreateEventScreen(
    busy: Boolean,
    retainedDraft: CreateEventFormState?,
    onRetainDraft: (CreateEventFormState) -> Unit,
    onCreate: (EventDraft) -> Unit,
) {
    EventFormScreen(null, FormMode.CREATE, busy, onCreate, retainedDraft, onRetainDraft)
}

@Composable
fun EditEventScreen(event: Event, busy: Boolean, onUpdate: (EventDraft) -> Unit) {
    EventFormScreen(event, FormMode.EDIT, busy, onUpdate)
}

@Composable
fun ReuseEventScreen(event: Event, busy: Boolean, onCreate: (EventDraft) -> Unit) {
    val draft = remember(event.id) { event.reuseDraft() }
    val template = remember(event.id) {
        event.copy(id = "reuse-${event.id}", startAt = draft.startAt, endAt = draft.endAt,
            registrationDeadline = draft.registrationDeadline, status = EventStatus.OPEN, demoCheckInOpen = false, meetingAt = null)
    }
    EventFormScreen(template, FormMode.REUSE, busy, onCreate)
}

private enum class FormMode { CREATE, EDIT, REUSE }

@Composable
private fun EventFormScreen(
    initial: Event?,
    mode: FormMode,
    busy: Boolean,
    onSubmit: (EventDraft) -> Unit,
    retainedDraft: CreateEventFormState? = null,
    onRetainDraft: (CreateEventFormState) -> Unit = {},
) {
    val zone = remember { ZoneId.systemDefault() }
    val tomorrow = remember { LocalDate.now().plusDays(1) }
    val defaultStart = remember { tomorrow.atTime(18, 0).atZone(zone).toInstant().toEpochMilli() }
    val editing = mode == FormMode.EDIT
    val prefix = mode.name.lowercase()
    val retained = retainedDraft.takeIf { mode == FormMode.CREATE }
    var title by rememberSaveable(initial?.id) { mutableStateOf(retained?.title ?: initial?.title.orEmpty()) }
    var category by rememberSaveable(initial?.id) { mutableStateOf(retained?.category ?: initial?.category?.name ?: Category.SPORTS.name) }
    var description by rememberSaveable(initial?.id) { mutableStateOf(retained?.description ?: initial?.description.orEmpty()) }
    var startDate by rememberSaveable(initial?.id) { mutableStateOf(retained?.startDate ?: formatDate(initial?.startAt ?: defaultStart, zone)) }
    var startTime by rememberSaveable(initial?.id) { mutableStateOf(retained?.startTime ?: formatClock(initial?.startAt ?: defaultStart, zone)) }
    var endDate by rememberSaveable(initial?.id) { mutableStateOf(retained?.endDate ?: formatDate(initial?.endAt ?: defaultStart + RegistrationEngine.TWO_HOURS, zone)) }
    var endTime by rememberSaveable(initial?.id) { mutableStateOf(retained?.endTime ?: formatClock(initial?.endAt ?: defaultStart + RegistrationEngine.TWO_HOURS, zone)) }
    var location by rememberSaveable(initial?.id) { mutableStateOf(retained?.location ?: initial?.location.orEmpty()) }
    var city by rememberSaveable(initial?.id) { mutableStateOf(retained?.city ?: initial?.city ?: "Suzhou") }
    var capacity by rememberSaveable(initial?.id) { mutableStateOf(retained?.capacity ?: initial?.capacity?.toString() ?: "4") }
    var deadlineDate by rememberSaveable(initial?.id) {
        mutableStateOf(retained?.deadlineDate ?: formatDate(initial?.registrationDeadline ?: defaultStart - 60 * 60 * 1000L, zone))
    }
    var deadlineTime by rememberSaveable(initial?.id) {
        mutableStateOf(retained?.deadlineTime ?: formatClock(initial?.registrationDeadline ?: defaultStart - 60 * 60 * 1000L, zone))
    }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(retained?.notes ?: initial?.notes.orEmpty()) }
    var experience by rememberSaveable(initial?.id) {
        mutableStateOf(retained?.experience ?: initial?.experienceRequirement?.name ?: ExperienceRequirement.NONE.name)
    }
    var beginnerGuidance by rememberSaveable(initial?.id) { mutableStateOf(retained?.beginnerGuidance ?: initial?.beginnerGuidance ?: false) }
    var requiredItems by rememberSaveable(initial?.id) { mutableStateOf(retained?.requiredItems ?: initial?.requiredItems.orEmpty()) }
    var spareEquipment by rememberSaveable(initial?.id) { mutableStateOf(retained?.spareEquipment ?: initial?.spareEquipment.orEmpty()) }
    var imageUri by rememberSaveable(initial?.id) { mutableStateOf(retained?.imageUri ?: initial?.imageUri.orEmpty()) }
    var error by rememberSaveable(initial?.id) { mutableStateOf<String?>(null) }
    var meetingDate by rememberSaveable(initial?.id) { mutableStateOf(initial?.meetingAt?.let { formatDate(it, zone) }.orEmpty()) }
    var meetingTime by rememberSaveable(initial?.id) { mutableStateOf(initial?.meetingAt?.let { formatClock(it, zone) }.orEmpty()) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) imageUri = uri.toString()
    }
    val currentCreateDraft = CreateEventFormState(
        title, category, description, startDate, startTime, endDate, endTime, location, city,
        capacity, deadlineDate, deadlineTime, notes, experience, beginnerGuidance,
        requiredItems, spareEquipment, imageUri,
    )
    LaunchedEffect(mode, currentCreateDraft) {
        if (mode == FormMode.CREATE) onRetainDraft(currentCreateDraft)
    }

    Page {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(when (mode) {
                FormMode.CREATE -> "Create an event"
                FormMode.EDIT -> "Edit your event"
                FormMode.REUSE -> "Reuse this event"
            }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(when (mode) {
                FormMode.CREATE -> "Bring people together, one plan at a time."
                FormMode.EDIT -> "Keep participants clear on what changed."
                FormMode.REUSE -> "Adjust the copied time and place before publishing."
            }, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (imageUri.isNotBlank()) {
            EventImage(imageUri, title.ifBlank { "Event image" }, Modifier.fillMaxWidth().height(180.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.testTag("${prefix}_choose_image")) { Text("Change cover") }
                TextButton(onClick = { imageUri = "" },
                    modifier = Modifier.testTag("${prefix}_remove_image")) { Text("Remove") }
            }
        } else {
            OutlinedButton(onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth().height(112.dp)
                    .testTag("${prefix}_choose_image"),
                shape = MaterialTheme.shapes.large) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("▧ +", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text("Add an event cover", fontWeight = FontWeight.SemiBold)
                    Text("Choose a photo from your gallery",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Optional · 16:9 recommended",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalDivider()
        FormSectionHeader("01", "Basics")
        FormField("Event title", title, { title = it }, "${prefix}_title",
            placeholder = "e.g. Friday Badminton Session")
        Text("Category", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { option ->
                FilterChip(category == option.name, { category = option.name },
                    label = { Text(option.label) })
            }
        }
        FormField("Description", description, { description = it }, "${prefix}_description",
            multiline = true, placeholder = "Tell others what this event is about...")

        HorizontalDivider()
        FormSectionHeader("02", "Date & Location")
        DateSelectionField("Start date", startDate, { startDate = it }, "${prefix}_date")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeSelectionField("Start time", startTime, { startTime = it }, "${prefix}_time", Modifier.weight(1f))
            TimeSelectionField("End time", endTime, { endTime = it }, "${prefix}_end_time", Modifier.weight(1f))
        }
        DateSelectionField("End date", endDate, { endDate = it }, "${prefix}_end_date")
        FormField("Location", location, { location = it }, "${prefix}_location",
            placeholder = "Choose a campus location")
        FormField("City", city, { city = it }, "${prefix}_city", placeholder = "e.g. Suzhou")
        Text("Times use ${zone.id}. Events may last up to 24 hours.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateSelectionField("Registration deadline", deadlineDate, { deadlineDate = it },
                "${prefix}_deadline_date", Modifier.weight(1.35f))
            TimeSelectionField("Time", deadlineTime, { deadlineTime = it },
                "${prefix}_deadline_time", Modifier.weight(0.65f))
        }

        if (editing && initial?.meetingAt != null) {
            Text("Meeting time", style = MaterialTheme.typography.titleMedium)
            Text("Adjust this too if you move the event's end time. The meeting place and notes stay unchanged.", style = MaterialTheme.typography.bodySmall)
            DateSelectionField("Meeting date", meetingDate, { meetingDate = it }, "edit_meeting_date")
            TimeSelectionField("Meeting time", meetingTime, { meetingTime = it }, "edit_meeting_time")
        }
        if (mode == FormMode.REUSE && initial?.meetingLocation != null) {
            InfoCard("Meeting point", "${initial.meetingLocation}\nThe meeting time defaults to this new event's start time. You can adjust it in Manage event.")
        }
        HorizontalDivider()
        FormSectionHeader("03", "Participation")
        OutlinedTextField(capacity, { capacity = it }, label = { Text("Maximum participants") },
            placeholder = { Text("e.g. 6") },
            supportingText = { Text("2–50 people, including the organizer") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().testTag("${prefix}_capacity"))
        Text("Experience level", style = MaterialTheme.typography.labelLarge)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ExperienceRequirement.entries.forEach { option ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = experience == option.name,
                        onClick = { experience = option.name })
                    Text(option.formLabel(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Beginner guidance provided", fontWeight = FontWeight.SemiBold)
                Text("Someone will explain or demonstrate the basics.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(beginnerGuidance, { beginnerGuidance = it }, modifier = Modifier.testTag("${prefix}_guidance"))
        }
        FormField("Items participants need to bring", requiredItems, { requiredItems = it },
            "${prefix}_required_items", multiline = true)
        FormField("Spare equipment available", spareEquipment, { spareEquipment = it },
            "${prefix}_spare_equipment", multiline = true)
        FormField("Additional notes (optional)", notes, { notes = it }, "${prefix}_notes", multiline = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("${prefix}_error")) }
        Button(onClick = {
            try {
                val missing = listOf(
                    "Start date" to startDate, "Start time" to startTime,
                    "End date" to endDate, "End time" to endTime,
                    "Registration deadline date" to deadlineDate, "Registration deadline time" to deadlineTime,
                ).firstOrNull { it.second.isBlank() }?.first
                require(missing == null) { "Select $missing before publishing." }
                if (editing && initial?.meetingAt != null) {
                    require(meetingDate.isNotBlank() && meetingTime.isNotBlank()) { "Select a meeting date and time before saving." }
                }
                val draft = EventDraft(
                    title = title, category = Category.valueOf(category), description = description,
                    startAt = parseEventDateTime(startDate, startTime), location = location, city = city,
                    capacity = capacity.toIntOrNull() ?: 0,
                    registrationDeadline = parseEventDateTime(deadlineDate, deadlineTime), notes = notes,
                    endAt = parseEventDateTime(endDate, endTime),
                    experienceRequirement = ExperienceRequirement.valueOf(experience),
                    beginnerGuidance = beginnerGuidance, requiredItems = requiredItems,
                    spareEquipment = spareEquipment, imageUri = imageUri,
                    meetingAt = if (editing && initial?.meetingAt != null) parseEventDateTime(meetingDate, meetingTime) else null,
                    meetingLocation = initial?.meetingLocation, meetingNotes = initial?.meetingNotes.orEmpty(),
                )
                RegistrationEngine.validateDraft(draft, System.currentTimeMillis(), allowClosedDeadline = editing)
                error = null
                onSubmit(draft)
            } catch (_: DateTimeException) {
                error = "Select valid dates and 24-hour times."
            } catch (e: IllegalArgumentException) {
                error = e.message
            }
        }, enabled = !busy, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag(when (mode) {
            FormMode.EDIT -> "save_event"
            FormMode.REUSE -> "publish_reused_event"
            FormMode.CREATE -> "publish_event"
        })) {
            Text(if (mode == FormMode.EDIT) "Save changes" else "Publish event")
        }
        if (mode == FormMode.CREATE) {
            Text("Draft saves automatically when you leave this page.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("draft_saved_hint"))
        }
    }
}

private fun formatDate(epoch: Long, zone: ZoneId) = Instant.ofEpochMilli(epoch).atZone(zone)
    .format(DateTimeFormatter.ISO_LOCAL_DATE)

private fun formatClock(epoch: Long, zone: ZoneId) = Instant.ofEpochMilli(epoch).atZone(zone)
    .format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun FormSectionHeader(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge)
        Text("$number / $title", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    change: (String) -> Unit,
    tag: String,
    multiline: Boolean = false,
    placeholder: String? = null,
) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = !multiline,
        placeholder = placeholder?.let { { Text(it) } }, minLines = if (multiline) 3 else 1,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable
private fun DateSelectionField(
    label: String,
    value: String,
    change: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val context = LocalContext.current
    SelectionField(label, value, "YYYY-MM-DD", tag, modifier) {
        val initial = runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }
        DatePickerDialog(context, R.style.Theme_Gogether_Picker, { _, year, month, day ->
            change(LocalDate.of(year, month + 1, day).format(DateTimeFormatter.ISO_LOCAL_DATE))
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }
}

@Composable
private fun TimeSelectionField(
    label: String,
    value: String,
    change: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val context = LocalContext.current
    SelectionField(label, value, "HH:mm", tag, modifier) {
        val initial = runCatching { LocalTime.parse(value) }.getOrElse { LocalTime.of(18, 0) }
        TimePickerDialog(context, R.style.Theme_Gogether_Picker, { _, hour, minute ->
            change(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
        }, initial.hour, initial.minute, true).show()
    }
}

@Composable
private fun SelectionField(
    label: String,
    value: String,
    hint: String,
    tag: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = modifier.testTag(tag),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifBlank { "Select $hint" }, style = MaterialTheme.typography.bodyLarge,
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun ExperienceRequirement.formLabel(): String = when (this) {
    ExperienceRequirement.NONE -> "All levels welcome"
    ExperienceRequirement.BASIC -> "Some experience preferred"
    ExperienceRequirement.EXPERIENCED -> "Experienced participants only"
}

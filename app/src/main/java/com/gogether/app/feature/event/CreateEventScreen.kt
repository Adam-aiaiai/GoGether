package com.gogether.app.feature.event

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.registration.RegistrationEngine
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun CreateEventScreen(busy: Boolean, onCreate: (EventDraft) -> Unit) {
    EventFormScreen(null, FormMode.CREATE, busy, onCreate)
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
            registrationDeadline = draft.registrationDeadline, status = EventStatus.OPEN, demoCheckInOpen = false)
    }
    EventFormScreen(template, FormMode.REUSE, busy, onCreate)
}

private enum class FormMode { CREATE, EDIT, REUSE }

@Composable
private fun EventFormScreen(initial: Event?, mode: FormMode, busy: Boolean, onSubmit: (EventDraft) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val tomorrow = remember { LocalDate.now().plusDays(1) }
    val defaultStart = remember { tomorrow.atTime(18, 0).atZone(zone).toInstant().toEpochMilli() }
    val editing = mode == FormMode.EDIT
    val prefix = mode.name.lowercase()
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var category by rememberSaveable(initial?.id) { mutableStateOf(initial?.category?.name ?: Category.SPORTS.name) }
    var description by rememberSaveable(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var startDate by rememberSaveable(initial?.id) { mutableStateOf(formatDate(initial?.startAt ?: defaultStart, zone)) }
    var startTime by rememberSaveable(initial?.id) { mutableStateOf(formatClock(initial?.startAt ?: defaultStart, zone)) }
    var endDate by rememberSaveable(initial?.id) { mutableStateOf(formatDate(initial?.endAt ?: defaultStart + RegistrationEngine.TWO_HOURS, zone)) }
    var endTime by rememberSaveable(initial?.id) { mutableStateOf(formatClock(initial?.endAt ?: defaultStart + RegistrationEngine.TWO_HOURS, zone)) }
    var location by rememberSaveable(initial?.id) { mutableStateOf(initial?.location.orEmpty()) }
    var city by rememberSaveable(initial?.id) { mutableStateOf(initial?.city ?: "Suzhou") }
    var capacity by rememberSaveable(initial?.id) { mutableStateOf(initial?.capacity?.toString() ?: "4") }
    var deadlineDate by rememberSaveable(initial?.id) {
        mutableStateOf(formatDate(initial?.registrationDeadline ?: defaultStart - 60 * 60 * 1000L, zone))
    }
    var deadlineTime by rememberSaveable(initial?.id) {
        mutableStateOf(formatClock(initial?.registrationDeadline ?: defaultStart - 60 * 60 * 1000L, zone))
    }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var experience by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.experienceRequirement?.name ?: ExperienceRequirement.NONE.name)
    }
    var beginnerGuidance by rememberSaveable(initial?.id) { mutableStateOf(initial?.beginnerGuidance ?: false) }
    var requiredItems by rememberSaveable(initial?.id) { mutableStateOf(initial?.requiredItems.orEmpty()) }
    var spareEquipment by rememberSaveable(initial?.id) { mutableStateOf(initial?.spareEquipment.orEmpty()) }
    var imageUri by rememberSaveable(initial?.id) { mutableStateOf(initial?.imageUri.orEmpty()) }
    var error by rememberSaveable(initial?.id) { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) imageUri = uri.toString()
    }

    Page {
        SectionTitle(when (mode) {
            FormMode.CREATE -> "Make a plan"
            FormMode.EDIT -> "Edit your plan"
            FormMode.REUSE -> "Reuse this plan"
        }, when (mode) {
            FormMode.CREATE -> "A few details. A few new faces."
            FormMode.EDIT -> "Keep participants clear on what changed."
            FormMode.REUSE -> "Review the copied details, then adjust the time and place."
        })
        FormField("Title", title, { title = it }, "${prefix}_title")
        Text("Category", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { option ->
                FilterChip(category == option.name, { category = option.name }, label = { Text(option.label) })
            }
        }
        FormField("Description", description, { description = it }, "${prefix}_description", multiline = true)
        Text("Event image (optional)", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()
            .testTag("${prefix}_choose_image")) {
            Text(if (imageUri.isBlank()) "Choose an image" else "Change image")
        }
        if (imageUri.isNotBlank()) {
            EventImage(imageUri, title.ifBlank { "Event image" }, Modifier.fillMaxWidth().height(180.dp))
            TextButton(onClick = { imageUri = "" }, modifier = Modifier.testTag("${prefix}_remove_image")) {
                Text("Remove image")
            }
        }
        Text("Schedule", style = MaterialTheme.typography.titleMedium)
        FormField("Start date (YYYY-MM-DD)", startDate, { startDate = it }, "${prefix}_date")
        FormField("Start time (HH:mm, 24-hour)", startTime, { startTime = it }, "${prefix}_time")
        FormField("End date (YYYY-MM-DD)", endDate, { endDate = it }, "${prefix}_end_date")
        FormField("End time (HH:mm, 24-hour)", endTime, { endTime = it }, "${prefix}_end_time")
        Text("Times use ${zone.id}. Events may last up to 24 hours.", style = MaterialTheme.typography.bodySmall)
        FormField("City", city, { city = it }, "${prefix}_city")
        FormField("Meeting location", location, { location = it }, "${prefix}_location")
        OutlinedTextField(capacity, { capacity = it }, label = { Text("Maximum participants") },
            supportingText = { Text("2–50 people, including you as the organizer") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("${prefix}_capacity"))
        FormField("Deadline date (YYYY-MM-DD)", deadlineDate, { deadlineDate = it }, "${prefix}_deadline_date")
        FormField("Deadline time (HH:mm)", deadlineTime, { deadlineTime = it }, "${prefix}_deadline_time")

        SectionTitle("Before people join", "Make the participation threshold visible.")
        Text("Experience requirement", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExperienceRequirement.entries.forEach { option ->
                FilterChip(experience == option.name, { experience = option.name }, label = { Text(option.label) })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Beginner guidance provided", style = MaterialTheme.typography.titleMedium)
                Text("Choose this only when someone will explain or demonstrate the basics.",
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
                val draft = EventDraft(
                    title = title, category = Category.valueOf(category), description = description,
                    startAt = parseEventDateTime(startDate, startTime), location = location, city = city,
                    capacity = capacity.toIntOrNull() ?: 0,
                    registrationDeadline = parseEventDateTime(deadlineDate, deadlineTime), notes = notes,
                    endAt = parseEventDateTime(endDate, endTime),
                    experienceRequirement = ExperienceRequirement.valueOf(experience),
                    beginnerGuidance = beginnerGuidance, requiredItems = requiredItems,
                    spareEquipment = spareEquipment, imageUri = imageUri,
                )
                RegistrationEngine.validateDraft(draft, System.currentTimeMillis(), allowClosedDeadline = editing)
                error = null
                onSubmit(draft)
            } catch (_: DateTimeException) {
                error = "Use valid dates (YYYY-MM-DD) and 24-hour times (HH:mm)."
            } catch (e: IllegalArgumentException) {
                error = e.message
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag(when (mode) {
            FormMode.EDIT -> "save_event"
            FormMode.REUSE -> "publish_reused_event"
            FormMode.CREATE -> "publish_event"
        })) {
            Text(if (mode == FormMode.EDIT) "Save changes" else "Publish event")
        }
    }
}

private fun formatDate(epoch: Long, zone: ZoneId) = Instant.ofEpochMilli(epoch).atZone(zone)
    .format(DateTimeFormatter.ISO_LOCAL_DATE)

private fun formatClock(epoch: Long, zone: ZoneId) = Instant.ofEpochMilli(epoch).atZone(zone)
    .format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun FormField(label: String, value: String, change: (String) -> Unit, tag: String, multiline: Boolean = false) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = !multiline,
        minLines = if (multiline) 3 else 1, modifier = Modifier.fillMaxWidth().testTag(tag))
}

package com.gogether.app.feature.event

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.registration.RegistrationEngine
import java.time.LocalDate
import java.time.ZoneId
import java.time.DateTimeException

@Composable
fun CreateEventScreen(busy: Boolean, onCreate: (EventDraft) -> Unit) {
    val tomorrow = remember { LocalDate.now().plusDays(1).toString() }
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Category.SPORTS.name) }
    var description by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(tomorrow) }
    var time by rememberSaveable { mutableStateOf("18:00") }
    var location by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("Suzhou") }
    var capacity by rememberSaveable { mutableStateOf("4") }
    var deadlineDate by rememberSaveable { mutableStateOf(tomorrow) }
    var deadlineTime by rememberSaveable { mutableStateOf("17:00") }
    var notes by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Page {
        SectionTitle("Make a plan", "A few details. A few new faces.")
        FormField("Title", title, { title = it }, "create_title")
        Text("Category", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Category.entries.forEach { option ->
                FilterChip(category == option.name, { category = option.name }, label = { Text(option.label) })
            }
        }
        FormField("Description", description, { description = it }, "create_description", multiline = true)
        FormField("Date (YYYY-MM-DD)", date, { date = it }, "create_date")
        FormField("Time (HH:mm, 24-hour)", time, { time = it }, "create_time")
        Text("Times use ${ZoneId.systemDefault().id}. Demo events last two hours.", style = MaterialTheme.typography.bodySmall)
        FormField("City", city, { city = it }, "create_city")
        FormField("Meeting location", location, { location = it }, "create_location")
        OutlinedTextField(capacity, { capacity = it }, label = { Text("Maximum participants") },
            supportingText = { Text("2–50 people, including you as the organizer") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("create_capacity"))
        FormField("Deadline date (YYYY-MM-DD)", deadlineDate, { deadlineDate = it }, "create_deadline_date")
        FormField("Deadline time (HH:mm)", deadlineTime, { deadlineTime = it }, "create_deadline_time")
        FormField("Additional notes (optional)", notes, { notes = it }, "create_notes", multiline = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("create_error")) }
        Button(onClick = {
            try {
                val draft = EventDraft(title, Category.valueOf(category), description, parseEventDateTime(date, time),
                    location, city, capacity.toIntOrNull() ?: 0, parseEventDateTime(deadlineDate, deadlineTime), notes)
                RegistrationEngine.validateDraft(draft, System.currentTimeMillis())
                error = null
                onCreate(draft)
            } catch (e: DateTimeException) {
                error = "Use a valid date (YYYY-MM-DD) and 24-hour time (HH:mm)."
            } catch (e: IllegalArgumentException) {
                error = e.message
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("publish_event")) { Text("Publish event") }
    }
}

@Composable
private fun FormField(label: String, value: String, change: (String) -> Unit, tag: String, multiline: Boolean = false) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = !multiline, minLines = if (multiline) 3 else 1,
        modifier = Modifier.fillMaxWidth().testTag(tag))
}

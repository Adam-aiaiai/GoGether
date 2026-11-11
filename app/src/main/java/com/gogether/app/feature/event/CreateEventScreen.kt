package com.gogether.app.feature.event

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gogether.app.core.ui.GogetherViewModel
import com.gogether.app.core.ui.PageTitle
import java.time.*
import java.time.format.DateTimeParseException

@Composable
fun CreateEventScreen(viewModel: GogetherViewModel, onCreated: (String) -> Unit) {
    var title by remember { mutableStateOf("") }; var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var time by remember { mutableStateOf("18:30") }; var location by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("8") }; var deadline by remember { mutableStateOf(LocalDate.now().plusDays(6).toString()) }
    var notes by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle("Create an event", "Bring people together with a few details")
        Field(title, { title = it }, "Title", error = title.isBlank() && error != null)
        Field(category, { category = it }, "Category", error = category.isBlank() && error != null)
        Field(description, { description = it }, "Description", singleLine = false, error = description.isBlank() && error != null)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Field(date, { date = it }, "Date (YYYY-MM-DD)", Modifier.weight(1f)); Field(time, { time = it }, "Time (HH:mm)", Modifier.weight(1f)) }
        Field(location, { location = it }, "Meeting location", error = location.isBlank() && error != null)
        Field(capacity, { capacity = it.filter(Char::isDigit) }, "Maximum participants")
        Field(deadline, { deadline = it }, "Registration deadline (YYYY-MM-DD)")
        Field(notes, { notes = it }, "Additional notes", singleLine = false)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            try {
                val start = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant()
                val closes = LocalDate.parse(deadline).atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant()
                val event = viewModel.createEvent(title, category, description, start, location, capacity.toIntOrNull() ?: 0, closes, notes)
                if (event != null) onCreated(event.id) else error = viewModel.message.value ?: "Please check the form"
            } catch (_: DateTimeParseException) { error = "Use valid date and time values" }
        }, modifier = Modifier.fillMaxWidth()) { Text("Create demo event") }
        Text("This writes to the shared in-memory repository, so the event immediately appears in Explore and My Events.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, error: Boolean = false) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(), singleLine = singleLine, isError = error)
}

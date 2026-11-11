package com.gogether.app.feature.event

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EventDetailScreen(viewModel: GogetherViewModel, eventId: String, onBack: () -> Unit, onManage: (String) -> Unit, onCheckIn: (String) -> Unit) {
    val events by viewModel.events.collectAsState()
    val registrations by viewModel.registrations.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val message by viewModel.message.collectAsState()
    val event = events.firstOrNull { it.id == eventId }
    if (event == null) { Box(Modifier.fillMaxSize().padding(24.dp)) { Text("Event not found") }; return }
    val rows = registrations.filter { it.eventId == eventId }
    val counts = EventCounts(rows.count { it.status == RegistrationStatus.CONFIRMED }, rows.count { it.status == RegistrationStatus.WAITLISTED })
    val registration = rows.lastOrNull { it.userId == user?.id && it.status in setOf(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED) }
    Scaffold(topBar = { TopAppBar(title = { Text("Event details") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AssistChip(onClick = {}, label = { Text(event.category) })
            Text(event.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(event.displayDate(), style = MaterialTheme.typography.titleMedium)
            DetailRow("Meeting point", event.meetingLocation)
            DetailRow("About", event.description)
            DetailRow("Capacity", "${counts.confirmed} / ${event.maxParticipants} confirmed")
            DetailRow("Waitlist", "${counts.waitlisted} people")
            DetailRow("Registration status", registration?.status?.name ?: if (event.status == EventStatus.CANCELLED) "EVENT CANCELLED" else "NOT REGISTERED")
            if (event.additionalNotes.isNotBlank()) DetailRow("Notes", event.additionalNotes)
            message?.let { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) { Text(it, Modifier.padding(14.dp)) } }
            when {
                event.creatorId == user?.id -> Button(onClick = { onManage(eventId) }, modifier = Modifier.fillMaxWidth()) { Text("Manage event") }
                registration != null -> {
                    Button(onClick = { viewModel.cancelRegistration(eventId) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Cancel registration") }
                    if (registration.status == RegistrationStatus.CONFIRMED) OutlinedButton(onClick = { onCheckIn(eventId) }, modifier = Modifier.fillMaxWidth()) { Text("Demo check-in") }
                }
                event.status == EventStatus.OPEN -> Button(onClick = { viewModel.join(eventId) }, modifier = Modifier.fillMaxWidth()) { Text(if (counts.confirmed >= event.maxParticipants) "Join waitlist" else "Join event") }
            }
            Text("Demo state is stored only in memory and resets when the app process restarts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary); Text(value) }
}

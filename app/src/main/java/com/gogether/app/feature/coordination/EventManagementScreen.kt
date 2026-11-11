package com.gogether.app.feature.coordination

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.RegistrationStatus
import com.gogether.app.core.ui.GogetherViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EventManagementScreen(viewModel: GogetherViewModel, eventId: String, onBack: () -> Unit) {
    val events by viewModel.events.collectAsState(); val registrations by viewModel.registrations.collectAsState(); val users by viewModel.users.collectAsState()
    val event = events.firstOrNull { it.id == eventId }
    val confirmed = registrations.filter { it.eventId == eventId && it.status == RegistrationStatus.CONFIRMED }
    val waitlisted = registrations.filter { it.eventId == eventId && it.status == RegistrationStatus.WAITLISTED }.sortedBy { it.createdAt }
    Scaffold(topBar = { TopAppBar(title = { Text("Event management") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(event?.title ?: "Event not found", style = MaterialTheme.typography.headlineMedium); Text("${confirmed.size} / ${event?.maxParticipants ?: 0} confirmed · ${waitlisted.size} waitlisted"); Text("Meeting point: ${event?.meetingLocation.orEmpty()}") }
            item { Text("Confirmed participants", style = MaterialTheme.typography.titleMedium) }
            items(confirmed) { row -> ListItem(headlineContent = { Text(users.firstOrNull { it.id == row.userId }?.nickname ?: row.userId) }, supportingContent = { Text(if (row.userId == event?.creatorId) "Organizer · confirmed" else "Confirmed") }) }
            item { Text("Waitlist queue", style = MaterialTheme.typography.titleMedium) }
            if (waitlisted.isEmpty()) item { Text("The waitlist is empty.") }
            items(waitlisted) { row -> ListItem(headlineContent = { Text(users.firstOrNull { it.id == row.userId }?.nickname ?: row.userId) }, supportingContent = { Text("Queue position ${waitlisted.indexOf(row) + 1}") }) }
            item { Button(onClick = { viewModel.cancelEvent(eventId) }, enabled = event?.status?.name == "OPEN", colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Cancel event") }; Text("Cancelling blocks new registrations and marks active demo registrations cancelled.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

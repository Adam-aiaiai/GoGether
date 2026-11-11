package com.gogether.app.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.RegistrationStatus
import com.gogether.app.core.ui.*

@Composable
fun ProfileScreen(viewModel: GogetherViewModel, onEvent: (String) -> Unit) {
    val users by viewModel.users.collectAsState(); val current by viewModel.currentUser.collectAsState()
    val registrations by viewModel.registrations.collectAsState(); val events by viewModel.events.collectAsState()
    val history = events.filter { event -> registrations.any { it.eventId == event.id && it.userId == current?.id && it.status == RegistrationStatus.CANCELLED } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { PageTitle("Profile"); DemoBadge() } }
        item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(current?.nickname.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Interests: ${current?.interests?.joinToString().orEmpty()}"); Text("Role: ${if (current?.isOrganizer == true) "Organizer demo" else "Participant demo"}") } } }
        item { Text("Switch test user", style = MaterialTheme.typography.titleMedium) }
        items(users) { user -> OutlinedButton(onClick = { viewModel.login(user.id) }, modifier = Modifier.fillMaxWidth(), enabled = user.id != current?.id) { Text(user.nickname) } }
        item { Text("Activity history", style = MaterialTheme.typography.titleMedium); Text(if (history.isEmpty()) "Cancelled/completed activity history will appear here." else history.joinToString { it.title }) }
        item { HorizontalDivider(); TextButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) { Text("Return to demo login") }; Text("No account, credential, or personal data is sent to a backend in this MVP.", style = MaterialTheme.typography.bodySmall) }
    }
}

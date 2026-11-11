package com.gogether.app.feature.checkin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gogether.app.core.ui.DemoBadge
import com.gogether.app.core.ui.GogetherViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CheckInScreen(viewModel: GogetherViewModel, eventId: String, onBack: () -> Unit) {
    val events by viewModel.events.collectAsState(); val checkIns by viewModel.checkIns.collectAsState(); val user by viewModel.currentUser.collectAsState(); val message by viewModel.message.collectAsState()
    val event = events.firstOrNull { it.id == eventId }; val checkedIn = checkIns.any { it.eventId == eventId && it.userId == user?.id }
    Scaffold(topBar = { TopAppBar(title = { Text("Check-in") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            DemoBadge(); Text(event?.title ?: "Event", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(event?.meetingLocation.orEmpty())
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Text("This button simulates local check-in only. QR scanning, identity verification, anti-fraud controls, and server validation are not implemented.", Modifier.padding(18.dp)) }
            Button(onClick = { viewModel.checkIn(eventId) }, enabled = !checkedIn, modifier = Modifier.fillMaxWidth()) { Text(if (checkedIn) "Checked in (demo)" else "Record demo check-in") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

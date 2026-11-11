package com.gogether.app.feature.registration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MyEventsScreen(viewModel: GogetherViewModel, onEvent: (String) -> Unit, onManage: (String) -> Unit) {
    val events by viewModel.events.collectAsState(); val registrations by viewModel.registrations.collectAsState(); val user by viewModel.currentUser.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val userId = user?.id
    val visible = when (tab) {
        0 -> events.filter { event -> registrations.any { it.eventId == event.id && it.userId == userId && it.status == RegistrationStatus.CONFIRMED } }
        1 -> events.filter { event -> registrations.any { it.eventId == event.id && it.userId == userId && it.status == RegistrationStatus.WAITLISTED } }
        else -> events.filter { it.creatorId == userId }
    }
    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Box(Modifier.padding(horizontal = 20.dp)) { PageTitle("My Events", "All lists use the shared demo repository") }
        PrimaryTabRow(tab, Modifier.padding(top = 14.dp)) { listOf("Joined", "Waitlisted", "Created").forEachIndexed { index, label -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) }) } }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (visible.isEmpty()) item { Text("Nothing here yet.", Modifier.padding(vertical = 28.dp)) }
            items(visible, key = { it.id }) { event ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val rows = registrations.filter { it.eventId == event.id }
                    EventCard(event, EventCounts(rows.count { it.status == RegistrationStatus.CONFIRMED }, rows.count { it.status == RegistrationStatus.WAITLISTED }), { onEvent(event.id) })
                    if (tab == 2) TextButton(onClick = { onManage(event.id) }) { Text("Open organizer tools") }
                }
            }
        }
    }
}

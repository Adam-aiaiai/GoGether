package com.gogether.app.feature.explore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.EventStatus
import com.gogether.app.core.ui.*

@Composable
fun ExploreScreen(viewModel: GogetherViewModel, onEvent: (String) -> Unit) {
    val events by viewModel.events.collectAsState()
    val registrations by viewModel.registrations.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    val categories = listOf("All") + events.map { it.category }.distinct()
    val visible = events.filter { it.status == EventStatus.OPEN && (category == "All" || it.category == category) && (query.isBlank() || it.title.contains(query, true) || it.meetingLocation.contains(query, true)) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { PageTitle("Hello, ${user?.nickname?.substringBefore(" ·") ?: "friend"}!", "What would you like to do?"); DemoBadge() } }
        item { OutlinedTextField(query, { query = it }, label = { Text("Search events or places") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(categories) { item -> FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) }) } } }
        if (visible.isEmpty()) item { Text("No matching events. Try another category or search.", Modifier.padding(vertical = 32.dp)) }
        items(visible, key = { it.id }) { event ->
            val counts = remember(registrations, event.id) { val rows = registrations.filter { it.eventId == event.id }; com.gogether.app.core.model.EventCounts(rows.count { it.status.name == "CONFIRMED" }, rows.count { it.status.name == "WAITLISTED" }) }
            EventCard(event, counts, { onEvent(event.id) })
        }
    }
}

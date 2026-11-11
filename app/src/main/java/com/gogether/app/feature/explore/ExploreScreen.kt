package com.gogether.app.feature.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*

@Composable
fun ExploreScreen(state: AppSnapshot, events: List<Event>, filter: ExploreFilter, now: Long,
                  onFilter: (ExploreFilter) -> Unit, openEvent: (String) -> Unit) {
    Page {
        Text("HELLO, ${state.currentUser?.displayName?.uppercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Good plans.\nBetter together.", style = MaterialTheme.typography.headlineLarge)
                Text("Discover your next small adventure.", style = MaterialTheme.typography.bodyLarge)
            }
        }
        OutlinedTextField(filter.query, { onFilter(filter.copy(query = it)) }, label = { Text("Search events") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("search"), shape = RoundedCornerShape(18.dp))
        OutlinedTextField(filter.city, { onFilter(filter.copy(city = it)) }, label = { Text("City or meeting location") },
            placeholder = { Text("e.g. Suzhou") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter.category == null, { onFilter(filter.copy(category = null)) }, label = { Text("All") })
            Category.entries.forEach { category ->
                FilterChip(filter.category == category, { onFilter(filter.copy(category = category)) }, label = { Text(category.label) })
            }
        }
        FilterChip(filter.nextSevenDays, { onFilter(filter.copy(nextSevenDays = !filter.nextSevenDays)) }, label = { Text("Next 7 days") })
        Text("Find your next plan · ${events.size}", style = MaterialTheme.typography.titleLarge)
        if (events.isEmpty()) InfoCard("No events here yet", "Try another category, city or search. You can also create your own plan.")
        events.forEach { EventCard(it, state, { openEvent(it.id) }, now) }
        Text("Demo city search uses fictional listings. Location permission is not required.", style = MaterialTheme.typography.bodySmall)
    }
}

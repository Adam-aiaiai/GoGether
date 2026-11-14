package com.gogether.app.feature.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*

@Composable
fun ExploreScreen(state: AppSnapshot, events: List<Event>, filter: ExploreFilter, now: Long,
                  onFilter: (ExploreFilter) -> Unit, openEvent: (String) -> Unit) {
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val advancedCount = listOf(
        filter.organizerQuery.isNotBlank(), filter.city.isNotBlank(), filter.category != null,
        filter.time != EventTimeFilter.ANY, filter.freeTime != FreeTimeFilter.ANY,
        filter.beginnerFriendlyOnly, filter.guidanceOnly, filter.spareEquipmentOnly,
    ).count { it }
    Page {
        Text("HELLO, ${state.currentUser?.displayName?.uppercase()}", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Good plans.\nBetter together.", style = MaterialTheme.typography.headlineLarge)
                Text("Discover your next small adventure.", style = MaterialTheme.typography.bodyLarge)
            }
        }
        OutlinedTextField(filter.query, { onFilter(filter.copy(query = it)) },
            label = { Text("Search events, organizers or equipment") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("search"), shape = RoundedCornerShape(18.dp))
        OutlinedButton(onClick = { filtersExpanded = !filtersExpanded },
            modifier = Modifier.fillMaxWidth().testTag("toggle_filters")) {
            Text(if (filtersExpanded) "Hide filters" else "Filters${if (advancedCount > 0) " · $advancedCount active" else ""}")
        }
        if (filtersExpanded) Surface(shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(filter.organizerQuery, { onFilter(filter.copy(organizerQuery = it)) },
                    label = { Text("Organizer") }, placeholder = { Text("e.g. Alice") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("organizer_filter"))
                OutlinedTextField(filter.city, { onFilter(filter.copy(city = it)) },
                    label = { Text("City or meeting location") }, placeholder = { Text("e.g. Suzhou") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("location_filter"))

                FilterHeading("Category")
                FilterRow {
                    FilterChip(filter.category == null, { onFilter(filter.copy(category = null)) }, label = { Text("All") })
                    Category.entries.forEach { category ->
                        FilterChip(filter.category == category, { onFilter(filter.copy(category = category)) },
                            label = { Text(category.label) })
                    }
                }
                FilterHeading("Date range")
                FilterRow {
                    EventTimeFilter.entries.forEach { option ->
                        FilterChip(filter.time == option, { onFilter(filter.copy(time = option)) },
                            label = { Text(option.label) })
                    }
                }
                FilterHeading("Your free time · based on event start")
                FilterRow {
                    FreeTimeFilter.entries.forEach { option ->
                        FilterChip(filter.freeTime == option, { onFilter(filter.copy(freeTime = option)) },
                            label = { Text(option.label) }, modifier = Modifier.testTag("free_time_${option.name.lowercase()}"))
                    }
                }
                FilterHeading("Participation")
                FilterRow {
                    FilterChip(filter.beginnerFriendlyOnly,
                        { onFilter(filter.copy(beginnerFriendlyOnly = !filter.beginnerFriendlyOnly)) },
                        label = { Text("Beginner friendly") }, modifier = Modifier.testTag("filter_beginner"))
                    FilterChip(filter.guidanceOnly, { onFilter(filter.copy(guidanceOnly = !filter.guidanceOnly)) },
                        label = { Text("Guidance provided") }, modifier = Modifier.testTag("filter_guidance"))
                    FilterChip(filter.spareEquipmentOnly,
                        { onFilter(filter.copy(spareEquipmentOnly = !filter.spareEquipmentOnly)) },
                        label = { Text("Spare gear") }, modifier = Modifier.testTag("filter_spare"))
                }
            }
        }
        if (filter != ExploreFilter()) {
            TextButton(onClick = { onFilter(ExploreFilter()) }, modifier = Modifier.testTag("clear_filters")) {
                Text("Clear filters")
            }
        }
        Text("Find your next plan · ${events.size}", style = MaterialTheme.typography.titleLarge)
        if (events.isEmpty()) InfoCard("No events here yet",
            "Try another category, time, place or participation filter. You can also create your own plan.")
        events.forEach { EventCard(it, state, { openEvent(it.id) }, now) }
        Text("Demo city search uses fictional listings. Location permission is not required.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FilterHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun FilterRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

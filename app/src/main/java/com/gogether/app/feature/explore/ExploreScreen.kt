package com.gogether.app.feature.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*

@Composable
fun ExploreScreen(
    state: AppSnapshot,
    events: List<Event>,
    filter: ExploreFilter,
    now: Long,
    onFilter: (ExploreFilter) -> Unit,
    openEvent: (String) -> Unit,
) {
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val advancedCount = listOf(
        filter.organizerQuery.isNotBlank(), filter.city.isNotBlank(), filter.categories.isNotEmpty(),
        filter.time != EventTimeFilter.ANY, filter.freeTime != FreeTimeFilter.ANY,
        filter.experienceLevels.isNotEmpty(), filter.guidanceOnly,
        filter.equipment != EquipmentFilter.ANY, filter.spareEquipmentOnly,
    ).count { it }

    Page {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("HELLO, ${state.currentUser?.displayName?.uppercase()} 👋",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Find your next plan.", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold)
            Text("Discover activities that fit your campus life.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedTextField(
            value = filter.query,
            onValueChange = { onFilter(filter.copy(query = it)) },
            placeholder = { Text("Search events or locations") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { filtersExpanded = !filtersExpanded },
                    modifier = Modifier.testTag("toggle_filters")) {
                    BadgedBox(badge = {
                        if (advancedCount > 0) Badge { Text(advancedCount.toString()) }
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Filters")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("search"),
            shape = RoundedCornerShape(18.dp),
        )

        CategoryPicker(filter.categories) { category ->
            onFilter(filter.copy(categories = filter.categories.toggle(category)))
        }

        FilterRow {
            QuickFilterChip("Today", filter.time == EventTimeFilter.TODAY, "filter_today") {
                onFilter(filter.copy(time = filter.time.toggle(EventTimeFilter.TODAY)))
            }
            QuickFilterChip("Tomorrow", filter.time == EventTimeFilter.TOMORROW, "filter_tomorrow") {
                onFilter(filter.copy(time = filter.time.toggle(EventTimeFilter.TOMORROW)))
            }
            QuickFilterChip("This Weekend", filter.time == EventTimeFilter.THIS_WEEKEND, "filter_weekend") {
                onFilter(filter.copy(time = filter.time.toggle(EventTimeFilter.THIS_WEEKEND)))
            }
            QuickFilterChip(
                "Beginner Friendly",
                ExperienceRequirement.NONE in filter.experienceLevels,
                "filter_beginner",
            ) {
                onFilter(filter.copy(
                    experienceLevels = filter.experienceLevels.toggle(ExperienceRequirement.NONE)))
            }
        }

        if (filtersExpanded) AdvancedFilters(filter, onFilter)

        if (filter != ExploreFilter()) {
            TextButton(onClick = { onFilter(ExploreFilter()) },
                modifier = Modifier.testTag("clear_filters")) {
                Text("Clear all filters")
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Upcoming Activities", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${events.size} ${if (events.size == 1) "activity" else "activities"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (events.isEmpty()) {
            InfoCard("No matching activities",
                "Try changing the date, interest or experience filters. You can also create your own plan.")
        }
        events.forEach { event -> EventCard(event, state, { openEvent(event.id) }, now) }
    }
}

@Composable
private fun CategoryPicker(selected: Set<Category>, select: (Category) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Category.entries.forEach { category ->
            val active = category in selected
            Surface(
                onClick = { select(category) },
                shape = RoundedCornerShape(18.dp),
                color = if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                modifier = Modifier.widthIn(min = 62.dp)
                    .testTag("category_${category.name.lowercase()}"),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(category.symbol, style = MaterialTheme.typography.headlineSmall)
                    Text(category.label, style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun AdvancedFilters(filter: ExploreFilter, onFilter: (ExploreFilter) -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("advanced_filters")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("More ways to match", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            OutlinedTextField(filter.organizerQuery,
                { onFilter(filter.copy(organizerQuery = it)) }, label = { Text("Organizer") },
                placeholder = { Text("e.g. Alice") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("organizer_filter"))
            OutlinedTextField(filter.city, { onFilter(filter.copy(city = it)) },
                label = { Text("City or meeting location") }, placeholder = { Text("e.g. Sports Hall") },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("location_filter"))

            FilterHeading("Activity date · based on start time")
            FilterRow {
                EventTimeFilter.entries.forEach { option ->
                    FilterChip(filter.time == option, { onFilter(filter.copy(time = option)) },
                        label = { Text(option.label) },
                        modifier = Modifier.testTag("date_${option.name.lowercase()}"))
                }
            }
            if (filter.time == EventTimeFilter.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(filter.customStartDate,
                        { onFilter(filter.copy(customStartDate = it)) },
                        label = { Text("From") }, placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true, modifier = Modifier.weight(1f).testTag("custom_start_date"))
                    OutlinedTextField(filter.customEndDate,
                        { onFilter(filter.copy(customEndDate = it)) },
                        label = { Text("To") }, placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true, modifier = Modifier.weight(1f).testTag("custom_end_date"))
                }
            }

            FilterHeading("Time of day")
            FilterRow {
                FreeTimeFilter.entries.forEach { option ->
                    FilterChip(filter.freeTime == option, { onFilter(filter.copy(freeTime = option)) },
                        label = { Text(option.label) },
                        modifier = Modifier.testTag("free_time_${option.name.lowercase()}"))
                }
            }

            FilterHeading("Experience level")
            FilterRow {
                ExperienceRequirement.entries.forEach { option ->
                    FilterChip(option in filter.experienceLevels,
                        { onFilter(filter.copy(
                            experienceLevels = filter.experienceLevels.toggle(option))) },
                        label = { Text(option.filterLabel()) },
                        modifier = Modifier.testTag("experience_${option.name.lowercase()}"))
                }
            }

            FilterHeading("Participation support")
            FilterRow {
                FilterChip(filter.guidanceOnly,
                    { onFilter(filter.copy(guidanceOnly = !filter.guidanceOnly)) },
                    label = { Text("Guidance provided") }, modifier = Modifier.testTag("filter_guidance"))
                FilterChip(filter.spareEquipmentOnly,
                    { onFilter(filter.copy(spareEquipmentOnly = !filter.spareEquipmentOnly)) },
                    label = { Text("Spare gear available") }, modifier = Modifier.testTag("filter_spare"))
            }

            FilterHeading("Equipment to bring")
            FilterRow {
                EquipmentFilter.entries.forEach { option ->
                    FilterChip(filter.equipment == option,
                        { onFilter(filter.copy(equipment = option)) }, label = { Text(option.label) },
                        modifier = Modifier.testTag("equipment_${option.name.lowercase()}"))
                }
            }
        }
    }
}

@Composable
private fun QuickFilterChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(selected, onClick, label = { Text(label) }, modifier = Modifier.testTag(tag))
}

@Composable
private fun FilterHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun FilterRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value

private fun EventTimeFilter.toggle(value: EventTimeFilter): EventTimeFilter =
    if (this == value) EventTimeFilter.ANY else value

private fun ExperienceRequirement.filterLabel(): String = when (this) {
    ExperienceRequirement.NONE -> "Beginner Friendly"
    ExperienceRequirement.BASIC -> "Some Experience Required"
    ExperienceRequirement.EXPERIENCED -> "Experienced Players"
}

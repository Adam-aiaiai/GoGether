package com.gogether.app.feature.explore

import android.content.DialogInterface
import com.gogether.app.core.ui.AppOutlinedTextField
import com.gogether.app.core.ui.AppUi
import com.gogether.app.core.ui.AppFilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import java.time.LocalDate

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

        AppOutlinedTextField(
            value = filter.query,
            onValueChange = { onFilter(filter.copy(query = it)) },
            label = { Text("Search events or locations") },
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
            shape = AppUi.FieldShape,
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

        if (filtersExpanded) AdvancedFilters(filter, onFilter) { filtersExpanded = false }

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
                shape = AppUi.FieldShape,
                color = if (active) AppUi.SelectedTag
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.widthIn(min = 62.dp)
                    .testTag("category_${category.name.lowercase()}"),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(category.symbol, style = MaterialTheme.typography.headlineSmall)
                    Text(category.label, style = MaterialTheme.typography.labelSmall,
                        color = if (active) AppUi.SelectedTagText
                            else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun AdvancedFilters(filter: ExploreFilter, onFilter: (ExploreFilter) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).testTag("advanced_filters")) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AppDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filters", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose, modifier = Modifier.testTag("hide_filters")) { Text("Hide filters") }
                }
                Text("Tap options to filter events. Results update automatically.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppOutlinedTextField(filter.organizerQuery,
                    { onFilter(filter.copy(organizerQuery = it)) }, label = { Text("Organizer") },
                    placeholder = { Text("e.g. Alice") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("organizer_filter"))
                AppOutlinedTextField(filter.city, { onFilter(filter.copy(city = it)) },
                    label = { Text("City or meeting location") }, placeholder = { Text("e.g. Sports Hall") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("location_filter"))
            }
            FilterSection("Activity start date", "Choose one") {
                FilterOptions {
                    EventTimeFilter.entries.forEach { option ->
                        AdvancedFilterOption(filter.time == option, { onFilter(filter.copy(time = option)) },
                            option.label, "date_${option.name.lowercase()}")
                    }
                }
                if (filter.time == EventTimeFilter.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterDateField("From", filter.customStartDate,
                            { onFilter(filter.copy(customStartDate = it)) },
                            Modifier.weight(1f).testTag("custom_start_date"))
                        FilterDateField("To", filter.customEndDate,
                            { onFilter(filter.copy(customEndDate = it)) },
                            Modifier.weight(1f).testTag("custom_end_date"))
                    }
                }
            }
            FilterSection("Time of day", "Choose one") {
                FilterOptions {
                    FreeTimeFilter.entries.forEach { option ->
                        AdvancedFilterOption(filter.freeTime == option, { onFilter(filter.copy(freeTime = option)) },
                            option.label, "free_time_${option.name.lowercase()}")
                    }
                }
            }
            FilterSection("Experience level", "Choose any") {
                FilterOptions {
                    ExperienceRequirement.entries.forEach { option ->
                        AdvancedFilterOption(option in filter.experienceLevels,
                            { onFilter(filter.copy(experienceLevels = filter.experienceLevels.toggle(option))) },
                            option.filterLabel(), "experience_${option.name.lowercase()}")
                    }
                }
            }
            FilterSection("Participation support", "Choose any") {
                FilterOptions {
                    AdvancedFilterOption(filter.guidanceOnly,
                        { onFilter(filter.copy(guidanceOnly = !filter.guidanceOnly)) },
                        "Guidance provided", "filter_guidance")
                    AdvancedFilterOption(filter.spareEquipmentOnly,
                        { onFilter(filter.copy(spareEquipmentOnly = !filter.spareEquipmentOnly)) },
                        "Spare gear available", "filter_spare")
                }
            }
            FilterSection("Equipment to bring", "Choose one") {
                FilterOptions {
                    EquipmentFilter.entries.forEach { option ->
                        AdvancedFilterOption(filter.equipment == option,
                            { onFilter(filter.copy(equipment = option)) }, option.label, "equipment_${option.name.lowercase()}")
                    }
                }
            }
            AppDivider()
        }
    }
}

@Composable
private fun FilterDateField(label: String, value: String, onDateChange: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var showing by remember { mutableStateOf(false) }
    val updateDate by rememberUpdatedState(onDateChange)
    AppOutlinedPickerField(label, value, "date", modifier) {
        focus.clearFocus()
        keyboard?.hide()
        showing = true
    }
    if (showing) DisposableEffect(context) {
        val initial = runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }
        val dialog = AppDatePickerDialog(context, { _, year, month, day ->
            updateDate(LocalDate.of(year, month + 1, day).toString())
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth)
        dialog.setTitle("$label date")
        // Keep the existing ability to filter with only one end of the range set.
        if (value.isNotBlank()) dialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Clear") { _, _ -> updateDate("") }
        dialog.setOnDismissListener { showing = false }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}

@Composable
private fun QuickFilterChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    AppFilterChip(selected, onClick, label = { Text(label) }, modifier = Modifier.testTag(tag))
}

@Composable
private fun FilterSection(title: String, choiceHint: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppDivider(Modifier.padding(bottom = 12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(choiceHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@Composable
private fun AdvancedFilterOption(selected: Boolean, onClick: () -> Unit, label: String, tag: String) {
    FilterChip(selected, onClick, label = { Text(label) }, modifier = Modifier.testTag(tag),
        shape = AppUi.TagShape, border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f)
                Color(0xFFF1F2F4) else Color(0xFF303136),
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = AppUi.SelectedTag, selectedLabelColor = AppUi.SelectedTagText,
        ))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterOptions(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
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

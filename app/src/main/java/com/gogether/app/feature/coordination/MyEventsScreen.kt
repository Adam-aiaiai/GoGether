package com.gogether.app.feature.coordination

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.profile.*

@Composable
fun MyEventsScreen(state: AppSnapshot, now: Long, openEvent: (String) -> Unit) {
    var selected by rememberSaveable(state.currentUserId) { mutableStateOf(PersonalEventTab.JOINED.name) }
    val tab = PersonalEventTab.valueOf(selected)
    val events = personalEvents(state, tab, now)
    Page {
        SectionTitle("Your plans", "Upcoming activities, waitlists and past experiences.")
        @Suppress("DEPRECATION")
        ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp) {
            PersonalEventTab.entries.forEach { option ->
                Tab(selected = option == tab, onClick = { selected = option.name },
                    text = { Text(option.label) }, modifier = Modifier.testTag("personal_tab_${option.name}"))
            }
        }
        if (events.isEmpty()) InfoCard("No ${tab.label.lowercase()} activities yet", when (tab) {
            PersonalEventTab.JOINED -> "Find an activity in Explore and join when you are ready."
            PersonalEventTab.WAITLISTED -> "Activities you are waiting to join will appear here."
            PersonalEventTab.CREATED -> "Your published activities will appear here."
            PersonalEventTab.HISTORY -> "Ended activities, cancelled plans and withdrawals will appear here."
        })
        events.forEach { event ->
            EventCard(event, state, { openEvent(event.id) }, now)
            Text(participationSummary(state, event, now), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("participation_${event.id}"))
        }
    }
}

package com.gogether.app.feature.coordination

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*

fun myEventList(state: AppSnapshot, tab: Int, history: Boolean, now: Long): List<Event> {
    if (state.currentUserId == null) return emptyList()
    return state.events.filter { event ->
        val registration = state.registration(event.id)
        val past = event.status != EventStatus.OPEN || event.endAt <= now || registration?.status == RegistrationStatus.CANCELLED
        val belongs = when (tab) {
            0 -> registration?.status == RegistrationStatus.CONFIRMED || (history && registration?.status == RegistrationStatus.CANCELLED)
            1 -> registration?.status == RegistrationStatus.WAITLISTED
            else -> event.organizerId == state.currentUserId
        }
        belongs && past == history
    }.let { if (history) it.sortedByDescending { e -> e.startAt } else it.sortedBy { e -> e.startAt } }
}

@Composable
fun MyEventsScreen(state: AppSnapshot, now: Long, openInbox: () -> Unit = {}, explore: () -> Unit = {},
                   create: () -> Unit = {}, openEvent: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var history by rememberSaveable { mutableStateOf(false) }
    val tabs = listOf("Joined", "Waitlisted", "Created")
    val events = myEventList(state, tab, history, now)
    val unread = state.notifications.count { it.userId == state.currentUserId && !it.isRead }
    Page {
        SectionTitle("Your plans", "Your next activity comes first.")
        FilledTonalButton(onClick = openInbox, modifier = Modifier.fillMaxWidth().testTag("open_inbox")) {
            Text("Notifications · $unread unread")
        }
        TabRow(tab) {
            tabs.forEachIndexed { index, title -> Tab(tab == index, { tab = index }, text = { Text(title) }) }
        }
        Row {
            FilterChip(!history, { history = false }, label = { Text("Upcoming") }, modifier = Modifier.testTag("events_upcoming"))
            FilterChip(history, { history = true }, label = { Text("History") }, modifier = Modifier.testTag("events_history"))
        }
        if (events.isEmpty()) {
            InfoCard(if (history) "No activity history here" else "No ${tabs[tab].lowercase()} events yet",
                if (history) "Ended and cancelled activities will appear here." else "Choose an activity or start a plan of your own.")
            Button(onClick = if (tab == 2) create else explore, modifier = Modifier.testTag("empty_events_action")) {
                Text(if (tab == 2) "Create an activity" else "Explore activities")
            }
        }
        events.forEach { event -> EventCard(event, state, { openEvent(event.id) }, now) }
    }
}

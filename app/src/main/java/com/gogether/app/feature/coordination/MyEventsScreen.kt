package com.gogether.app.feature.coordination

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*

@Composable
fun MyEventsScreen(state: AppSnapshot, now: Long, openEvent: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Joined", "Waitlisted", "Created")
    val events = state.events.filter { event ->
        when (tab) {
            0 -> state.registration(event.id)?.status == RegistrationStatus.CONFIRMED
            1 -> state.registration(event.id)?.status == RegistrationStatus.WAITLISTED
            else -> event.organizerId == state.currentUserId
        }
    }.sortedByDescending { it.startAt }
    Page {
        SectionTitle("Your plans", "Everything you're part of, in one place.")
        TabRow(tab) {
            tabs.forEachIndexed { index, title -> Tab(tab == index, { tab = index }, text = { Text(title) }) }
        }
        if (events.isEmpty()) InfoCard("No ${tabs[tab].lowercase()} events yet", "Explore a new activity or create a plan of your own.")
        events.forEach { event -> EventCard(event, state, { openEvent(event.id) }, now) }
    }
}

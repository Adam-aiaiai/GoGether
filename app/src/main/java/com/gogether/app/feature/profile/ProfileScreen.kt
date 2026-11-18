package com.gogether.app.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.coordination.InboxContent

@Composable
fun ProfileScreen(state: AppSnapshot, now: Long, busy: Boolean, switchUser: (String) -> Unit,
                  logout: () -> Unit, read: (String) -> Unit, readAll: () -> Unit = {}, explore: () -> Unit = {}, openEvent: (String) -> Unit) {
    val user = state.currentUser ?: return
    val history = state.events.filter { event -> state.registration(event.id) != null &&
        (event.status != EventStatus.OPEN || event.endAt <= now || state.registration(event.id)?.status == RegistrationStatus.CANCELLED) }
    Page {
        SectionTitle("Hey, ${user.displayName}", "Make room for a little more together.")
        StatusPill("Demo Mode")
        InfoCard("Your interests", user.interests.joinToString { it.label }.ifEmpty { "Ready to try something new" })
        InfoCard("This demo session", "Changes are saved on this device, including announcements and read status. Separate devices still have separate Demo data.")
        Text("Switch test user", style = MaterialTheme.typography.titleLarge)
        state.users.filter { it.id != user.id }.forEach { target ->
            OutlinedButton(onClick = { switchUser(target.id) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("switch_${target.id}")) { Text("Switch to ${target.displayName}") }
        }
        TextButton(onClick = logout, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Log out / create Demo profile") }
        InboxContent(state, busy, read, readAll, openEvent, explore)
        Text("Activity history", style = MaterialTheme.typography.titleLarge)
        if (history.isEmpty()) Text("Your past plans will appear here.")
        history.forEach { EventCard(it, state, { openEvent(it.id) }, now) }
    }
}

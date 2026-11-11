package com.gogether.app.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*

@Composable
fun ProfileScreen(state: AppSnapshot, now: Long, busy: Boolean, switchUser: (String) -> Unit,
                  logout: () -> Unit, read: (String) -> Unit, openEvent: (String) -> Unit) {
    val user = state.currentUser ?: return
    val history = state.events.filter { event -> state.registration(event.id) != null &&
        (event.status != EventStatus.OPEN || event.endAt <= now || state.registration(event.id)?.status == RegistrationStatus.CANCELLED) }
    val notifications = state.notifications.filter { it.userId == user.id }.sortedByDescending { it.createdAt }
    Page {
        SectionTitle("Hey, ${user.displayName}", "Make room for a little more together.")
        StatusPill("Demo Mode")
        InfoCard("Your interests", user.interests.joinToString { it.label }.ifEmpty { "Ready to try something new" })
        InfoCard("This demo session", "Profiles and activities are shared within this app process. Restarting the process resets all changes. Separate devices have separate data.")
        Text("Switch test user", style = MaterialTheme.typography.titleLarge)
        state.users.filter { it.id != user.id }.forEach { target ->
            OutlinedButton(onClick = { switchUser(target.id) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("switch_${target.id}")) { Text("Switch to ${target.displayName}") }
        }
        TextButton(onClick = logout, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Log out / create Demo profile") }
        Text("Inbox · ${notifications.count { !it.isRead }} unread", style = MaterialTheme.typography.titleLarge)
        if (notifications.isEmpty()) Text("Activity updates and waitlist promotions will appear here.")
        notifications.forEach { notification ->
            InfoCard(if (notification.isRead) "Read · ${displayTime(notification.createdAt)}" else "New · ${displayTime(notification.createdAt)}", notification.content)
            if (!notification.isRead) TextButton(onClick = { read(notification.id) }) { Text("Mark as read") }
        }
        Text("Activity history", style = MaterialTheme.typography.titleLarge)
        if (history.isEmpty()) Text("Your past plans will appear here.")
        history.forEach { EventCard(it, state, { openEvent(it.id) }, now) }
    }
}

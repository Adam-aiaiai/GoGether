package com.gogether.app.feature.coordination

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.gogether.app.core.model.Notification
import com.gogether.app.core.ui.*

/** D-owned inbox component, embedded by A. The parent supplies only the signed-in user's rows. */
@Composable
fun NotificationSection(notifications: List<Notification>, busy: Boolean, read: (String) -> Unit, openEvent: (String) -> Unit) {
    Text("Inbox · ${notifications.count { !it.isRead }} unread", style = MaterialTheme.typography.titleLarge)
    if (notifications.isEmpty()) Text("Activity updates and waitlist promotions will appear here.")
    notifications.sortedByDescending { it.createdAt }.forEach { notification ->
        InfoCard(if (notification.isRead) "Read · ${displayTime(notification.createdAt)}" else "New · ${displayTime(notification.createdAt)}", notification.content)
        TextButton(onClick = { openEvent(notification.eventId) }, enabled = !busy) { Text("View activity") }
        if (!notification.isRead) TextButton(onClick = { read(notification.id) }, enabled = !busy) { Text("Mark as read") }
    }
}

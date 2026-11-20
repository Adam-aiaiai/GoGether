package com.gogether.app.feature.coordination

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*

@Composable
fun InboxContent(state: AppSnapshot, busy: Boolean, read: (String) -> Unit,
                 readAll: () -> Unit, openEvent: (String) -> Unit, explore: () -> Unit = {},
                 openQuestion: ((String, String) -> Unit)? = null) {
    var unreadOnly by rememberSaveable(state.currentUserId) { mutableStateOf(false) }
    val all = state.notifications.filter { it.userId == state.currentUserId }.sortedByDescending { it.createdAt }
    val notifications = all.filter { !unreadOnly || !it.isRead }
    Text("Inbox · ${all.count { !it.isRead }} unread", style = MaterialTheme.typography.titleLarge)
    Row {
        FilterChip(!unreadOnly, { unreadOnly = false }, label = { Text("All") }, modifier = Modifier.testTag("inbox_all"))
        FilterChip(unreadOnly, { unreadOnly = true }, label = { Text("Unread") }, modifier = Modifier.testTag("inbox_unread"))
    }
    TextButton(enabled = !busy && all.any { !it.isRead }, onClick = readAll, modifier = Modifier.testTag("read_all")) {
        Text(if (busy) "Updating…" else "Mark all as read")
    }
    if (notifications.isEmpty()) {
        InfoCard(if (unreadOnly) "You're all caught up" else "No notifications yet", "Activity updates and waitlist promotions appear here.")
        TextButton(onClick = explore) { Text("Explore activities") }
    }
    notifications.forEach { notification ->
        InfoCard(if (notification.isRead) "Read · ${displayTime(notification.createdAt)}" else "New · ${displayTime(notification.createdAt)}",
            notification.content, Modifier.testTag("notification_${notification.id}"))
        TextButton(onClick = {
            if (notification.questionId != null && openQuestion != null) openQuestion(notification.eventId, notification.questionId)
            else openEvent(notification.eventId)
        }, modifier = Modifier.testTag("notification_open_${notification.id}")) {
            Text(if (notification.questionId != null && openQuestion != null) "View question" else "View event")
        }
        if (!notification.isRead) TextButton(enabled = !busy, onClick = { read(notification.id) }, modifier = Modifier.testTag("notification_read_${notification.id}")) { Text("Mark as read") }
    }
}

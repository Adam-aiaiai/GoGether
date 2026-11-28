package com.gogether.app.feature.coordination

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import com.gogether.app.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.profile.ParticipantAvatar
import kotlinx.coroutines.launch

internal fun Notification.isInteraction() = type.isInteraction

@Composable
fun InboxContent(state: AppSnapshot, busy: Boolean, read: (String) -> Unit,
    readAll: () -> Unit, openEvent: (String) -> Unit, explore: () -> Unit = {},
    openQuestion: ((String, String) -> Unit)? = null, back: (() -> Unit)? = null, openConversation: ((String) -> Unit)? = null) {
    val all = state.notifications.filter { it.userId == state.currentUserId }.sortedByDescending { it.createdAt }
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        AppPageHeader("Notifications", onBack = back, actions = {
            IconButton(readAll, enabled = !busy && all.any { !it.isRead }, modifier = Modifier.testTag("read_all")) {
                Icon(painterResource(R.drawable.ic_mark_all_read), "Mark all as read", Modifier.size(25.dp))
            }
        })
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            listOf("Interactions", "Activity updates").forEachIndexed { index, title ->
                AppTextTab(title, pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    modifier = Modifier.testTag(if (index == 0) "inbox_interactions" else "inbox_activity"),
                    unread = all.any { !it.isRead && it.isInteraction() == (index == 0) })
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).testTag("inbox_pager"), verticalAlignment = Alignment.Top) { page ->
            val notices = all.filter { it.isInteraction() == (page == 0) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (notices.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (page == 0) "No interactions yet" else "No activity updates yet", fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text(if (page == 0) "Questions, reviews and replies will appear here." else "Changes to your activities will appear here.",
                            Modifier.padding(top = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(explore) { Text("Explore activities", fontSize = 12.sp) }
                    }
                }
                items(notices, key = { it.id }) { notification ->
                    NotificationRow(notification, state, !busy) {
                        if (!notification.isRead) read(notification.id)
                        if (notification.contentId != null && openConversation != null) openConversation(notification.contentId)
                        else if (notification.questionId != null && openQuestion != null) openQuestion(notification.eventId, notification.questionId)
                        else openEvent(notification.eventId)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notice: Notification, state: AppSnapshot, enabled: Boolean, open: () -> Unit) {
    val title = if (notice.isInteraction()) notice.actorName ?: "Activity conversation" else when (notice.type) {
        NotificationType.PROMOTED -> "Your place is confirmed"
        NotificationType.EVENT_CANCELLED -> "Activity cancelled"
        NotificationType.ANNOUNCEMENT -> "Organizer announcement"
        NotificationType.MEETING_UPDATED -> "Meeting point updated"
        NotificationType.EVENT_UPDATED -> "Activity updated"
        else -> "Activity update"
    }
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = open)
        .testTag("notification_open_${notice.id}").padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(7.dp).padding(top = 7.dp), contentAlignment = Alignment.TopCenter) {
            if (!notice.isRead) Box(Modifier.size(6.dp).background(Color(0xFFEF534B), CircleShape).testTag("unread_${notice.id}"))
        }
        if (notice.isInteraction() && notice.actorAvatarUri.isNotBlank()) ParticipantAvatar(title, notice.actorAvatarUri, Modifier.size(38.dp))
        Column(Modifier.weight(1f).testTag("notification_${notice.id}"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Text(java.time.Instant.ofEpochMilli(notice.createdAt).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("d MMM · HH:mm", java.util.Locale.ENGLISH)),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(notice.content, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.event(notice.eventId)?.let { Text(it.title, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = AppUi.OnSoftAccent) }
        }
    }
}

package com.gogether.app.feature.coordination

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import com.gogether.app.core.model.*

/** Participant announcements and the current recipient's older, unlinked AB messages. */
@Composable
fun AnnouncementSection(event: Event, state: AppSnapshot) {
    val member = state.registration(event.id)?.status in setOf(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
    if (state.currentUserId != event.organizerId && !member) return
    state.announcements.filter { it.eventId == event.id }.sortedByDescending { it.createdAt }.forEach {
        PlainMessage("Organizer announcement", it.content, it.createdAt,
            Modifier.testTag("announcement_${it.id}"))
    }
    state.notifications.filter { it.eventId == event.id && it.userId == state.currentUserId &&
        it.type == NotificationType.ANNOUNCEMENT && it.announcementId == null }
        .sortedByDescending { it.createdAt }.forEach {
            PlainMessage("Organizer update", it.content, it.createdAt,
                Modifier.testTag("legacy_announcement_${it.id}"))
        }
}

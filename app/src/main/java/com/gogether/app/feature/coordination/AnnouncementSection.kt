package com.gogether.app.feature.coordination

import androidx.compose.runtime.Composable
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.InfoCard
import com.gogether.app.core.ui.displayTime

/** Participant announcements and the current recipient's older, unlinked AB messages. */
@Composable
fun AnnouncementSection(event: Event, state: AppSnapshot) {
    val member = state.registration(event.id)?.status in setOf(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
    if (state.currentUserId != event.organizerId && !member) return
    state.announcements.filter { it.eventId == event.id }.sortedByDescending { it.createdAt }.forEach {
        InfoCard("Organizer announcement · ${displayTime(it.createdAt)}", it.content)
    }
    state.notifications.filter { it.eventId == event.id && it.userId == state.currentUserId &&
        it.type == NotificationType.ANNOUNCEMENT && it.announcementId == null }
        .sortedByDescending { it.createdAt }.forEach {
            InfoCard("Organizer update · ${displayTime(it.createdAt)}", it.content)
        }
}

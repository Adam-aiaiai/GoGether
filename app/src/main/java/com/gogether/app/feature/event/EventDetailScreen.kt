package com.gogether.app.feature.event

import com.gogether.app.core.ui.AppUi
import com.gogether.app.core.ui.PageActionButton
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.registration.ParticipantsCard
import com.gogether.app.feature.registration.registrationConflicts
import com.gogether.app.feature.coordination.AnnouncementSection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun EventDetailScreen(
    event: Event,
    state: AppSnapshot,
    now: Long,
    busy: Boolean,
    onJoin: () -> Unit,
    onCancel: () -> Unit,
    onManage: () -> Unit,
    onCheckIn: () -> Unit,
    onQuestions: () -> Unit = {},
) {
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    var confirmConflict by rememberSaveable(event.id, state.currentUserId) { mutableStateOf(false) }
    val registration = state.registration(event.id)
    val organizer = state.currentUserId == event.organizerId
    val active = registration?.status == RegistrationStatus.CONFIRMED ||
        registration?.status == RegistrationStatus.WAITLISTED
    val confirmed = state.confirmed(event.id).size
    val remaining = (event.capacity - confirmed).coerceAtLeast(0)
    val conflicts = state.registrationConflicts(event, now)
    val canJoin = !organizer && !active && state.currentUserId != null && event.acceptsRegistration(now)
    LaunchedEffect(canJoin) { if (!canJoin) confirmConflict = false }

    Page {
        EventHero(event)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            StatusPill(event.category.label)
            if (event.experienceRequirement == ExperienceRequirement.NONE) {
                Text("Beginner friendly", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(event.title, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text(event.description, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        DetailFact("▣", detailDate(event.startAt),
            "${detailClock(event.startAt)} – ${detailClock(event.endAt)}")
        AppDivider()
        DetailFact("⌖", event.location, event.city)
        if (!organizer && !active) MeetingMapButton("${event.city} ${event.location}")
        AppDivider()
        ParticipantsCard(event, state)
        DetailFact("♙", "$confirmed of ${event.capacity} spots taken",
            if (state.waitlist(event.id).isEmpty()) "$remaining spots remaining"
            else "${state.waitlist(event.id).size} people on the waitlist")

        Text("Before you join", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailTile("Experience", event.experienceRequirement.detailLabel(), Modifier.weight(1f))
            DetailTile("Guidance", if (event.beginnerGuidance) "Available" else "Not provided",
                Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailTile("Equipment", event.requiredItems.takeIf { it.isNotBlank() }
                ?.let { "Bring: $it" } ?: "Nothing required", Modifier.weight(1f))
            DetailTile("Group size", "Up to ${event.capacity} people", Modifier.weight(1f))
        }
        if (event.spareEquipment.isNotBlank()) {
            DetailTile("Available to borrow", "Spare equipment: ${event.spareEquipment}",
                Modifier.fillMaxWidth())
        }

        Text("About this event", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Text(event.description, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (event.notes.isNotBlank()) {
            Text(event.notes, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text("Hosted by", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        HostRow(state.userName(event.organizerId), state.users.find { it.id == event.organizerId }?.avatarUri.orEmpty())

        if (organizer || active) {
            InfoCard("Meeting point", "${displayTime(event.meetingAt ?: event.startAt)}\n${event.meetingLocation ?: event.location}\n${event.meetingNotes}")
            MeetingMapButton("${event.city} ${event.meetingLocation ?: event.location}")
        }
        AnnouncementSection(event, state)
        PageActionButton(onClick = onQuestions, modifier = Modifier.fillMaxWidth().testTag("open_questions")) {
            Text("Questions & answers (${state.questions.count { it.eventId == event.id }})")
        }

        AppDivider()
        Text("Registration closes ${displayTime(event.registrationDeadline)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Your status: ${registration?.status?.name?.lowercase() ?: "not registered"}",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (registration?.status == RegistrationStatus.WAITLISTED) {
            Text("Your waitlist position: ${state.waitlist(event.id)
                .indexOfFirst { it.userId == state.currentUserId } + 1}",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("waitlist_position"))
        }

        EventActionArea(
            event = event,
            organizer = organizer,
            active = active,
            remaining = remaining,
            busy = busy,
            now = now,
            onManage = onManage,
            onCancel = { confirmCancel = true },
            onJoin = { if (conflicts.isEmpty()) onJoin() else confirmConflict = true },
        )

        if (registration?.status == RegistrationStatus.CONFIRMED &&
            event.status != EventStatus.CANCELLED) {
            PageActionButton(onClick = onCheckIn,
                modifier = Modifier.fillMaxWidth().testTag("open_checkin")) {
                Text(if (event.statusAt(now) == EventStatus.COMPLETED)
                    "Leave feedback" else "Check-in & meeting")
            }
        }
    }

    if (confirmConflict && canJoin) {
        SquareActionDialog(
            onDismiss = { confirmConflict = false },
            title = "Schedule conflict",
            compact = true,
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedPageActionButton(onClick = { confirmConflict = false },
                            modifier = Modifier.testTag("dismiss_conflicting_join")) { Text("Go back") }
                    }
                    Box(Modifier.weight(1f)) {
                        PageActionButton(onClick = { confirmConflict = false; onJoin() }, enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("confirm_conflicting_join")) {
                            Text(if (remaining == 0) "Join waitlist anyway" else "Join anyway")
                        }
                    }
                }
            },
            content = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This event overlaps with your other activities. You can still join if you choose.")
                    conflicts.forEach { other ->
                        val status = when {
                            other.organizerId == state.currentUserId -> "Hosting"
                            state.registration(other.id)?.status == RegistrationStatus.WAITLISTED -> "Waitlisted (place not guaranteed)"
                            else -> "Confirmed"
                        }
                        Text("${other.title}\n${displayTime(other.startAt)} – ${displayTime(other.endAt)}\n$status")
                    }
                    if (remaining == 0) Text("This event is full. Continuing will add you to its waitlist.")
                }
            },
        )
    }

    if (confirmCancel) {
        val waitlisted = registration?.status == RegistrationStatus.WAITLISTED
        SquareActionDialog(
            title = if (waitlisted) "Leave the waitlist?" else "Cancel your registration?",
            onDismiss = { confirmCancel = false },
            modifier = Modifier.testTag("registration_cancel_dialog"),
            compact = true,
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedPageActionButton(onClick = { confirmCancel = false },
                            modifier = Modifier.testTag("dismiss_registration_cancel")) {
                            Text(if (waitlisted) "Stay on list" else "Keep my place")
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        PageActionButton(onClick = { confirmCancel = false; onCancel() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("confirm_registration_cancel")) {
                            Text(if (waitlisted) "Leave waitlist" else "Yes, cancel")
                        }
                    }
                }
            },
        ) {
            Text(if (waitlisted) "You will lose your current position in the queue."
                else "Your place may go to the first person on the waitlist.")
        }
    }
}

@Composable
private fun EventHero(event: Event) {
    if (event.imageUri.isNotBlank()) {
        EventImage(event.imageUri, event.title, Modifier.fillMaxWidth().height(235.dp))
    } else {
        Box(Modifier.fillMaxWidth().height(190.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, AppUi.CardShape),
            contentAlignment = Alignment.Center) {
            Text(event.category.symbol, style = MaterialTheme.typography.displayLarge)
        }
    }
}

@Composable
private fun DetailFact(icon: String, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(icon, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailTile(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = AppUi.CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HostRow(name: String, avatarUri: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        com.gogether.app.feature.profile.ParticipantAvatar(name, avatarUri, Modifier.size(44.dp))
        Column {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text("Event organizer", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EventActionArea(
    event: Event,
    organizer: Boolean,
    active: Boolean,
    remaining: Int,
    busy: Boolean,
    now: Long,
    onManage: () -> Unit,
    onCancel: () -> Unit,
    onJoin: () -> Unit,
) {
    Surface(shape = AppUi.CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (remaining == 0) "Waitlist available" else "$remaining spots left",
                    fontWeight = FontWeight.Bold)
                Text(if (event.acceptsRegistration(now)) "Free to join"
                    else event.statusAt(now).name.lowercase().replaceFirstChar(Char::uppercase),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                organizer -> Button(onClick = onManage,
                    modifier = Modifier.testTag("manage_event")) { Text("Manage") }
                active && event.status == EventStatus.OPEN && now < event.startAt ->
                    OutlinedButton(onClick = onCancel, enabled = !busy,
                        modifier = Modifier.testTag("cancel_registration")) {
                        Text("Cancel")
                    }
                !active && event.acceptsRegistration(now) ->
                    Button(onClick = onJoin, enabled = !busy,
                        modifier = Modifier.testTag("join_event")) {
                        Text(if (remaining == 0) "Join Waitlist" else "Join Event")
                    }
                else -> Text("Registration closed", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun ExperienceRequirement.detailLabel(): String = when (this) {
    ExperienceRequirement.NONE -> "All levels"
    ExperienceRequirement.BASIC -> "Some experience"
    ExperienceRequirement.EXPERIENCED -> "Experienced only"
}

private fun detailDate(epoch: Long): String = Instant.ofEpochMilli(epoch)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEEE, MMMM d", java.util.Locale.ENGLISH))

private fun detailClock(epoch: Long): String = Instant.ofEpochMilli(epoch)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
fun MeetingMapButton(location: String) {
    val context = LocalContext.current
    var error by remember { mutableStateOf(false) }
    PageActionButton(onClick = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                "geo:0,0?q=${Uri.encode(location)}".toUri()))
        } catch (_: ActivityNotFoundException) {
            error = true
        }
    }, modifier = Modifier.fillMaxWidth()) {
        Text("Open meeting location in Maps")
    }
    if (error) {
        Text("No maps app is installed. Use the meeting address shown above.",
            color = MaterialTheme.colorScheme.error)
    }
}

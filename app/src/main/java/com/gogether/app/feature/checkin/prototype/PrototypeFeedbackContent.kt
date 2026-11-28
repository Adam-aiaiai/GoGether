package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppDivider
import com.gogether.app.core.ui.AppUi
import com.gogether.app.core.ui.PageActionButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gogether.app.feature.checkin.FeedbackInput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun reviewTime(time: Long) = SimpleDateFormat("MMM d, HH:mm", Locale.ENGLISH).format(Date(time))

/** A compact activity summary and scrollable form, with the submit action above the keyboard. */
@Composable
internal fun HistoryDetail(actor: String, entry: PrototypeHistoryEntry, now: Long, busy: Boolean, message: String,
    submit: (String, Int, String, FeedbackDetails) -> Unit, modifier: Modifier, openCheckIn: (PrototypeSession) -> Unit,
    openAttendance: (PrototypeSession) -> Unit, openReviews: (PrototypeSession) -> Unit, pendingCheckIn: Boolean = false,
    back: () -> Unit = {}, reviewSummary: ReviewEventSummary? = null) {
    val context = LocalContext.current.applicationContext
    val activity = LocalCheckInActivities.current.infoOrNull(entry.session)
    val drafts = remember(context) { FeedbackDraftStore(context) }
    val initial = remember(actor, entry.session.id) { runCatching { drafts.load(actor, entry.session.id) } }
    var draft by remember(actor, entry.session.id) { mutableStateOf(initial.getOrDefault(FeedbackDraft())) }
    var draftError by remember(actor, entry.session.id) { mutableStateOf(if (initial.isFailure) "Could not load draft. Go back and try again." else "") }
    fun updateDraft(change: (FeedbackDraft) -> FeedbackDraft) {
        draft = change(draft)
        draftError = runCatching { drafts.save(actor, entry.session.id, draft) }
            .exceptionOrNull()?.message.orEmpty()
    }
    val rating = draft.rating
    val comment = draft.comment
    val (organization, venue, atmosphere, tags, anonymous, visibility, photos) = draft.details
    val photoActions = rememberFeedbackPhotoActions(actor, entry.session.id, photos, busy) { ids ->
        updateDraft { it.copy(details = it.details.copy(photos = ids.toList())) }
    }
    val blocked = busy || photoActions.importing
    fun toggleTag(id: String) = updateDraft { current ->
        val selected = current.details.tags
        current.copy(details = current.details.copy(tags = if (id in selected) selected - id else selected + id))
    }
    LaunchedEffect(actor, entry.session.id, entry.feedback) {
        withContext(Dispatchers.IO) {
            FeedbackPhotoStore(context).cleanUnreferenced(actor, entry.session.id)
        }
    }
    val canReview = entry.canReview(now)
    if (canReview) {
        FeedbackEditor("$actor/${entry.session.id}", reviewSummary ?: ReviewEventSummary(
            activity?.title ?: "Event details unavailable", activity?.organizerName ?: "Organizer", entry.session.startAt),
            draft, blocked, draftError, message, { value -> updateDraft { value } }, { updateDraft { it } },
            submit = { submit(entry.session.id, rating, comment, draft.details) }, back = back,
            reviews = { openReviews(entry.session) }, modifier = modifier, allowAnonymous = activity?.organizerId != actor,
            extraActions = {
                if (activity?.organizerId == actor) DropdownMenuItem(text = { Text("View attendance") },
                    onClick = { openAttendance(entry.session) }, modifier = Modifier.testTag("history_open_attendance"))
                if (pendingCheckIn) DropdownMenuItem(text = { Text("Check unconfirmed check-in") },
                    onClick = { openCheckIn(entry.session) }, modifier = Modifier.testTag("history_resolve_checkin"))
            }, photos = {
                FeedbackPhotoStrip(photos, actor, entry.session.id, blocked, photoActions.add, photoActions.remove, compact = true)
                if (photoActions.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (photoActions.error.isNotBlank()) Text(photoActions.error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("feedback_photo_error"))
            })
        return
    }
    Column(modifier.fillMaxWidth()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("history_detail"),
            verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CheckInStyle.shape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DateRange, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(activity?.title ?: "Event details unavailable", style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Hosted by ${activity?.organizerName ?: "Unknown organizer"}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("${reviewTime(entry.session.startAt)} — ${SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(entry.session.endAt))}",
                    style = MaterialTheme.typography.bodyMedium)
                Text(activity?.meetingName ?: "Meeting point not confirmed", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                if (pendingCheckIn) TextButton(onClick = { openCheckIn(entry.session) }, enabled = !blocked,
                    shape = CheckInStyle.shape, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.testTag("history_resolve_checkin")) { Text("Check-in unconfirmed. Check result >") }
                else Text(entry.receipt?.let { "Checked in  ${reviewTime(it.checkedInAt)}" } ?: "Not checked in",
                    Modifier.testTag("history_checkin"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (activity?.organizerId == actor) item {
                PageActionButton(onClick = { openAttendance(entry.session) }, modifier = Modifier.testTag("history_open_attendance")) { Text("View attendance") }
            }
            item { AppDivider(horizontalInset = 16.dp, color = MaterialTheme.colorScheme.outlineVariant) }
            when {
                entry.feedback != null -> {
                    item {
                        SavedReviewItem("You", true, entry.feedback.rating, entry.feedback.comment,
                            entry.feedback.createdAt, entry.feedback.details, Modifier.testTag("feedback_saved"),
                            avatarUri = LocalCheckInActivities.current.avatarUri(actor), anonymousName = entry.feedback.anonymousName, organizer = activity?.organizerId == actor) {
                            FeedbackPhotoStrip(entry.feedback.details.photos, actor, entry.session.id, showHeader = false)
                        }
                        com.gogether.app.feature.social.SocialContentActions(entry.feedback.contentId)
                    }
                }
                entry.cancelled -> item { Text("This event is cancelled. You cannot review it.") }
                !entry.eligible -> item { Text("Only confirmed participants can review this event.") }
                !entry.hasEnded(now) -> item {
                    Text(if (entry.receipt == null) "Check in during the event to leave a review after it ends."
                        else "Review after the event ends. No need to scan again.")
                    Text("Ends: ${reviewTime(entry.session.endAt)}", style = MaterialTheme.typography.bodySmall)
                    PageActionButton(onClick = { openCheckIn(entry.session) }, enabled = !blocked,
                        modifier = Modifier.testTag("history_open_checkin")) { Text("Open event check-in") }
                }
                entry.receipt == null -> item { Text("You did not check in. A valid check-in is required to review this event.") }
                else -> Unit
            }

            item {
                TextButton(onClick = { openReviews(entry.session) }, enabled = !blocked,
                    modifier = Modifier.testTag("feedback_open_reviews")) { Text("View event reviews >") }
            }
        }
    }
}

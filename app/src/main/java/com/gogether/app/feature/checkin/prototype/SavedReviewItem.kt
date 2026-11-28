package com.gogether.app.feature.checkin.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gogether.app.core.ui.AppUi
import com.gogether.app.feature.profile.ParticipantAvatar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One review stays together; callers supply only the already-authorized photo content. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SavedReviewItem(authorName: String, own: Boolean, rating: Int, comment: String,
    createdAt: Long, details: FeedbackDetails, modifier: Modifier = Modifier, avatarUri: String = "",
    anonymousName: String = "", organizer: Boolean = false,
    photos: @Composable () -> Unit = {}) {
    val name = if (details.anonymous) anonymousName.takeIf { Regex("Anonymous participant [0-9]+").matches(it) } ?: "Anonymous participant" else authorName.ifBlank { "Participant" }
    val displayName = name + if (own && name != "You") " (you)" else ""
    val submitted = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault())
    val date = submitted.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
    val time = submitted.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top) {
            if (!details.anonymous) ParticipantAvatar(name, avatarUri, Modifier.size(40.dp))
            else Surface(Modifier.size(40.dp), shape = CircleShape,
                color = if (details.anonymous) AppUi.Primary else AppUi.SoftAccent) {
                Box(contentAlignment = Alignment.Center) {
                    if (details.anonymous) Icon(Icons.Default.Person, null, Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onPrimary)
                    else Text(name.take(1).uppercase(Locale.ENGLISH), style = MaterialTheme.typography.titleLarge,
                        color = AppUi.OnSoftAccent)
                }
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val stackDate = maxWidth < 230.dp || LocalDensity.current.fontScale > 1.2f
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top) {
                        Text(displayName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        if (!stackDate) Text(date,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End)
                    }
                    if (organizer && !details.anonymous) Text("Organizer", style = MaterialTheme.typography.labelSmall)
                    if (stackDate) Text("$date · $time", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top) {
                        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.semantics { contentDescription = "$rating out of 5 stars" },
                                horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                (1..5).forEach { score -> Icon(Icons.Default.Star, null, Modifier.size(18.dp),
                                    tint = if (score <= rating) AppUi.Primary else MaterialTheme.colorScheme.outlineVariant) }
                            }
                            Text("$rating.0 ·", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (details.visibility == FeedbackVisibility.PUBLIC) "Public" else "Organizer only",
                                Modifier.testTag("feedback_privacy_saved"), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!stackDate) Text(time,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End)
                    }
                }
            }
        }
        if (comment.isNotBlank()) Text(comment, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface)
        if (details.photos.isNotEmpty()) photos()
        SavedFeedbackDetails(details)
    }
}

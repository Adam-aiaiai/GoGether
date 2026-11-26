package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppPageHeader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Receives only access-filtered DTOs, never raw author IDs. */
@Composable
internal fun ActivityReviewsScreen(actor: String, session: PrototypeSession, reviews: List<VisibleFeedback>,
    busy: Boolean, error: String, back: () -> Unit, refresh: () -> Unit) {
    val activity = LocalCheckInActivities.current.infoOrNull(session)
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("activity_reviews")) {
        AppPageHeader("Event review", onBack = back, actions = {
            TextButton(onClick = refresh, enabled = !busy) { Text("Refresh") }
        })
        Text(activity?.title ?: "Event details unavailable", style = MaterialTheme.typography.titleMedium)
        Text(if (activity?.organizerId == actor) "Public reviews and feedback shared with you" else "Public reviews and your own feedback",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = refresh, enabled = !busy) { Text("Retry") }
        } else if (!busy && reviews.isEmpty()) Text("No reviews to show yet", Modifier.padding(vertical = 24.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(reviews) { index, review ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("activity_review_$index")) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(if (review.anonymous) "A" else review.authorName.take(1)) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(review.authorName + if (review.own) " (you)" else "", style = MaterialTheme.typography.titleSmall)
                            Text(SimpleDateFormat("MMM d, HH:mm", Locale.ENGLISH).format(Date(review.createdAt)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${review.rating} / 5", color = MaterialTheme.colorScheme.primary)
                    }
                    if (review.details.visibility == FeedbackVisibility.ORGANIZER_ONLY) Text("Organizer only",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    SavedFeedbackDetails(review.details)
                    if (review.comment.isNotBlank()) Text(review.comment)
                    if (review.details.photos.isNotEmpty()) FeedbackPhotoStrip(review.details.photos, actor, session.id)
                    HorizontalDivider()
                }
            }
        }
    }
}

package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppPageHeader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Receives only access-filtered DTOs, never raw author IDs. */
@Composable
internal fun ActivityReviewsScreen(actor: String, session: PrototypeSession, reviews: List<VisibleFeedback>,
    busy: Boolean, error: String, back: () -> Unit, refresh: () -> Unit) {
    val activity = LocalCheckInActivities.current.infoOrNull(session)
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().testTag("activity_reviews")) {
        AppPageHeader("Event reviews", onBack = back, actions = {
            TextButton(onClick = refresh, enabled = !busy) { Text("Refresh") }
        })
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(activity?.title ?: "Event details unavailable", style = MaterialTheme.typography.titleMedium)
            Text(if (activity?.organizerId == actor) "Public reviews and feedback shared with you" else "Public reviews and your own feedback",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = refresh, enabled = !busy) { Text("Retry") }
        } else if (!busy && reviews.isEmpty()) Text("No reviews to show yet", Modifier.padding(vertical = 24.dp))
        LazyColumn(Modifier.weight(1f).testTag("activity_reviews_list"), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(50.dp)) {
            itemsIndexed(reviews) { index, review ->
                SavedReviewItem(review.authorName, review.own, review.rating, review.comment, review.createdAt,
                    review.details, Modifier.testTag("activity_review_$index")) {
                    FeedbackPhotoStrip(review.details.photos, actor, session.id, showHeader = false)
                }
            }
        }
    }
}

package com.gogether.app.feature.checkin.prototype

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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("< 返回") }
            Text("活动评价", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = refresh, enabled = !busy) { Text("刷新") }
        }
        Text(activity?.title ?: "活动资料不可用", style = MaterialTheme.typography.titleMedium)
        Text(if (activity?.organizerId == actor) "公开评价及发给你的反馈" else "公开评价及自己的反馈",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = refresh, enabled = !busy) { Text("重试") }
        } else if (!busy && reviews.isEmpty()) Text("暂时没有可查看的评价", Modifier.padding(vertical = 24.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(reviews) { index, review ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("activity_review_$index")) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(if (review.anonymous) "匿" else review.authorName.take(1)) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(review.authorName + if (review.own) "（我）" else "", style = MaterialTheme.typography.titleSmall)
                            Text(SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(review.createdAt)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${review.rating} / 5", color = MaterialTheme.colorScheme.primary)
                    }
                    if (review.details.visibility == FeedbackVisibility.ORGANIZER_ONLY) Text("仅组织者可见",
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

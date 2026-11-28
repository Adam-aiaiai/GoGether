package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppFilterChip
import com.gogether.app.core.ui.AppUi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FeedbackDimension(label: String, key: String, rating: Int, busy: Boolean, choose: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("feedback_dimension_$key"), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).testTag("feedback_dimension_label_$key"), style = MaterialTheme.typography.bodyMedium)
        RatingStars(label, key, rating, busy, Modifier.width(160.dp), choose)
    }
}

@Composable
internal fun FeedbackTags(improvement: Boolean, selected: List<String>, busy: Boolean, toggle: (String) -> Unit) {
    Column(Modifier.testTag(if (improvement) "feedback_improvements" else "feedback_likes")) {
        SectionLabel(if (improvement) "What could improve" else "What went well", if (improvement) "Optional" else "Choose any")
        feedbackTags.filter { it.improvement == improvement }.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { tag ->
                    AppFilterChip(selected = tag.id in selected, onClick = { toggle(tag.id) }, enabled = !busy,
                        label = { Text(tag.label, Modifier.fillMaxWidth(), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                        modifier = Modifier.weight(1f).testTag("feedback_tag_${tag.id}"))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SavedFeedbackDetails(details: FeedbackDetails) {
    val dimensions = listOf("Organization" to details.organization, "Venue" to details.venue,
        "Atmosphere" to details.atmosphere).filter { it.second > 0 }
    if (dimensions.isEmpty() && details.tags.isEmpty() && details.positiveComment.isBlank() &&
        details.improvementComment.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(false, true).forEach { improvement ->
            val selected = feedbackTags.filter { it.improvement == improvement && it.id in details.tags }
            val note = if (improvement) details.improvementComment else details.positiveComment
            if (selected.isNotEmpty() || note.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (improvement || note.isNotBlank()) Text(if (improvement) "What could improve" else "What went well",
                        style = MaterialTheme.typography.titleSmall)
                    if (selected.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        selected.forEach { tag ->
                            Surface(shape = CircleShape,
                                color = if (improvement) MaterialTheme.colorScheme.surfaceContainerLow else AppUi.SelectedTag) {
                                Text(tag.label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!improvement && dimensions.isNotEmpty()) Text(
                dimensions.joinToString(" · ") { (label, score) -> "$label $score" },
                Modifier.testTag("saved_review_dimensions"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

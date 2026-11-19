package com.gogether.app.feature.checkin.prototype

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

@Composable
internal fun FeedbackDimension(label: String, key: String, rating: Int, busy: Boolean, choose: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(if (rating == 0) "Not rated" else "$rating / 5", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            (1..5).forEach { score ->
                Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(CheckInStyle.shape)
                    .selectable(rating == score, enabled = !busy, role = Role.RadioButton, onClick = { choose(score) })
                    .semantics { contentDescription = "$label: $score out of 5" }.testTag("feedback_${key}_$score"),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, null, Modifier.size(26.dp),
                        tint = if (score <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackTags(improvement: Boolean, selected: List<String>, busy: Boolean, toggle: (String) -> Unit) {
    Column(Modifier.testTag(if (improvement) "feedback_improvements" else "feedback_likes")) {
        Text(if (improvement) "What could improve" else "What went well", style = MaterialTheme.typography.titleMedium)
        Text("Optional. Select any that apply.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            feedbackTags.filter { it.improvement == improvement }.forEach { tag ->
                FilterChip(selected = tag.id in selected, onClick = { toggle(tag.id) }, enabled = !busy,
                    label = { Text(tag.label) }, shape = CheckInStyle.shape,
                    border = null, colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier.testTag("feedback_tag_${tag.id}"))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SavedFeedbackDetails(details: FeedbackDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val dimensions = listOf("Organization" to details.organization, "Venue" to details.venue, "Atmosphere" to details.atmosphere)
        if (dimensions.any { it.second > 0 }) {
            Text("Experience ratings", style = MaterialTheme.typography.titleMedium)
            dimensions.filter { it.second > 0 }.forEach { (label, score) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text("$score / 5", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        listOf(false, true).forEach { improvement ->
            val selected = feedbackTags.filter { it.improvement == improvement && it.id in details.tags }
            if (selected.isNotEmpty()) {
                Text(if (improvement) "What could improve" else "What went well", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selected.forEach { tag ->
                        Surface(shape = CheckInStyle.shape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(tag.label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

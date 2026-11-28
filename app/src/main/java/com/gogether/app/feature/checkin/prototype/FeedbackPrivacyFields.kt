package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppDivider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun FeedbackPrivacyFields(anonymous: Boolean, visibility: FeedbackVisibility, busy: Boolean,
    changeAnonymous: (Boolean) -> Unit, changeVisibility: (FeedbackVisibility) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("feedback_privacy")) {
        AppDivider()
        Row(Modifier.fillMaxWidth().toggleable(anonymous, enabled = !busy, role = Role.Checkbox, onValueChange = changeAnonymous)
            .testTag("feedback_anonymous"), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = anonymous, onCheckedChange = null, enabled = !busy)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("Anonymous review", style = MaterialTheme.typography.titleSmall)
                Text("Hide your name and avatar from others", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (anonymous) Text("Personal details in your text and photos will not be hidden automatically.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Who can see this?", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            FeedbackVisibility.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().selectable(visibility == option, enabled = !busy,
                    role = Role.RadioButton, onClick = { changeVisibility(option) }).testTag("feedback_visibility_${option.name}"),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(visibility == option, onClick = null, enabled = !busy)
                    Column(Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(if (option == FeedbackVisibility.PUBLIC) "Public review" else "Organizer only", style = MaterialTheme.typography.bodyLarge)
                        Text(if (option == FeedbackVisibility.PUBLIC) "Help others learn about the event" else "Share with the organizer, not other participants",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FeedbackPrivacySummary(details: FeedbackDetails) {
    Text("${if (details.anonymous) "Anonymous review" else "Named review"}  |  ${if (details.visibility == FeedbackVisibility.PUBLIC) "Public" else "Organizer only"}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("feedback_privacy_saved"))
}

package com.gogether.app.feature.checkin.prototype

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
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().toggleable(anonymous, enabled = !busy, role = Role.Checkbox, onValueChange = changeAnonymous)
            .testTag("feedback_anonymous"), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = anonymous, onCheckedChange = null, enabled = !busy)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("匿名评价", style = MaterialTheme.typography.titleSmall)
                Text("对其他人隐藏头像和姓名", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (anonymous) Text("文字和图片中的个人信息不会自动隐藏。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("谁可以看到", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            FeedbackVisibility.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().selectable(visibility == option, enabled = !busy,
                    role = Role.RadioButton, onClick = { changeVisibility(option) }).testTag("feedback_visibility_${option.name}"),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(visibility == option, onClick = null, enabled = !busy)
                    Column(Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(if (option == FeedbackVisibility.PUBLIC) "公开评价" else "仅组织者可见", style = MaterialTheme.typography.bodyLarge)
                        Text(if (option == FeedbackVisibility.PUBLIC) "让其他同学了解活动体验" else "反馈给组织者，其他参与者看不到",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FeedbackPrivacySummary(details: FeedbackDetails) {
    Text("${if (details.anonymous) "匿名评价" else "实名评价"}  |  ${if (details.visibility == FeedbackVisibility.PUBLIC) "公开" else "仅组织者可见"}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("feedback_privacy_saved"))
}

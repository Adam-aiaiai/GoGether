package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppUi
import com.gogether.app.core.ui.OutlinedPageActionButton
import com.gogether.app.core.ui.PageActionButton
import com.gogether.app.core.ui.SquareActionDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared only by the check-in flow. A visual approximation of the supplied reference cards. */
internal object CheckInStyle {
    val cornerRadius = 16.dp
    val shape = AppUi.CardShape
    val sheetShape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
}

@Composable
internal fun CheckInInfoCard(title: String, text: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = CheckInStyle.shape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CheckInConfirmDialog(title: String, description: String, action: String,
    onDismiss: () -> Unit, onConfirm: () -> Unit) {
    CheckInActionDialog(title, description, action, onDismiss,
        onConfirm = { onDismiss(); onConfirm() }, dismissLabel = "Keep it")
}

/** Presentation only: each caller retains its original confirmation and dismissal callbacks. */
@Composable
internal fun CheckInActionDialog(title: String, description: String, action: String,
    onDismiss: () -> Unit, onConfirm: () -> Unit, dismissLabel: String = "Cancel",
    confirmEnabled: Boolean = true) {
    SquareActionDialog(title = title, onDismiss = onDismiss, compact = true,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedPageActionButton(onClick = onDismiss) { Text(dismissLabel) }
                }
                Box(Modifier.weight(1f)) {
                    PageActionButton(onClick = onConfirm, enabled = confirmEnabled,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(action)
                    }
                }
            }
        }) { Text(description) }
}

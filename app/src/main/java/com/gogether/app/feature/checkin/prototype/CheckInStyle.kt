package com.gogether.app.feature.checkin.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared only by the check-in flow. A visual approximation of the supplied reference cards. */
internal object CheckInStyle {
    val cornerRadius = 12.dp
    val shape = RoundedCornerShape(cornerRadius)
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
    AlertDialog(shape = CheckInStyle.shape, onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(shape = CheckInStyle.shape, onClick = { onDismiss(); onConfirm() }) { Text(action) } },
        dismissButton = { TextButton(shape = CheckInStyle.shape, onClick = onDismiss) { Text("Keep it") } })
}

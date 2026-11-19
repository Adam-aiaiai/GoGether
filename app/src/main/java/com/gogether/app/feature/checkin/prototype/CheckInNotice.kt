package com.gogether.app.feature.checkin.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun CheckInNotice(notice: PrototypeNotice, dismiss: (Long) -> Unit, modifier: Modifier = Modifier) {
    val accessibility = LocalAccessibilityManager.current
    val currentDismiss by rememberUpdatedState(dismiss)
    LaunchedEffect(notice.id) {
        delay(accessibility?.calculateRecommendedTimeoutMillis(2_500, containsIcons = true,
            containsText = true, containsControls = false) ?: 2_500)
        currentDismiss(notice.id)
    }
    Surface(modifier.padding(20.dp).widthIn(max = 360.dp).testTag("checkin_notice")
        .semantics { liveRegion = LiveRegionMode.Polite },
        shape = CheckInStyle.shape, color = Color(0xCC303030), contentColor = Color.White) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Check, null, Modifier.size(20.dp))
            Text(notice.text, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

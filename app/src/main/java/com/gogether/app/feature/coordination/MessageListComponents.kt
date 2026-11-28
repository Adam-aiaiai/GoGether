package com.gogether.app.feature.coordination

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val messageTimeFormat = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.ENGLISH)

@Composable
internal fun MessageHeading(title: String, time: Long, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text(messageTimeFormat.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())),
            fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PlainMessage(title: String, message: String, time: Long, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        MessageHeading(title, time)
        Text(message, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

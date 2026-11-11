package com.gogether.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.Event
import com.gogether.app.core.model.EventCounts
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val eventDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm")
fun Event.displayDate(): String = eventDateFormatter.format(startAt.atZone(ZoneId.systemDefault()))

@Composable
fun DemoBadge(modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(50)) {
        Text("DEMO MODE", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EventCard(event: Event, counts: EventCounts, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = onClick, label = { Text(event.category) })
                Spacer(Modifier.weight(1f))
                Text("${(event.maxParticipants - counts.confirmed).coerceAtLeast(0)} spots", style = MaterialTheme.typography.labelMedium)
            }
            Text(event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(event.displayDate(), style = MaterialTheme.typography.bodyMedium)
            Text(event.meetingLocation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (counts.waitlisted > 0) Text("${counts.waitlisted} on waitlist", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun PageTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

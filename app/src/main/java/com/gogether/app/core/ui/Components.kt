package com.gogether.app.core.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun displayTime(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm"))

@Composable
fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun InfoCard(title: String, text: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusPill(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun EventImage(imageUri: String, contentDescription: String, modifier: Modifier = Modifier) {
    if (imageUri.isBlank()) return
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, imageUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(imageUri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri).use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sampleSize = 1
                while (bounds.outWidth / sampleSize > 1_600 || bounds.outHeight / sampleSize > 1_600) {
                    sampleSize *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                context.contentResolver.openInputStream(uri).use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(it, contentDescription, contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(20.dp)))
    }
}

@Composable
fun EventCard(event: Event, state: AppSnapshot, onClick: () -> Unit, now: Long = System.currentTimeMillis()) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("event_${event.id}"),
        shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EventImage(event.imageUri, event.title, Modifier.fillMaxWidth().height(140.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Text(event.category.symbol, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(event.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(event.title, style = MaterialTheme.typography.titleLarge)
                }
            }
            Text(displayTime(event.startAt), style = MaterialTheme.typography.bodyMedium)
            Text("${event.city} · ${event.location}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (event.experienceRequirement == ExperienceRequirement.NONE) StatusPill("Beginner friendly")
                if (event.beginnerGuidance) StatusPill("Guidance provided")
                if (event.spareEquipment.isNotBlank()) StatusPill("Spare gear")
            }
            val remaining = (event.capacity - state.confirmed(event.id).size).coerceAtLeast(0)
            val availability = when {
                event.statusAt(now) != EventStatus.OPEN -> event.statusAt(now).name.lowercase().replaceFirstChar { it.uppercase() }
                now >= event.endAt -> "Ended"
                now >= event.startAt -> "In progress"
                !event.acceptsRegistration(now) -> "Registration closed"
                remaining == 0 -> "Full · waitlist open"
                else -> "$remaining places left"
            }
            Text(availability, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            state.registration(event.id)?.let { StatusPill(it.status.name.lowercase().replaceFirstChar(Char::uppercase)) }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, description: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } })
}

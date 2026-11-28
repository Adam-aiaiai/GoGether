package com.gogether.app.core.ui

import com.gogether.app.core.ui.AppUi
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gogether.app.core.model.*
import com.gogether.app.GogetherApplication
import com.gogether.app.core.data.cloud.CloudOperations
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun displayTime(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", java.util.Locale.ENGLISH))

@Composable
fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun InfoCard(title: String, text: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = AppUi.CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusPill(text: String) {
    Surface(shape = AppUi.TagShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EventImage(imageUri: String, contentDescription: String, modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = AppUi.CardShape, contentScale: ContentScale = ContentScale.Crop) {
    if (imageUri.isBlank()) return
    val context = LocalContext.current
    val repository = (context.applicationContext as? GogetherApplication)?.repository
    val cloud = repository as? CloudOperations
    val actor = repository?.state?.collectAsStateWithLifecycle()?.value?.currentUserId
    val bitmap by produceState<ImageBitmap?>(null, imageUri, actor) {
        value = null
        value = withContext(Dispatchers.IO) {
            try {
                if (imageUri.startsWith("covers/") || imageUri.startsWith("profiles/") || imageUri.startsWith("gs://")) {
                    val storage = requireNotNull(cloud) { "Sign in to load this image." }.storage
                    val ref = if (imageUri.startsWith("gs://")) storage.getReferenceFromUrl(imageUri) else storage.reference.child(imageUri)
                    val bytes = ref.getBytes(2L * 1024 * 1024).await()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
                    return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                }
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
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }
    bitmap?.let {
        Image(it, contentDescription, contentScale = contentScale,
            modifier = modifier.clip(shape))
    }
}

@Composable
fun EventCard(event: Event, state: AppSnapshot, onClick: () -> Unit, now: Long = System.currentTimeMillis(), showRating: Boolean = false) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("event_${event.id}"),
        shape = AppUi.CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column {
            if (event.imageUri.isNotBlank()) {
                EventImage(event.imageUri, event.title, Modifier.fillMaxWidth().height(190.dp))
            } else {
                Box(Modifier.fillMaxWidth().height(118.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center) {
                    Text(event.category.symbol, style = MaterialTheme.typography.displayMedium)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${event.category.label.uppercase()} · ${displayTime(event.startAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (event.experienceRequirement == ExperienceRequirement.NONE) {
                    Text("Beginner Friendly", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(event.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if(showRating) EventRatingBadge(state.eventRatings[event.id],event.id)
            }
            Text("⌖ ${event.location}   ⚉ ${state.confirmed(event.id).size}/${event.capacity} joined",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (event.beginnerGuidance) StatusPill("Guidance provided")
                if (event.requiredItems.isNotBlank()) StatusPill("Bring equipment")
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
}

@Composable
fun ConfirmDialog(title: String, description: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    SquareActionDialog(title = title, onDismiss = onDismiss, compact = true,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedPageActionButton(onClick = onDismiss) { Text("Keep it") }
                }
                Box(Modifier.weight(1f)) {
                    PageActionButton(onClick = { onDismiss(); onConfirm() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(action)
                    }
                }
            }
        }) { Text(description) }
}

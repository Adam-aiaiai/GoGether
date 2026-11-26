package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppMediaAddTile
import com.gogether.app.core.ui.AppMediaThumbnail
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class FeedbackPhotoActions(val importing: Boolean, val error: String, val add: () -> Unit, val remove: (String) -> Unit)

/** Kept outside lazy items so scrolling cannot dispose an in-flight picker/import. */
@Composable
internal fun rememberFeedbackPhotoActions(actor: String, sessionId: String, photos: List<String>, busy: Boolean,
    update: (List<String>) -> Unit): FeedbackPhotoActions {
    val context = LocalContext.current.applicationContext
    val activities = LocalCheckInActivities.current
    val store = remember(context, activities) { FeedbackPhotoStore(context, activities) }
    val scope = rememberCoroutineScope()
    var importing by remember(actor, sessionId) { mutableStateOf(false) }
    var error by remember(actor, sessionId) { mutableStateOf("") }
    val latestPhotos by rememberUpdatedState(photos)
    val latestBusy by rememberUpdatedState(busy)
    val latestScope by rememberUpdatedState("$actor/$sessionId")
    var pendingScope by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        val request = pendingScope
        pendingScope = null
        if (request == latestScope && !latestBusy && uris.isNotEmpty()) {
            scope.launch {
                importing = true; error = ""
                try {
                    val added = store.importUris(actor, sessionId, uris, 3 - latestPhotos.size)
                    if (request == latestScope) update(latestPhotos + added)
                    else withContext(Dispatchers.IO) { added.forEach { store.removeDraft(it, actor, sessionId) } }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Could not load the photo. Choose it again." }
                finally { importing = false }
            }
        }
    }
    return FeedbackPhotoActions(importing, error, add = {
        if (!busy && !importing && pendingScope == null && photos.size < 3) {
            error = ""; pendingScope = latestScope
            try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            catch (_: Exception) { pendingScope = null; error = "Could not open the photo picker. Try again later." }
        }
    }, remove = { id ->
        if (!busy && !importing) {
            update(photos - id)
            scope.launch(Dispatchers.IO) { store.removeDraft(id, actor, sessionId) }
        }
    })
}

@Composable
internal fun FeedbackPhotoStrip(photos: List<String>, viewer: String, sessionId: String, busy: Boolean = false,
    add: (() -> Unit)? = null, remove: ((String) -> Unit)? = null, compact: Boolean = false, addTag: String = "feedback_add_photo") {
    var preview by remember(viewer, sessionId, photos.toList()) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (compact) "Photos" else if (add != null) "Event photos (optional)" else "Event photos", style = MaterialTheme.typography.titleMedium)
            Text("${photos.size} / 3", style = MaterialTheme.typography.bodySmall)
        }
        if (add != null && !compact) Text("Up to 3 photos, 10 MB each", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (index in 0..2) {
                val id = photos.getOrNull(index)
                if (id != null) AppMediaThumbnail(onPreview = { preview = id }, modifier = Modifier.weight(1f),
                    previewTag = "feedback_photo_$index", onRemove = remove?.let { { it(id) } },
                    removeTag = "feedback_remove_photo_$index", enabled = !busy, removeDescription = "Remove photo ${index + 1}") {
                    FeedbackPhotoImage(id, viewer, sessionId, "View photo ${index + 1}", Modifier.fillMaxSize(), false)
                } else if (add != null && index == photos.size) {
                    AppMediaAddTile(add, Modifier.weight(1f).testTag(addTag), enabled = !busy, processing = busy)
                } else Spacer(Modifier.weight(1f))
            }
        }
    }
    preview?.let { id ->
        key(viewer, sessionId, photos.toList()) {
            FeedbackPhotoPreview(photos, photos.indexOf(id).coerceAtLeast(0), viewer, sessionId) { preview = null }
        }
    }
}

@Composable
private fun FeedbackPhotoImage(id: String, viewer: String, sessionId: String, description: String,
    modifier: Modifier, large: Boolean) {
    val context = LocalContext.current.applicationContext
    val activities = LocalCheckInActivities.current
    var loading by remember(id, viewer, sessionId) { mutableStateOf(true) }
    val bitmap by produceState<Bitmap?>(null, id, viewer, sessionId, large, activities) {
        loading = true
        value = FeedbackPhotoStore(context, activities).bitmap(id, viewer, sessionId, if (large) 1600 else 320)
        loading = false
    }
    if (!loading && bitmap != null) Image(bitmap!!.asImageBitmap(), description, modifier,
        contentScale = if (large) ContentScale.Fit else ContentScale.Crop)
    else Text(if (loading) "Loading" else "Photo unavailable", color = if (large) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall)
}

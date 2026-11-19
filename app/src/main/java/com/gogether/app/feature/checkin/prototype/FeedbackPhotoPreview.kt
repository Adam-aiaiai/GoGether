package com.gogether.app.feature.checkin.prototype

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
internal fun FeedbackPhotoPreview(photos: List<String>, initialPage: Int, viewer: String, sessionId: String,
    close: () -> Unit) {
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { photos.size })
    var zoomed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().testTag("feedback_photo_preview")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = close) { Text("< Back", color = Color.White) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("${pager.currentPage + 1} / ${photos.size}", color = Color.White,
                        modifier = Modifier.testTag("photo_page_count"))
                }
                // Explicit navigation remains available while an enlarged photo consumes horizontal drags.
                TextButton(enabled = pager.currentPage > 0, onClick = { scope.launch { pager.scrollToPage(pager.currentPage - 1) } }) {
                    Text("Previous", color = if (pager.currentPage > 0) Color.White else Color.Gray)
                }
                TextButton(enabled = pager.currentPage < photos.lastIndex,
                    onClick = { scope.launch { pager.scrollToPage(pager.currentPage + 1) } }) {
                    Text("Next", color = if (pager.currentPage < photos.lastIndex) Color.White else Color.Gray)
                }
            }
            HorizontalPager(state = pager, userScrollEnabled = !zoomed,
                key = { photos[it] }, modifier = Modifier.fillMaxWidth().weight(1f).testTag("photo_pager")) { page ->
                key(viewer, sessionId, photos[page]) {
                    ZoomableFeedbackPhoto(photos[page], viewer, sessionId, page == pager.currentPage) {
                        if (page == pager.currentPage) zoomed = it
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableFeedbackPhoto(id: String, viewer: String, sessionId: String, active: Boolean,
    zoomChanged: (Boolean) -> Unit) {
    val context = LocalContext.current.applicationContext
    val activities = LocalCheckInActivities.current
    val result by produceState<Result<Bitmap?>?>(null, id, viewer, sessionId, activities) {
        value = try { Result.success(FeedbackPhotoStore(context, activities).bitmap(id, viewer, sessionId, 1600)) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    fun reset() { scale = 1f; offset = Offset.Zero }
    LaunchedEffect(active, viewport) { reset() }
    LaunchedEffect(active, scale) { if (active) zoomChanged(scale > 1f) }
    val bitmap = result?.getOrNull()
    fun bounded(value: Offset, zoom: Float): Offset {
        val image = bitmap ?: return Offset.Zero
        val fit = minOf(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
        val maxX = ((image.width * fit * zoom - viewport.width) / 2).coerceAtLeast(0f)
        val maxY = ((image.height * fit * zoom - viewport.height) / 2).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }
        .testTag("photo_zoom_$id")
        .semantics { stateDescription = "Zoom ${(scale * 100).toInt()}%" }
        .pointerInput(id, bitmap) { detectTapGestures(onDoubleTap = { reset() }) }
        .pointerInput(id, bitmap, viewport) {
            if (bitmap == null) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var transforming = false
                do {
                    // Claim multi-touch before the parent pager interprets either finger as a swipe.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) transforming = true
                    if (transforming || scale > 1f) {
                        val next = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                        offset = bounded(offset * (next / scale) + event.calculatePan(), next)
                        scale = next
                        // Leave stationary taps to the double-tap recognizer.
                        event.changes.filter { it.position != it.previousPosition }.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
        contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), "Event photo preview", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            })
        else Text(if (result == null) "Loading" else "Photo unavailable", color = Color.White)
    }
}

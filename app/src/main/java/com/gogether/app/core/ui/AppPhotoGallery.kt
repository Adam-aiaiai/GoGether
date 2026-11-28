package com.gogether.app.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
internal fun AppPhotoGallery(photos: List<String>, initialPage: Int,
    loadPhoto: suspend (String) -> Bitmap?, close: () -> Unit) {
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { photos.size })
    var zoomed by remember { mutableStateOf(false) }
    val aspectRatios = remember(photos, loadPhoto) { mutableStateMapOf<String, Float>() }
    val scope = rememberCoroutineScope()
    AppPhotoPreviewDialog(onDismiss = close, modifier = Modifier.testTag("feedback_photo_preview"),
        aspectRatio = aspectRatios[photos[pager.currentPage]] ?: (4f / 3f),
        navigation = PhotoPreviewNavigation(pager.currentPage, photos.size,
            previous = { scope.launch { pager.scrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
            next = { scope.launch { pager.scrollToPage((pager.currentPage + 1).coerceAtMost(photos.lastIndex)) } })) { modifier ->
        HorizontalPager(state = pager, userScrollEnabled = !zoomed,
            key = { photos[it] }, modifier = modifier.fillMaxSize().testTag("photo_pager")) { page ->
            key(loadPhoto, photos[page]) {
                ZoomableGalleryPhoto(photos[page], loadPhoto, page == pager.currentPage,
                    aspectChanged = { aspectRatios[photos[page]] = it },
                    zoomChanged = { if (page == pager.currentPage) zoomed = it })
            }
        }
    }
}

@Composable
private fun ZoomableGalleryPhoto(id: String, loadPhoto: suspend (String) -> Bitmap?, active: Boolean,
    aspectChanged: (Float) -> Unit, zoomChanged: (Boolean) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val result by produceState<Result<Bitmap?>?>(null, id, loadPhoto, retry) {
        value = null
        value = try { Result.success(loadPhoto(id)) }
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
    LaunchedEffect(bitmap) { bitmap?.let { aspectChanged(it.width.toFloat() / it.height) } }
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
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (result == null) "Loading" else "Photo unavailable", color = Color.White)
            if (result != null) TextButton(onClick = { retry++ }) { Text("Retry", color = Color.White) }
        }
    }
}

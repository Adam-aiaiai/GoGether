package com.gogether.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/** Zero-based position and actions for a photo gallery. */
data class PhotoPreviewNavigation(val page: Int, val count: Int,
    val previous: () -> Unit, val next: () -> Unit)

/** Photo-only overlay. The native dialog keeps outside-tap and Back dismissal. */
@Composable
fun AppPhotoPreviewDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float? = null,
    navigation: PhotoPreviewNavigation? = null,
    photo: @Composable (Modifier) -> Unit,
) {
    val multiple = navigation != null && navigation.count > 1
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Layout(modifier = modifier, content = {
            // Let the image keep its aspect ratio so the control follows the actual
            // photo corner, including portrait photos, rather than an empty frame.
            Box { photo(Modifier.heightIn(max = 550.dp)) }
            IconButton(onClick = onDismiss,
                modifier = Modifier.size(48.dp).testTag("photo_preview_close")) {
                Icon(Icons.Default.Close, contentDescription = "Close photo",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                        .background(Color.Black.copy(alpha = .55f), CircleShape).padding(5.dp))
            }
            if (multiple) {
                val gallery = requireNotNull(navigation)
                IconButton(onClick = gallery.previous, enabled = gallery.page > 0,
                    modifier = Modifier.size(48.dp).testTag("photo_previous")) {
                    Icon(Icons.Default.KeyboardArrowLeft, "Previous photo",
                        tint = Color.White.copy(alpha = if (gallery.page > 0) 1f else .3f),
                        modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = gallery.next, enabled = gallery.page < gallery.count - 1,
                    modifier = Modifier.size(48.dp).testTag("photo_next")) {
                    Icon(Icons.Default.KeyboardArrowRight, "Next photo",
                        tint = Color.White.copy(alpha = if (gallery.page < gallery.count - 1) 1f else .3f),
                        modifier = Modifier.size(30.dp))
                }
            }
            if (navigation != null) {
                Text("${navigation.page + 1} / ${navigation.count}", color = Color.White,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("photo_page_count"))
            }
        }) { measurables, constraints ->
            val touchSize = 48.dp.roundToPx()
            val cornerOffset = 14.dp.roundToPx()
            val edgeSpace = touchSize / 2 + cornerOffset
            // Each arrow gets its own touch target outside the image.
            val sideSpace = if (multiple) touchSize else edgeSpace
            val maxWidth = (constraints.maxWidth - sideSpace * 2).coerceAtLeast(0)
            val maxHeight = (constraints.maxHeight - edgeSpace * 2).coerceIn(0, 550.dp.roundToPx())
            val imageConstraints = if (aspectRatio != null && aspectRatio.isFinite() && aspectRatio > 0f) {
                val width = minOf(maxWidth, (maxHeight * aspectRatio).roundToInt())
                Constraints.fixed(width, (width / aspectRatio).roundToInt().coerceAtMost(maxHeight))
            } else Constraints(maxWidth = maxWidth, maxHeight = maxHeight)
            val image = measurables[0].measure(imageConstraints)
            val close = measurables[1].measure(Constraints.fixed(touchSize, touchSize))
            val previous = if (multiple) measurables[2].measure(Constraints.fixed(touchSize, touchSize)) else null
            val next = if (multiple) measurables[3].measure(Constraints.fixed(touchSize, touchSize)) else null
            val count = if (navigation != null) measurables.last().measure(Constraints(maxWidth = image.width,
                maxHeight = edgeSpace)) else null
            layout(image.width + sideSpace * 2, image.height + edgeSpace * 2) {
                image.place(sideSpace, edgeSpace)
                // The 18dp-radius circle sits diagonally outside the corner:
                // sqrt(14^2 + 14^2) > 18, leaving the photo completely uncovered.
                close.place(sideSpace + image.width + cornerOffset - touchSize / 2, 0)
                val arrowY = edgeSpace + (image.height - touchSize) / 2
                previous?.place(0, arrowY)
                next?.place(sideSpace + image.width, arrowY)
                count?.place(sideSpace + (image.width - count.width) / 2,
                    edgeSpace + image.height + (edgeSpace - count.height) / 2)
            }
        }
    }
}

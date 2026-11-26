package com.gogether.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private val MediaShape = RoundedCornerShape(8.dp)
private val MediaBackground = Color(0xFFF6F7F9)

@Composable
fun AppMediaAddTile(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    description: String = "Add photo", processing: Boolean = false) {
    Surface(onClick = onClick, enabled = enabled, shape = MediaShape, color = MediaBackground,
        modifier = modifier.aspectRatio(1.25f).drawWithContent {
            drawContent()
            drawRoundRect(Color(0xFFBFC1C6), cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
        }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Add, description, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            if (processing) Text("Processing", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AppMediaThumbnail(onPreview: () -> Unit, modifier: Modifier = Modifier, previewTag: String,
    onRemove: (() -> Unit)? = null, removeTag: String = "", enabled: Boolean = true,
    removeDescription: String = "Remove photo", content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(MediaShape).background(MediaBackground)
            .clickable(onClick = onPreview).testTag(previewTag), contentAlignment = Alignment.Center, content = content)
        if (onRemove != null) IconButton(onClick = onRemove, enabled = enabled,
            modifier = Modifier.align(Alignment.TopEnd).size(32.dp).testTag(removeTag)) {
            Icon(Icons.Default.Close, removeDescription,
                Modifier.size(22.dp).background(Color.White, RoundedCornerShape(50)), tint = Color(0xFF30343A))
        }
    }
}

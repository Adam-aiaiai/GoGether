package com.gogether.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Used only by registration cancellation, waitlist exit and review visibility. */
@Composable
fun SquareActionDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    actions: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // These approved dialogs stay white, including when the surrounding app is dark.
    val colors = MaterialTheme.colorScheme.copy(
        primary = AppUi.Primary, onPrimary = Color.White,
        surface = Color.White, onSurface = Color(0xFF26272B),
        onSurfaceVariant = Color(0xFF646870), outline = AppUi.Primary,
    )
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MaterialTheme(colorScheme = colors) {
            BoxWithConstraints(Modifier.fillMaxSize().pointerInput(onDismiss) {
                detectTapGestures { onDismiss() }
            }.safeDrawingPadding().imePadding().padding(16.dp),
                contentAlignment = Alignment.Center) {
                val width = minOf(maxWidth, 400.dp)
                val minimumHeight = if (compact) 0.dp else minOf(width * .86f, maxHeight)
                CompositionLocalProvider(LocalPageActionWidth provides width * .8f) {
                    Surface(modifier.width(width).heightIn(min = minimumHeight, max = maxHeight)
                        .pointerInput(Unit) { detectTapGestures { /* Keep taps inside the panel inside. */ } },
                        shape = RoundedCornerShape(6.dp), color = Color.White,
                        contentColor = colors.onSurface, tonalElevation = 0.dp, shadowElevation = 6.dp) {
                        // Short confirmations fit their content; visibility retains its approved height.
                        Column(Modifier.verticalScroll(rememberScrollState())
                            .heightIn(min = minimumHeight).padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(title, Modifier.widthIn(max = width * .68f),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontSize = 24.sp, lineHeight = 30.sp,
                                    fontWeight = FontWeight.Bold, color = colors.onSurface)
                                CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                                    ProvideTextStyle(MaterialTheme.typography.bodyLarge) { content() }
                                }
                            }
                            Box(Modifier.fillMaxWidth().padding(top = 24.dp)) { actions() }
                        }
                    }
                }
            }
        }
    }
}

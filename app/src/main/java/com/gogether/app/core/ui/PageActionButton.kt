package com.gogether.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Full-row actions, including Done, use 80% of the page width (288 dp at 360 dp).
internal val LocalPageActionWidth = staticCompositionLocalOf { 288.dp }
private enum class PageActionStyle { Filled, Outlined, Tonal }

/** Full-row actions only. Compact actions beside other content keep their existing layout. */
@Composable
fun PageActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = PageAction(onClick, modifier, enabled, PageActionStyle.Filled, content)

@Composable
fun OutlinedPageActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = PageAction(onClick, modifier, enabled, PageActionStyle.Outlined, content)

@Composable
fun TonalPageActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = PageAction(onClick, modifier, enabled, PageActionStyle.Tonal, content)

@Composable
private fun PageAction(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    style: PageActionStyle,
    content: @Composable RowScope.() -> Unit,
) {
    // The theme computes this once from the page viewport, before nested padding.
    // A narrower pane or sheet can constrain it without overflowing its bounds.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val buttonModifier = Modifier.width(LocalPageActionWidth.current).then(modifier).heightIn(min = 52.dp)
        val labelStyle = MaterialTheme.typography.titleMedium.copy(
            fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        val label: @Composable RowScope.() -> Unit = { ProvideTextStyle(labelStyle) { content() } }
        val padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        val shape = RoundedCornerShape(50)
        when (style) {
            PageActionStyle.Outlined -> OutlinedButton(onClick, buttonModifier, enabled, shape = shape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
                ), contentPadding = padding, content = label)
            PageActionStyle.Filled -> Button(onClick, buttonModifier, enabled, shape = shape,
                colors = ButtonDefaults.buttonColors(contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
                ), contentPadding = padding, content = label)
            PageActionStyle.Tonal -> FilledTonalButton(onClick, buttonModifier, enabled, shape = shape,
                contentPadding = padding, content = label)
        }
    }
}

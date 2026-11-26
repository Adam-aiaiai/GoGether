package com.gogether.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

/** Full-row actions use 80% width; compact slots can constrain this to their existing allocation. */
@Composable
fun PageActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    content: @Composable RowScope.() -> Unit,
) = PageAction(onClick, modifier, enabled, PageActionStyle.Filled, content, contentPadding)

/** Only for a full-row Cancel displayed alongside its filled save/confirm action. */
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
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
) {
    val interactions = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactions, enabled && style == PageActionStyle.Filled)
    // The theme computes this once from the page viewport, before nested padding.
    // A narrower pane or sheet can constrain it without overflowing its bounds.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(50)
        val buttonModifier = Modifier.width(LocalPageActionWidth.current).then(modifier).heightIn(min = 52.dp)
            .then(if (style == PageActionStyle.Filled) Modifier.pressBounce(scale, shape, enabled) else Modifier)
        val labelStyle = MaterialTheme.typography.titleMedium.copy(
            fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        val label: @Composable RowScope.() -> Unit = { ProvideTextStyle(labelStyle) { content() } }
        when (style) {
            PageActionStyle.Outlined -> OutlinedButton(onClick, buttonModifier, enabled, shape = shape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
                ), contentPadding = contentPadding, content = label)
            PageActionStyle.Filled -> Button(onClick, buttonModifier, enabled, shape = shape,
                interactionSource = interactions,
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                colors = ButtonDefaults.buttonColors(contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
                ), contentPadding = contentPadding, content = label)
            PageActionStyle.Tonal -> FilledTonalButton(onClick, buttonModifier, enabled, shape = shape,
                contentPadding = contentPadding, content = label)
        }
    }
}

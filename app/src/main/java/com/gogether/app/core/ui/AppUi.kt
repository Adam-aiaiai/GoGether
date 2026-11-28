package com.gogether.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Presentation tokens shared by all feature screens. */
object AppUi {
    val CardShape = RoundedCornerShape(16.dp)
    val FieldShape = RoundedCornerShape(12.dp)
    val TagShape = RoundedCornerShape(12.dp)
    val Primary = Color(0xFFF45B2B)
    val OnSoftAccent = Color(0xFFC94B1B)
    val NavigationBackground = Color.White
    val SoftAccent = Color(0xFFFFF1E7)
    val AccentFadeStart = Color(0xFFFFFCFA)
    val SelectedTag = Color(0xFFFFE5D4)
    val SelectedTagText = Color(0xFF8B3A17)
    val PageHeaderHeight = 64.dp
    val HeaderPeach = Color(0xFFFFD9C2)
    val HeaderMint = Color(0xFFD5EEE3)
}

// Screens can also render independently in previews and UI tests.
val LocalPageHeaderProvided = staticCompositionLocalOf { false }

/** Paint through page gutters without moving the title or its touch targets. */
fun Modifier.pageHeaderBackground(horizontalInset: Dp = 0.dp): Modifier = drawBehind {
    val inset = horizontalInset.toPx()
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(AppUi.HeaderPeach, AppUi.HeaderMint),
            startX = -inset,
            endX = size.width + inset,
        ),
        // Parent padding can land on half a physical pixel. Cover that rounding seam too.
        topLeft = Offset(-inset - 1f, 0f),
        size = Size(size.width + inset * 2 + 2f, size.height),
    )
}

@Composable
fun AppFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier,
        enabled = enabled, shape = AppUi.TagShape, border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = AppUi.SelectedTag,
            selectedLabelColor = AppUi.SelectedTagText,
        ))
}

@Composable
fun AppBackButton(onClick: () -> Unit, modifier: Modifier = Modifier, description: String = "Back") {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = description)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPageHeader(title: String, onBack: (() -> Unit)? = null, backModifier: Modifier = Modifier,
    backDescription: String = "Back", horizontalInset: Dp = 0.dp,
    actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        modifier = Modifier.fillMaxWidth().pageHeaderBackground(horizontalInset),
        expandedHeight = AppUi.PageHeaderHeight,
        title = { Text(title, style = MaterialTheme.typography.titleLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { onBack?.let { AppBackButton(it, backModifier, backDescription) } },
        actions = actions,
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

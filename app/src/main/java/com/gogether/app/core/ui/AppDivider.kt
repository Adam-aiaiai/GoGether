package com.gogether.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/** Extend only the separator through the surrounding content gutters, keeping text aligned. */
@Composable
fun AppDivider(
    modifier: Modifier = Modifier,
    horizontalInset: Dp = 20.dp,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    HorizontalDivider(modifier.fillMaxWidth().layout { measurable, constraints ->
        val inset = horizontalInset.roundToPx()
        val line = measurable.measure(constraints.offset(horizontal = inset * 2))
        layout(line.width - inset * 2, line.height) { line.placeRelative(-inset, 0) }
    }, color = color)
}

/** Keep the divider outside the tabs' clipped surface so it can reach both page edges. */
@Suppress("DEPRECATION")
@Composable
fun AppTabRow(selectedTabIndex: Int, horizontalInset: Dp = 20.dp, tabs: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        AppDivider(Modifier.align(Alignment.BottomCenter), horizontalInset)
        TabRow(selectedTabIndex, containerColor = Color.Transparent, divider = {}, tabs = tabs)
    }
}

@Suppress("DEPRECATION")
@Composable
fun AppScrollableTabRow(selectedTabIndex: Int, tabs: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        AppDivider(Modifier.align(Alignment.BottomCenter))
        ScrollableTabRow(selectedTabIndex, edgePadding = 0.dp, containerColor = Color.Transparent,
            divider = {}, tabs = tabs)
    }
}

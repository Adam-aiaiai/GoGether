package com.gogether.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
}

// Screens can also render independently in previews and UI tests.
val LocalPageHeaderProvided = staticCompositionLocalOf { false }

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
    backDescription: String = "Back", actions: @Composable RowScope.() -> Unit = {}) {
    Column {
        CenterAlignedTopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { onBack?.let { AppBackButton(it, backModifier, backDescription) } },
            actions = actions,
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Author, message and timestamp retain distinct emphasis in every message list. */
@Composable
fun MessageCard(author: String, message: String, timestamp: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = AppUi.CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(author, style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Text(timestamp, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

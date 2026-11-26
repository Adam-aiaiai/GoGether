package com.gogether.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Shared multi-line field for reviews, questions, descriptions and notes; not account or search inputs. */
@Composable
fun AppCommentField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    minLines: Int = 2,
    maxLines: Int = Int.MAX_VALUE,
    maxLength: Int? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val error = isError || (maxLength != null && value.length > maxLength)
    val outline = when {
        error -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> Color(0xFFBFC1C6)
    }
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .55f)
    BasicTextField(value, onValueChange, modifier.semantics { if (error) error("Invalid input") }, enabled,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        minLines = minLines, maxLines = maxLines,
        interactionSource = interaction,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), decorationBox = { field ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (label != null) ProvideTextStyle(MaterialTheme.typography.bodyMedium) { label() }
                Column(Modifier.fillMaxWidth().background(Color.White, shape).border(1.dp, outline, shape)
                    .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder != null) {
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) { placeholder() }
                                }
                            }
                            field()
                        }
                    }
                    if (maxLength != null) Text("${value.length}/$maxLength",
                        Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (supportingText != null) {
                    CompositionLocalProvider(LocalContentColor provides if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) { supportingText() }
                    }
                }
            }
        })
}

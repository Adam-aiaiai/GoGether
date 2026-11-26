package com.gogether.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Ordinary inputs keep their label on the outline, including when empty and unfocused. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable () -> Unit,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    shape: Shape = AppUi.FieldShape,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = outlinedFieldColors()
    BasicTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.semantics(mergeDescendants = true) {
            if (isError) error("Invalid input")
        }.padding(top = 8.dp).defaultMinSize(minHeight = 56.dp),
        enabled = enabled, readOnly = readOnly, singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color =
            if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)),
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        visualTransformation = visualTransformation, interactionSource = interaction,
        cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                // Material 3 1.3 has no always-minimized label option. This value controls only
                // decoration; the editor, accessibility value and callbacks use the real value above.
                value = " ", innerTextField = {
                    Box {
                        if (value.isEmpty() && placeholder != null) {
                            ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                placeholder()
                            }
                        }
                        innerTextField()
                    }
                }, enabled = enabled, singleLine = singleLine,
                visualTransformation = VisualTransformation.None, interactionSource = interaction,
                label = label, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                supportingText = supportingText, isError = isError, colors = colors,
                container = { OutlinedTextFieldDefaults.Container(enabled, isError, interaction, colors = colors, shape = shape) },
            )
        },
    )
}

/** A button styled like an ordinary outlined input. It opens a picker, never the keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOutlinedPickerField(label: String, value: String, hint: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val colors = outlinedFieldColors()
    Box(modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .padding(top = 8.dp).defaultMinSize(minHeight = 56.dp), propagateMinConstraints = true) {
        OutlinedTextFieldDefaults.DecorationBox(
            value = " ", innerTextField = {
                Text(value.ifBlank { "Select $hint" }, style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }, enabled = true, singleLine = true, visualTransformation = VisualTransformation.None,
            interactionSource = interaction, label = { Text(label) }, colors = colors,
            container = { OutlinedTextFieldDefaults.Container(true, false, interaction, colors = colors, shape = AppUi.FieldShape) },
        )
    }
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

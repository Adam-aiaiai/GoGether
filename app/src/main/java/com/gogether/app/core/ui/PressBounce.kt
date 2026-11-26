package com.gogether.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sqrt

internal object AppMotion {
    const val PressedScale = 0.94f
    const val Stiffness = 260f
    const val Damping = 12f
    const val SelectedIconScale = 1.40f
    // Compose takes a damping ratio, not the physical damping coefficient.
    // With unit mass: ratio = c / (2 * sqrt(k * m)). Passing 12 would remove bounce.
    val DampingRatio = Damping / (2f * sqrt(Stiffness))
    fun rebound() = spring<Float>(stiffness = Stiffness, dampingRatio = DampingRatio,
        visibilityThreshold = 0.0001f)
}

/** Observe the control's own interactions: never intercept or delay its click handler. */
@Composable
internal fun rememberPressScale(source: InteractionSource, enabled: Boolean = true): State<Float> {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(source, enabled) {
        scale.snapTo(1f)
        if (!enabled) return@LaunchedEffect
        val presses = mutableSetOf<PressInteraction.Press>()
        var motion: Job? = null
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    presses.add(interaction)
                    motion?.cancel()
                    motion = launch {
                        scale.animateTo(AppMotion.PressedScale, tween(80, easing = FastOutSlowInEasing))
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val press = when (interaction) {
                        is PressInteraction.Release -> interaction.press
                        is PressInteraction.Cancel -> interaction.press
                        else -> error("Unreachable")
                    }
                    if (presses.remove(press) && presses.isEmpty()) {
                        motion?.cancel()
                        motion = launch {
                            // Even a very quick tap gets a visible press. A cancelled drag just resets.
                            if (interaction is PressInteraction.Release && scale.value > AppMotion.PressedScale) {
                                scale.animateTo(AppMotion.PressedScale, tween(40))
                            }
                            scale.animateTo(1f, AppMotion.rebound())
                        }
                    }
                }
            }
        }
    }
    return scale.asState()
}

internal fun pressDepth(scale: Float): Float =
    ((1f - scale) / (1f - AppMotion.PressedScale)).coerceIn(0f, 1f)

internal fun Modifier.pressBounce(scale: State<Float>, shape: Shape, enabled: Boolean,
    elevation: Dp = 2.dp): Modifier = graphicsLayer {
    scaleX = scale.value
    scaleY = scale.value
    this.shape = shape
    clip = false
    shadowElevation = if (enabled) elevation.toPx() * (1f - .8f * pressDepth(scale.value)) else 0f
    ambientShadowColor = Color.Black.copy(alpha = .12f)
    spotShadowColor = Color.Black.copy(alpha = .16f)
}

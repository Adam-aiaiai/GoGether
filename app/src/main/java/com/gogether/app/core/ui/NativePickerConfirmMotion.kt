package com.gogether.app.core.ui

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.Button
import androidx.compose.animation.core.FloatSpringSpec

internal class NativePickerConfirmMotion(private val button: Button) {
    private var animation: ValueAnimator? = null
    private val restingElevation = 2f * button.resources.displayMetrics.density
    private var armed = false
    private var pending = false
    private var disposed = false

    fun attach() {
        disposed = false
        render(1f)
        button.setOnTouchListener { _, event ->
            if (pending) return@setOnTouchListener true
            if (button.isEnabled) when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { armed = true; press() }
                MotionEvent.ACTION_UP -> if (armed) {
                    armed = false
                    button.isPressed = false
                    button.cancelLongPress()
                    confirm()
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_CANCEL -> { armed = false; release(completePress = false) }
                MotionEvent.ACTION_MOVE -> if (event.x < 0 || event.x > button.width ||
                    event.y < 0 || event.y > button.height) { armed = false; release(completePress = false) }
            }
            false
        }
        button.setOnKeyListener { _, code, event ->
            if (code !in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE) || !button.isEnabled) false
            else {
                if (!pending && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) press()
                if (event.action == KeyEvent.ACTION_UP) {
                    if (event.isCanceled) release(completePress = false) else confirm()
                }
                true
            }
        }
        button.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action == AccessibilityNodeInfo.ACTION_CLICK && button.isEnabled) {
                    confirm()
                    return true
                }
                return super.performAccessibilityAction(host, action, args)
            }
        }
    }

    private fun confirm() {
        if (pending || disposed || !button.isEnabled) return
        pending = true
        release(completePress = true)
    }

    private fun finishConfirmation() {
        if (!pending) return
        pending = false
        render(1f)
        // Replay the original listener, including native validation, neutral actions and dismissal.
        if (!disposed && button.isAttachedToWindow && button.isEnabled) button.performClick()
    }

    private fun render(scale: Float) {
        button.scaleX = scale
        button.scaleY = scale
        button.elevation = restingElevation * (1f - .8f * pressDepth(scale))
    }

    private fun press() {
        animation?.cancel()
        animation = ValueAnimator.ofFloat(button.scaleX, AppMotion.PressedScale).apply {
            duration = 80L
            interpolator = PathInterpolator(.4f, 0f, .2f, 1f)
            addUpdateListener { render(it.animatedValue as Float) }
            start()
        }
    }

    private fun release(completePress: Boolean) {
        animation?.cancel()
        // Complete even a short tap. A cancelled drag returns from its current scale.
        if (completePress && button.scaleX > AppMotion.PressedScale) render(AppMotion.PressedScale)
        val initial = button.scaleX
        val spring = FloatSpringSpec(AppMotion.DampingRatio, AppMotion.Stiffness, .0001f)
        val durationNanos = spring.getDurationNanos(initial, 1f, 0f)
        var overshot = false
        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (durationNanos / 1_000_000).coerceAtLeast(1)
            interpolator = LinearInterpolator()
            addUpdateListener {
                val elapsed = (it.currentPlayTime * 1_000_000).coerceAtMost(durationNanos)
                val scale = spring.getValueFromNanos(elapsed, initial, 1f, 0f)
                render(scale)
                if (scale > 1f) overshot = true
                if (completePress && overshot && scale <= 1f) {
                    cancel()
                    finishConfirmation()
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    // Animator duration scale zero still performs the action exactly once.
                    if (!cancelled && completePress) finishConfirmation()
                }
            })
            start()
        }
    }

    fun dispose() {
        disposed = true
        pending = false
        armed = false
        animation?.cancel()
        button.setOnTouchListener(null)
        button.setOnKeyListener(null)
        button.accessibilityDelegate = null
        render(1f)
    }
}

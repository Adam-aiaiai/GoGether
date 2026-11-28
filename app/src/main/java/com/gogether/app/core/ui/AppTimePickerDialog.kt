package com.gogether.app.core.ui

import android.app.TimePickerDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import com.gogether.app.R
import kotlin.math.roundToInt

/** Shared appearance; the native picker still handles selection, validation and input mode. */
class AppTimePickerDialog(
    context: Context,
    listener: OnTimeSetListener,
    hour: Int,
    minute: Int,
    is24HourView: Boolean,
) : TimePickerDialog(context, R.style.Theme_Gogether_TimePicker, listener, hour, minute, is24HourView) {
    private var confirmMotion: NativePickerConfirmMotion? = null

    override fun onStart() {
        super.onStart()
        val root = window?.decorView
        val dial = root?.findNativeView("radial_picker")
        val parent = dial?.parent as? ViewGroup
        if (dial != null && parent != null && parent !is EnlargedTimeDial && dial.layoutParams.height > 0) {
            val index = parent.indexOfChild(dial)
            val original = dial.layoutParams
            val diameter = original.height
            val margins = original as? ViewGroup.MarginLayoutParams
            val topGap = margins?.topMargin ?: 0
            val bottomGap = margins?.bottomMargin ?: 0
            parent.removeView(dial)
            val container = EnlargedTimeDial(context, dial, diameter, topGap, bottomGap)
            // Keep vertical spacing inside the wrapper so it also disappears in keyboard mode.
            margins?.topMargin = 0
            margins?.bottomMargin = 0
            original.width = ViewGroup.LayoutParams.MATCH_PARENT
            original.height = ViewGroup.LayoutParams.WRAP_CONTENT
            parent.addView(container, index, original)
        }
        (root?.findNativeView("toggle_mode") as? ImageButton)?.apply {
            // Keep the native keyboard/clock drawables and click listener; both fit 32dp.
            val density = resources.displayMetrics.density
            val target = (48 * density).roundToInt()
            val inset = (8 * density).roundToInt()
            layoutParams = layoutParams.apply { width = target; height = target }
            minimumWidth = target
            minimumHeight = target
            setPadding(inset, inset, inset, inset)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        confirmMotion = NativePickerConfirmMotion(getButton(BUTTON_POSITIVE)).also { it.attach() }
    }

    override fun onStop() {
        confirmMotion?.dispose()
        confirmMotion = null
        super.onStop()
    }
}

private fun View.findNativeView(name: String): View? {
    if (runCatching { resources.getResourceEntryName(id) }.getOrNull() == name) return this
    if (this is ViewGroup) for (index in 0 until childCount) {
        getChildAt(index).findNativeView(name)?.let { return it }
    }
    return null
}

/** Reserve the enlarged bounds so the dial never overlaps the header or action row.
 * Scaling the native view also scales its digits and preserves Android's touch transforms.
 */
private class EnlargedTimeDial(
    context: Context,
    private val dial: View,
    private val baseSize: Int,
    topGap: Int,
    bottomGap: Int,
) : FrameLayout(context) {
    init {
        setPadding(0, topGap, 0, bottomGap)
        addView(dial, LayoutParams(baseSize, baseSize))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (baseSize * 1.2f).roundToInt()
        val width = resolveSize(desired, widthMeasureSpec)
        // Keyboard mode hides the native dial. Collapse its reserved space as well.
        if (dial.visibility == GONE) {
            setMeasuredDimension(width, 0)
            return
        }
        val verticalGap = paddingTop + paddingBottom
        val availableHeight = (resolveSize(desired + verticalGap, heightMeasureSpec) - verticalGap).coerceAtLeast(0)
        val size = minOf(desired, width, availableHeight)
        val childSpec = MeasureSpec.makeMeasureSpec(baseSize, MeasureSpec.EXACTLY)
        dial.measure(childSpec, childSpec)
        dial.scaleX = size.toFloat() / baseSize
        dial.scaleY = dial.scaleX
        setMeasuredDimension(width, size + verticalGap)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (dial.visibility == GONE) return
        val x = (width - baseSize) / 2
        val y = paddingTop + (height - paddingTop - paddingBottom - baseSize) / 2
        dial.layout(x, y, x + baseSize, y + baseSize)
    }
}

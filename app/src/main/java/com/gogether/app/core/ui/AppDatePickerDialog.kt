package com.gogether.app.core.ui

import android.app.DatePickerDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import com.gogether.app.R

/** Native date selection, with the same confirmation motion as filled page actions. */
class AppDatePickerDialog(
    context: Context,
    listener: OnDateSetListener,
    year: Int,
    month: Int,
    day: Int,
) : DatePickerDialog(context, R.style.Theme_Gogether_DatePicker, listener, year, month, day) {
    private var actionMotions: List<NativePickerConfirmMotion> = emptyList()

    override fun onStart() {
        super.onStart()
        // The native inner column uses wrap_content, which can be narrower than the
        // dialog. Its header must fill the picker instead of leaving a white end strip.
        datePicker.getChildAt(0)?.let { content ->
            content.layoutParams = content.layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT }
        }
        styleMonthArrows(datePicker)
        fitThreeActions()
        actionMotions = listOf(BUTTON_POSITIVE, BUTTON_NEUTRAL).mapNotNull { which ->
            getButton(which)?.takeIf { it.visibility == View.VISIBLE }?.let { button ->
                NativePickerConfirmMotion(button).also { it.attach() }
            }
        }
    }

    override fun onStop() {
        actionMotions.forEach { it.dispose() }
        actionMotions = emptyList()
        super.onStop()
    }

    private fun fitThreeActions() {
        val buttons = listOf(BUTTON_NEUTRAL, BUTTON_NEGATIVE, BUTTON_POSITIVE)
            .mapNotNull { getButton(it)?.takeIf { button -> button.visibility == View.VISIBLE } }
        if (buttons.size != 3) return
        // Leave room for three equally sized actions on narrow phones. The native
        // button bar can still stack them when larger accessibility text needs it.
        val density = context.resources.displayMetrics.density
        buttons.forEach { button ->
            button.minWidth = (88 * density).toInt()
            button.minimumWidth = (88 * density).toInt()
            button.setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            (button.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.marginStart = (3 * density).toInt()
                params.marginEnd = (3 * density).toInt()
                button.layoutParams = params
            }
        }
    }

    private fun styleMonthArrows(view: View) {
        // Use public View/ImageButton APIs. If a device supplies a different calendar layout,
        // leave its controls working rather than relying on hidden framework methods.
        if (view is ImageButton) {
            val icon = when (runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()) {
                "prev" -> R.drawable.calendar_previous_month
                "next" -> R.drawable.calendar_next_month
                else -> null
            }
            if (icon != null) {
                view.setImageResource(icon)
                view.scaleType = ImageView.ScaleType.CENTER
                view.setPadding(0, 0, 0, 0)
                // The native calendar centers both the title and these controls in its header.
                // Keep the touch target at least 48dp while the visible icon is 30dp.
                val touchSize = (48 * view.resources.displayMetrics.density).toInt()
                view.minimumWidth = touchSize
                view.minimumHeight = touchSize
            }
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) styleMonthArrows(view.getChildAt(index))
    }
}

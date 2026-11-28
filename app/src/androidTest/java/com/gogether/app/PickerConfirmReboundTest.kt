package com.gogether.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.ui.AppDatePickerDialog
import com.gogether.app.core.ui.AppTimePickerDialog
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PickerConfirmReboundTest {
    @get:Rule val compose = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private lateinit var dialog: AlertDialog
    private var callbacks = 0
    private fun show(factory: (Context) -> AlertDialog) {
        compose.setContent { GogetherTheme {
            val context = LocalContext.current
            DisposableEffect(Unit) {
                dialog = factory(context)
                dialog.show()
                onDispose { dialog.dismiss() }
            }
        } }
        compose.waitForIdle()
    }
    private fun touch(button: Button, action: Int) = inst.runOnMainSync {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, button.width/2f, button.height/2f, 0)
        button.dispatchTouchEvent(event)
        event.recycle()
    }
    private fun verifyRebound(which: Int, accessibility: Boolean = false) {
        val button = dialog.getButton(which)
        var peak = 1f
        val observer = ViewTreeObserver.OnPreDrawListener { peak = maxOf(peak, button.scaleX); true }
        inst.runOnMainSync { button.viewTreeObserver.addOnPreDrawListener(observer) }
        if (accessibility) inst.runOnMainSync {
            button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
            button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
        } else {
            touch(button, MotionEvent.ACTION_DOWN); SystemClock.sleep(140)
            inst.runOnMainSync { assertEquals(.94f, button.scaleX, .003f) }
            touch(button, MotionEvent.ACTION_UP)
            touch(button, MotionEvent.ACTION_DOWN); touch(button, MotionEvent.ACTION_UP)
        }
        SystemClock.sleep(150)
        inst.runOnMainSync { assertTrue(dialog.isShowing); assertEquals(0, callbacks) }
        SystemClock.sleep(900)
        inst.runOnMainSync {
            assertFalse(dialog.isShowing)
            assertEquals(1, callbacks)
            assertTrue("An actual release must draw the overshoot before closing: $peak", peak > 1.005f)
        }
    }
    @Test fun timeOkWaitsForVisibleReboundAndConfirmsOnce() {
        show { AppTimePickerDialog(it, { _, hour, minute ->
            assertEquals(14, hour); assertEquals(30, minute); callbacks++
        }, 14, 30, true) }
        val folder = File(inst.targetContext.getExternalFilesDir(null), "ui-consistency-fixes").apply { mkdirs() }
        val bitmap = inst.uiAutomation.takeScreenshot()
        File(folder, "09-time-picker.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        verifyRebound(AlertDialog.BUTTON_POSITIVE)
    }
    @Test fun calendarAccessibilityConfirmKeepsItsOriginalDateCallback() {
        show { AppDatePickerDialog(it, { _, year, month, day ->
            assertEquals(2026, year); assertEquals(10, month); assertEquals(28, day); callbacks++
        }, 2026, 10, 28) }
        verifyRebound(AlertDialog.BUTTON_POSITIVE, accessibility = true)
    }
    @Test fun calendarClearKeepsItsOriginalNeutralAction() {
        show { AppDatePickerDialog(it, { _, _, _, _ -> error("Clear must not confirm a date") }, 2026, 10, 28).apply {
            setButton(AlertDialog.BUTTON_NEUTRAL, "Clear") { _, _ -> callbacks++ }
        } }
        verifyRebound(AlertDialog.BUTTON_NEUTRAL)
    }
    @Test fun cancellingAPendingConfirmationDoesNotApplyTheSelection() {
        show { AppTimePickerDialog(it, { _, _, _ -> callbacks++ }, 14, 30, true) }
        val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        touch(ok, MotionEvent.ACTION_DOWN); SystemClock.sleep(100)
        touch(ok, MotionEvent.ACTION_UP)
        inst.runOnMainSync {
            val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            assertEquals(1f, cancel.scaleX, 0f)
            cancel.performClick()
        }
        SystemClock.sleep(1000)
        inst.runOnMainSync { assertFalse(dialog.isShowing); assertEquals(0, callbacks) }
    }
}

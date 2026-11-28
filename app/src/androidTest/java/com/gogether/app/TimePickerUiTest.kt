package com.gogether.app

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TimePicker
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.*
import androidx.test.espresso.action.ViewActions.click as viewClick
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.account.AccountMode
import com.gogether.app.core.data.*
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class TimePickerUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before fun openApp() {
        val now = System.currentTimeMillis()
        val local = FakeGogetherRepository(DemoData.create(now).copy(currentUserId = "alice"), { now })
        val repo = object : GogetherRepository by local { override val accountMode = AccountMode.CONNECTED }
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
        compose.mainClock.advanceTimeBy(5000)
    }

    private fun tap(tag: String) {
        closeSoftKeyboard()
        compose.mainClock.advanceTimeBy(5000)
        val node = compose.onNodeWithTag(tag)
        if (!tag.startsWith("nav_")) node.performScrollTo()
        node.performClick()
        compose.waitForIdle()
    }

    private fun nativeId(name: String) = instrumentation.targetContext.resources.getIdentifier(name, "id", "android")
    private fun nativeTap(name: String) = onView(withId(nativeId(name))).inRoot(isDialog()).perform(viewClick())
    private fun inspect(matcher: Matcher<View>, action: (View) -> Unit) {
        onView(matcher).inRoot(isDialog()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)
            override fun getDescription() = "Inspect the picker or dispatch a held press"
            override fun perform(controller: UiController, view: View) { action(view) }
        })
    }
    private fun time(action: (TimePicker) -> Unit) = inspect(isAssignableFrom(TimePicker::class.java)) { action(it as TimePicker) }
    private fun button(id: Int) {
        closeSoftKeyboard()
        lateinit var pressed: View
        inspect(withId(id)) { pressed = it }
        onView(withId(id)).inRoot(isDialog()).perform(viewClick())
        compose.waitUntil(5000) { !pressed.isShown }
        SystemClock.sleep(250) // Let the native window finish its exit transition.
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        SystemClock.sleep(400)
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "time-picker-unified").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun inspectAppearance() {
        time { picker ->
            val accent = TypedValue()
            picker.context.theme.resolveAttribute(android.R.attr.colorAccent, accent, true)
            assertEquals(0xFFF45B2B.toInt(), accent.data)
            assertTrue(picker.is24HourView)
        }
        inspect(withId(nativeId("radial_picker"))) {
            assertEquals(1.2f, it.scaleX, .01f)
            val dialBounds = Rect().also(it::getGlobalVisibleRect)
            val buttons = it.rootView.findViewById<View>(android.R.id.button1)
            val actionBounds = Rect().also(buttons::getGlobalVisibleRect)
            assertTrue("Scaled clock must not overlap buttons", dialBounds.bottom <= actionBounds.top)
        }
        inspect(withId(nativeId("toggle_mode"))) {
            val density = it.resources.displayMetrics.density
            assertEquals(32f, (it.width - it.paddingLeft - it.paddingRight) / density, 1f)
            assertEquals(1f, it.scaleX, 0f)
        }
    }
    private fun clockTap(x: Float, y: Float) {
        onView(withId(nativeId("radial_picker"))).inRoot(isDialog()).perform(
            GeneralClickAction(Tap.SINGLE, CoordinatesProvider {
                // Global visible bounds are relative to the dialog window, not the screen.
                // Inject at the displayed, scaled digit using the actual window offset.
                val location = IntArray(2).also(it::getLocationOnScreen)
                floatArrayOf(location[0] + it.width * it.scaleX * x, location[1] + it.height * it.scaleY * y)
            }, Press.FINGER)
        )
    }

    @Test fun enlargedClockStillSelectsTimeAndCancelPreservesTheField() {
        tap("nav_create"); tap("create_time")
        time { it.hour = 14; it.minute = 30 }
        inspectAppearance()
        capture("01-event-hours")
        // Touch the outer 3 on the enlarged dial, then 45 on its minute face.
        clockTap(.9f, .5f)
        time { assertEquals(3, it.hour) }
        nativeTap("minutes")
        clockTap(.1f, .5f)
        time { assertEquals(45, it.minute) }
        capture("02-event-minutes")
        button(android.R.id.button2)
        compose.onNodeWithTag("create_time").assertTextContains("18:00")

        tap("create_time")
        time { it.hour = 14; it.minute = 30 }
        nativeTap("toggle_mode")
        time { picker ->
            val header = picker.findViewById<View>(nativeId("input_header"))
            val headerBounds = Rect().also(header::getGlobalVisibleRect)
            val pickerBounds = Rect().also(picker::getGlobalVisibleRect)
            assertEquals("Hidden clock must not leave a blank strip above keyboard mode", pickerBounds.top, headerBounds.top)
        }
        onView(withId(nativeId("input_hour"))).inRoot(isDialog()).perform(ViewActions.replaceText("16"))
        onView(withId(nativeId("input_minute"))).inRoot(isDialog()).perform(ViewActions.replaceText("35"))
        closeSoftKeyboard()
        capture("03-event-keyboard")
        nativeTap("toggle_mode")
        time { assertEquals(16, it.hour); assertEquals(35, it.minute) }
        inspectAppearance()
        button(android.R.id.button1)
        compose.onNodeWithTag("create_time").assertTextContains("16:35")
        tap("create_time")
        time { assertEquals(16, it.hour); assertEquals(35, it.minute) }
        button(android.R.id.button2)
    }

    @Test fun meetingPickerUsesTheSameStyleAndOnlyConfirmBounces() {
        tap("event_event-1"); tap("manage_event"); tap("edit_meeting"); tap("meeting_time")
        time { it.hour = 14; it.minute = 30 }
        inspectAppearance()
        capture("04-meeting-hours")
        var largest = 1f
        var reboundObserver: ViewTreeObserver.OnPreDrawListener? = null
        fun touch(id: Int, action: Int) = inspect(withId(id)) { view ->
            if (id == android.R.id.button1 && action == MotionEvent.ACTION_CANCEL) {
                // Observe rendered frames; Espresso can wait past the short overshoot
                // before a separate sampling action gets a turn on the UI thread.
                reboundObserver = ViewTreeObserver.OnPreDrawListener {
                    largest = maxOf(largest, view.scaleX)
                    true
                }.also(view.viewTreeObserver::addOnPreDrawListener)
            }
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, action, view.width / 2f, view.height / 2f, 0)
            view.dispatchTouchEvent(event); event.recycle()
        }
        for (id in listOf(android.R.id.button2, android.R.id.button1)) {
            touch(id, MotionEvent.ACTION_DOWN)
            SystemClock.sleep(150)
            inspect(withId(id)) { assertEquals(if (id == android.R.id.button1) .94f else 1f, it.scaleX, .003f) }
            if (id == android.R.id.button1) capture("05-ok-held")
            touch(id, MotionEvent.ACTION_CANCEL)
            SystemClock.sleep(1200)
            inspect(withId(id)) {
                assertEquals(1f, it.scaleX, .001f)
                if (id == android.R.id.button1) {
                    reboundObserver?.let(it.viewTreeObserver::removeOnPreDrawListener)
                    assertTrue("Rendered spring should slightly overshoot; maximum=$largest", largest > 1.005f)
                }
            }
        }
        nativeTap("toggle_mode")
        capture("06-meeting-keyboard")
        button(android.R.id.button1)
        compose.onNodeWithTag("meeting_time").assertTextContains("14:30")
    }
}

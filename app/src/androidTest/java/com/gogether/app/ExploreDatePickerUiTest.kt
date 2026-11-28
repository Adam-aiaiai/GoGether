package com.gogether.app

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.DatePicker
import android.widget.ImageButton
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click as viewClick
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ExploreDatePickerUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = System.currentTimeMillis()
    private val seed = DemoData.create(now).copy(currentUserId = "alice")
    private val firstDate = Instant.ofEpochMilli(seed.events.first().startAt).atZone(ZoneId.systemDefault()).toLocalDate()

    @Before fun openCustomDates() {
        val local = FakeGogetherRepository(seed, { now })
        val repo = object : GogetherRepository by local { override val accountMode = AccountMode.CONNECTED }
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
        compose.mainClock.advanceTimeBy(5000)
        tap("toggle_filters")
        tap("date_custom")
    }

    private fun tap(tag: String) {
        closeSoftKeyboard()
        compose.onNodeWithTag(tag).performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
    }
    private fun dateAction(description: String, action: (DatePicker) -> Unit) {
        onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
            override fun getDescription() = description
            override fun perform(controller: UiController, view: View) { action(view as DatePicker) }
        })
    }
    private fun setDate(date: LocalDate) = dateAction("Choose $date") {
        it.updateDate(date.year, date.monthValue - 1, date.dayOfMonth)
    }
    private fun dialogButton(id: Int) {
        lateinit var pressed: View
        nativeAction(withId(id)) { pressed = it }
        onView(withId(id)).inRoot(isDialog()).perform(viewClick())
        compose.waitUntil(5000) { !pressed.isShown }
        SystemClock.sleep(250) // Confirm now finishes its visible rebound before closing.
        compose.waitForIdle()
    }
    private fun nativeAction(matcher: Matcher<View>, action: (View) -> Unit) {
        onView(matcher).inRoot(isDialog()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)
            override fun getDescription() = "Inspect or press a native calendar control"
            override fun perform(controller: UiController, view: View) { action(view) }
        })
    }
    private fun capture(name: String) {
        SystemClock.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "explore-calendar").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun dateRowScreenshot(name: String) {
        compose.onNodeWithTag("free_time_morning").performScrollTo()
        capture(name)
    }

    @Test fun calendarRangeFiltersEventsAndPreservesCancelAndClear() {
        val from = firstDate.plusDays(1)
        val to = firstDate.plusDays(2)
        dateRowScreenshot("01-empty-fields")
        tap("custom_start_date"); setDate(from); capture("02-from-calendar")
        dialogButton(android.R.id.button1)
        tap("custom_end_date"); setDate(to); capture("03-to-calendar")
        dialogButton(android.R.id.button1)
        compose.onNodeWithTag("custom_start_date").assertTextContains(from.toString())
        compose.onNodeWithTag("custom_end_date").assertTextContains(to.toString())
        dateRowScreenshot("04-selected-range")
        tap("hide_filters")
        compose.waitUntil(5000) { compose.onAllNodesWithText("2 activities").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("event_event-2").assertExists()
        compose.onNodeWithTag("event_event-3").assertExists()
        compose.onNodeWithTag("event_event-1").assertDoesNotExist()
        compose.onNodeWithTag("event_event-4").assertDoesNotExist()
        compose.onNodeWithTag("event_event-2").performScrollTo(); capture("05-filtered-results")

        tap("toggle_filters"); tap("custom_start_date")
        dateAction("Reopen at the selected From date") {
            assertEquals(from.year, it.year); assertEquals(from.monthValue - 1, it.month); assertEquals(from.dayOfMonth, it.dayOfMonth)
        }
        setDate(firstDate.plusDays(4)); dialogButton(android.R.id.button2)
        compose.onNodeWithTag("custom_start_date").assertTextContains(from.toString())
        tap("custom_start_date"); dialogButton(android.R.id.button3)
        compose.onNodeWithTag("custom_start_date").assertTextContains("Select date")
        compose.onNodeWithTag("custom_end_date").assertTextContains(to.toString())
        tap("hide_filters")
        compose.waitUntil(5000) { compose.onAllNodesWithText("3 activities").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("event_event-1").assertExists()
        tap("clear_filters")
        tap("toggle_filters"); tap("date_custom")
        compose.onNodeWithTag("custom_start_date").assertTextContains("Select date")
        compose.onNodeWithTag("custom_end_date").assertTextContains("Select date")
    }

    @Test fun dateFieldsHaveRippleWithoutResizingOrTextEditing() {
        for (tag in listOf("custom_start_date", "custom_end_date")) {
            val field = compose.onNodeWithTag(tag).performScrollTo()
            field.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
            val bounds = field.fetchSemanticsNode().boundsInRoot
            val before = field.captureToImage().toPixelMap()
            field.performTouchInput { down(center) }
            // Scrollable parents delay press feedback until a scroll gesture has been ruled out.
            compose.mainClock.advanceTimeBy(250)
            compose.waitForIdle()
            SystemClock.sleep(400)
            val held = field.captureToImage().toPixelMap()
            val x = (before.width * .90f).toInt()
            val y = (before.height * .55f).toInt()
            capture("06-$tag-pressed")
            assertTrue("$tag should display a ripple (${before[x, y].red} -> ${held[x, y].red})", before[x, y].red - held[x, y].red > .02f)
            assertEquals("$tag must not scale", bounds, field.fetchSemanticsNode().boundsInRoot)
            field.performTouchInput { cancel() }
            SystemClock.sleep(500)
        }
        // Cancelling a newly opened calendar must leave the range empty.
        tap("custom_end_date"); setDate(firstDate); dialogButton(android.R.id.button2)
        compose.onNodeWithTag("custom_end_date").assertTextContains("Select date")
    }

    @Test fun sixthCalendarRowStaysVisibleAboveActions() {
        // August 2026 occupies six weeks in the English Sunday-first calendar.
        tap("custom_start_date")
        setDate(LocalDate.of(2026, 8, 1))
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(500, 5000)
        val root = requireNotNull(automation.rootInActiveWindow)
        // Traverse virtual calendar cells too; native text search skips these nodes.
        fun descendants(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> =
            listOf(node) + (0 until node.childCount).flatMap { index ->
                node.getChild(index)?.let { descendants(it) }.orEmpty()
            }
        val nodes = descendants(root)
        val lastDay = nodes.first { it.text?.toString() == "31" && it.isVisibleToUser }
        val confirm = nodes.single { it.viewIdResourceName == "android:id/button1" }
        val dayBounds = Rect().also(lastDay::getBoundsInScreen)
        val actionBounds = Rect().also(confirm::getBoundsInScreen)
        assertTrue(lastDay.isVisibleToUser)
        assertTrue("The last row must stay above the buttons", dayBounds.bottom <= actionBounds.top)
        assertTrue(lastDay.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        capture("07-six-week-calendar")
        dialogButton(android.R.id.button1)
        compose.onNodeWithTag("custom_start_date").assertTextContains("2026-08-31")
        tap("custom_start_date")
        capture("08-calendar-with-clear")
        dialogButton(android.R.id.button2)
        compose.onNodeWithTag("custom_start_date").assertTextContains("2026-08-31")
    }

    @Test fun enlargedArrowsNavigateAcrossYearsAndConfirmStillWorks() {
        tap("custom_start_date")
        setDate(LocalDate.of(2026, 12, 1))
        for (description in listOf("Previous month", "Next month")) {
            nativeAction(withContentDescription(description)) {
                val arrow = it as ImageButton
                val density = arrow.resources.displayMetrics.density
                assertEquals(30f, arrow.drawable.intrinsicHeight / density, .5f)
                assertTrue(arrow.height / density >= 48f)
            }
        }
        onView(withContentDescription("Next month")).inRoot(isDialog()).perform(viewClick())
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(500, 5000)
        fun findDay(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
            if (node.text?.toString() == text && node.isVisibleToUser) return node
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                findDay(child, text)?.let { return it }
            }
            return null
        }
        requireNotNull(findDay(requireNotNull(automation.rootInActiveWindow), "2"))
            .performAction(AccessibilityNodeInfo.ACTION_CLICK)
        dateAction("Next month crosses into January 2027") {
            assertEquals(2027, it.year); assertEquals(0, it.month); assertEquals(2, it.dayOfMonth)
        }
        capture("09-larger-month-arrows")
        onView(withContentDescription("Previous month")).inRoot(isDialog()).perform(viewClick())
        automation.waitForIdle(500, 5000)
        requireNotNull(findDay(requireNotNull(automation.rootInActiveWindow), "31"))
            .performAction(AccessibilityNodeInfo.ACTION_CLICK)
        dialogButton(android.R.id.button1)
        compose.onNodeWithTag("custom_start_date").assertTextContains("2026-12-31")
    }

    @Test fun calendarHeaderFillsThePickerWidth() {
        tap("custom_start_date")
        dateAction("Header reaches both edges of the picker") { picker ->
            val headerId = picker.resources.getIdentifier("date_picker_header", "id", "android")
            val header = requireNotNull(picker.findViewById<View>(headerId))
            val pickerBounds = Rect().also(picker::getGlobalVisibleRect)
            val headerBounds = Rect().also(header::getGlobalVisibleRect)
            assertEquals(pickerBounds.left, headerBounds.left)
            assertEquals(pickerBounds.right, headerBounds.right)
        }
        capture("11-full-width-header")
        dialogButton(android.R.id.button2)
    }

    @Test fun onlyConfirmScalesAndReboundsWithoutChangingTheSelectedDate() {
        tap("custom_start_date")
        setDate(LocalDate.of(2026, 11, 29))
        fun touch(id: Int, action: Int) = nativeAction(withId(id)) { view ->
            val time = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(time, time, action, view.width / 2f, view.height / 2f, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        for (id in listOf(android.R.id.button2, android.R.id.button1)) {
            var maxScale = 1f
            var recorder: android.view.ViewTreeObserver.OnPreDrawListener? = null
            if (id == android.R.id.button1) nativeAction(withId(id)) { view ->
                recorder = android.view.ViewTreeObserver.OnPreDrawListener {
                    maxScale = maxOf(maxScale, view.scaleX); true
                }
                view.viewTreeObserver.addOnPreDrawListener(recorder)
            }
            touch(id, MotionEvent.ACTION_DOWN)
            SystemClock.sleep(150)
            nativeAction(withId(id)) {
                assertEquals(if (id == android.R.id.button1) .94f else 1f, it.scaleX, .003f)
            }
            if (id == android.R.id.button1) capture("10-ok-held")
            touch(id, MotionEvent.ACTION_CANCEL)
            SystemClock.sleep(1200)
            if (id == android.R.id.button1) {
                nativeAction(withId(id)) { view ->
                    recorder?.let { view.viewTreeObserver.removeOnPreDrawListener(it) }
                }
                assertTrue("The spring should overshoot slightly", maxScale > 1.005f)
            }
            nativeAction(withId(id)) { assertEquals(1f, it.scaleX, .001f) }
        }
        dateAction("Cancelled press leaves the selected date unchanged") {
            assertEquals(2026, it.year); assertEquals(10, it.month); assertEquals(29, it.dayOfMonth)
        }
        dialogButton(android.R.id.button2)
        compose.onNodeWithTag("custom_start_date").assertTextContains("Select date")
    }
}

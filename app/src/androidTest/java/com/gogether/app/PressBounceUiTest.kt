package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.DemoData
import com.gogether.app.core.ui.AppBottomNavigation
import com.gogether.app.core.ui.PageActionButton
import com.gogether.app.feature.checkin.cloud.CloudCheckInResultContent
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class PressBounceUiTest {
    @get:Rule val compose = createComposeRule()

    private fun frame(content: @Composable () -> Unit) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 860.dp))) {
                GogetherTheme(content)
            }
        }
        compose.waitForIdle()
    }

    private fun width(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot.width

    private fun assertNavigationLabelSizes() {
        for (label in listOf("Explore", "Create", "My Events", "Profile")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("One text layout for $label", 1, layouts.size)
            assertEquals("Unchanged font size for $label", 12.sp, layouts.single().layoutInput.style.fontSize)
            assertEquals("Unchanged line height for $label", 16.sp, layouts.single().layoutInput.style.lineHeight)
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "nav-bounce").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun filledActionShrinksThenOvershootsAndSettlesWithoutDuplicateClicks() {
        var clicks = 0
        frame {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PageActionButton({ clicks++ }, Modifier.testTag("action")) {
                    Text("Continue", Modifier.testTag("action_label"))
                }
            }
        }
        val action = compose.onNodeWithTag("action")
        action.assertWidthIsEqualTo(320.dp).assertHeightIsEqualTo(52.dp)
        val resting = width("action_label")
        val pixels = action.captureToImage().asAndroidBitmap()
        assertEquals("Approved primary orange", 0xFFF45B2B.toInt(), pixels.getPixel(pixels.width / 2, pixels.height / 5))
        capture("01-action-rest")
        compose.mainClock.autoAdvance = false
        action.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        assertEquals(.94f, width("action_label") / resting, .004f)
        assertEquals(0, clicks)
        capture("02-action-pressed")
        action.performTouchInput { up() }
        var peak = 1f
        repeat(35) {
            compose.mainClock.advanceTimeBy(16)
            peak = maxOf(peak, width("action_label") / resting)
        }
        assertTrue("Release has a small overshoot: $peak", peak > 1.005f && peak < 1.04f)
        compose.mainClock.advanceTimeBy(1600)
        assertEquals(1f, width("action_label") / resting, .001f)
        assertEquals(1, clicks)
        capture("03-action-settled")
    }

    @Test fun fastTapsCancelAndDisabledStatesNeverLeaveTheActionShrunk() {
        var enabled by mutableStateOf(true)
        var clicks = 0
        frame {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PageActionButton({ clicks++ }, Modifier.testTag("action"), enabled) {
                    Text("Continue", Modifier.testTag("action_label"))
                }
            }
        }
        val action = compose.onNodeWithTag("action")
        val resting = width("action_label")
        compose.mainClock.autoAdvance = false
        // Both events in a single frame still produce visible feedback.
        action.performTouchInput { down(center); up() }
        compose.mainClock.advanceTimeBy(64)
        assertTrue(width("action_label") / resting < .99f)
        compose.mainClock.advanceTimeBy(1800)
        assertEquals(1f, width("action_label") / resting, .001f)
        assertEquals(1, clicks)
        repeat(3) {
            action.performTouchInput { click() }
            compose.mainClock.advanceTimeBy(48)
        }
        compose.mainClock.advanceTimeBy(1800)
        assertEquals(1f, width("action_label") / resting, .001f)
        assertEquals(4, clicks)
        action.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        action.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1800)
        assertEquals(1f, width("action_label") / resting, .001f)
        assertEquals(4, clicks)
        // Becoming disabled during a press restores normal geometry immediately.
        action.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnUiThread { enabled = false }
        compose.mainClock.advanceTimeBy(80)
        action.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1800)
        action.assertIsNotEnabled()
        assertEquals(1f, width("action_label") / resting, .001f)
        assertEquals(4, clicks)
        action.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(200)
        assertEquals(1f, width("action_label") / resting, .001f)
        assertEquals(4, clicks)
    }

    @Test fun navigationUsesWhiteBackgroundSelectedSizeAndPressFeedback() {
        var selected by mutableStateOf("explore")
        var clicks = 0
        frame {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(1f))
                AppBottomNavigation(selected) { selected = it; clicks++ }
            }
        }
        val idleIcon = width("nav_icon_profile")
        assertEquals(1.40f, width("nav_icon_explore") / idleIcon, .005f)
        assertNavigationLabelSizes()
        val background = compose.onNodeWithTag("bottom_navigation").captureToImage().asAndroidBitmap()
        assertEquals(0xFFFFFFFF.toInt(), background.getPixel(3, 3))
        capture("04-navigation-explore")
        compose.mainClock.autoAdvance = false
        val profile = compose.onNodeWithTag("nav_profile")
        profile.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        assertEquals(.94f, width("nav_icon_profile") / idleIcon, .004f)
        profile.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1800)
        profile.assertIsSelected()
        compose.onNodeWithTag("nav_explore").assertIsNotSelected()
        assertEquals(1.40f, width("nav_icon_profile") / idleIcon, .005f)
        assertNavigationLabelSizes()
        assertEquals(1f, width("nav_icon_explore") / idleIcon, .005f)
        assertEquals(1, clicks)
        capture("05-navigation-profile")
        for (route in listOf("create", "my-events", "explore")) {
            compose.onNodeWithTag("nav_$route").performTouchInput { click() }
            compose.mainClock.advanceTimeBy(1800)
            compose.onNodeWithTag("nav_$route").assertIsSelected()
            assertEquals(1.40f, width("nav_icon_$route") / idleIcon, .005f)
            assertNavigationLabelSizes()
        }
        assertEquals(4, clicks)
    }

    @Test fun approvedSuccessLayoutRetainsFactsAndDoneWithoutRedundantSentence() {
        val now = System.currentTimeMillis()
        val event = DemoData.create(now).events.first().copy(title = "Badminton, anyone?")
        var exits = 0
        frame { CloudCheckInResultContent(event, "Bob", "Alice", now, { exits++ }) }
        compose.onNodeWithText("Bob, your attendance is recorded.").assertDoesNotExist()
        compose.onNodeWithText("Check-in successful").assertIsDisplayed()
        compose.onNodeWithTag("cloud_checkin_receipt").assertIsDisplayed()
        compose.onNodeWithTag("cloud_result_meeting").assertIsDisplayed()
        compose.onNodeWithTag("cloud_checkin_done").assertWidthIsEqualTo(320.dp).assertHeightIsEqualTo(52.dp)
        capture("06-checkin-success")
        compose.onNodeWithTag("cloud_checkin_done").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(1, exits)
    }
}

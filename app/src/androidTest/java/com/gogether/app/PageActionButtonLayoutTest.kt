package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.DemoData
import com.gogether.app.core.ui.LocalPageActionWidth
import com.gogether.app.feature.checkin.cloud.CloudCheckInResultContent
import com.gogether.app.feature.coordination.MyEventsScreen
import com.gogether.app.feature.event.EventDetailScreen
import com.gogether.app.feature.profile.EditProfileScreen
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class PageActionButtonLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullRowActionsKeepDoneProportionOnPhonesAndTablets() {
        val now = System.currentTimeMillis()
        val data = DemoData.create(now)
        val event = data.events.first()
        val alice = data.users.first { it.id == "alice" }
        var width by mutableIntStateOf(360)
        var height by mutableIntStateOf(740)
        var screen by mutableIntStateOf(0)
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then
                    DeviceConfigurationOverride.FontScale(1f),
            ) {
                GogetherTheme {
                    key(width, height, screen) {
                        when (screen) {
                            1 -> EditProfileScreen(alice, false, {}, {})
                            2 -> MyEventsScreen(data.copy(currentUserId = "alice"), now, openEvent = {})
                            else -> CloudCheckInResultContent(event, "Bob", "Alice", now, {})
                        }
                    }
                }
            }
        }

        // Both fixed width and fixed screen margins produce the wrong ratio away
        // from the reference phone. Check real screens, padding and button styles.
        for ((pageWidth, pageHeight) in listOf(320 to 740, 360 to 740, 400 to 740,
            600 to 960, 840 to 1100, 1024 to 768)) {
            compose.runOnIdle { width = pageWidth; height = pageHeight; screen = 0 }
            val done = compose.onNodeWithTag("cloud_checkin_done").performScrollTo()
            assertReferenceSizeAndCenter(done, pageWidth)
            val doneBounds = done.fetchSemanticsNode().boundsInRoot
            if (pageWidth == 360 || pageWidth == 840) capture("${pageWidth}-done")

            compose.runOnIdle { screen = 1 }
            val save = compose.onNodeWithTag("save_profile")
            save.assertIsNotEnabled()
            assertTrue(save.fetchSemanticsNode().boundsInRoot.width < doneBounds.width)
            compose.onNodeWithText("Cancel").assertDoesNotExist()
            compose.onNodeWithTag("interest_STUDY").performScrollTo().performClick()
            save.assertIsEnabled()

            compose.runOnIdle { screen = 2 }
            val inbox = compose.onNodeWithTag("open_inbox").performScrollTo()
            assertReferenceSizeAndCenter(inbox, pageWidth)
            assertEquals(doneBounds.width, inbox.fetchSemanticsNode().boundsInRoot.width, 1f)
        }
    }

    @Test fun joinEventStaysCompactAndRightAlignedWhilePageActionsGrow() {
        val now = System.currentTimeMillis()
        val state = DemoData.create(now).copy(currentUserId = "bob")
        val event = state.events[1]
        var width by mutableIntStateOf(360)
        var comparePreviousWidth by mutableStateOf(false)
        var joins = 0
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 960.dp))) {
                GogetherTheme {
                    // Compare old and new full-row sizing within the same viewport.
                    // ForcedSize changes density on tablets, so text-driven compact
                    // widths cannot be compared pixel-for-pixel across viewports.
                    CompositionLocalProvider(LocalPageActionWidth provides
                        if (comparePreviousWidth) 280.dp else LocalPageActionWidth.current) {
                        key(width) { EventDetailScreen(event, state, now, false, { joins++ }, {}, {}, {}) }
                    }
                }
            }
        }
        for (pageWidth in listOf(360, 840)) {
            compose.runOnIdle { width = pageWidth }
            assertReferenceSizeAndCenter(compose.onNodeWithTag("open_questions").performScrollTo(), pageWidth)
            val join = compose.onNodeWithTag("join_event").performScrollTo().assertIsDisplayed()
            join.assertTextContains("Join Event")
            val buttonBounds = join.fetchSemanticsNode().boundsInRoot
            val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue(buttonBounds.center.x > rootBounds.center.x)
            assertTrue(buttonBounds.width < rootBounds.width / 2)
            capture("${pageWidth}-join-event-unchanged")
            compose.runOnIdle { comparePreviousWidth = true }
            val before = join.performScrollTo().fetchSemanticsNode().boundsInRoot
            assertEquals(before.width, buttonBounds.width, 1f)
            assertEquals(before.height, buttonBounds.height, 1f)
            assertEquals(before.center.x, buttonBounds.center.x, 1f)
            compose.runOnIdle { comparePreviousWidth = false }
            join.performClick()
        }
        compose.runOnIdle { assertEquals(2, joins) }
    }

    private fun assertReferenceSizeAndCenter(button: SemanticsNodeInteraction, pageWidth: Int) {
        button.assertIsDisplayed().assertWidthIsEqualTo((pageWidth * 0.8f).dp).assertHeightIsEqualTo(52.dp)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val bounds = button.fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x, bounds.center.x, 1f)
        assertEquals(0.8f, bounds.width / root.width, .002f)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "responsive-button-width").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

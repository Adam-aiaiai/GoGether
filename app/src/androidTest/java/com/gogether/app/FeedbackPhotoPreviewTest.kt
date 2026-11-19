package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.io.File

@OptIn(ExperimentalTestApi::class)
class FeedbackPhotoPreviewTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = FeedbackPhotoStore(context)
    private val session = UUID.randomUUID().toString()
    private val ids = mutableListOf<String>()
    private var viewer by mutableStateOf("bob")
    private var activity by mutableStateOf(session)
    private var photos by mutableStateOf<List<String>>(emptyList())

    private fun launch(count: Int = 3) {
        runBlocking {
            repeat(count) { index ->
                val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.rgb(50 + index * 60, 100, 160))
                val bytes = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
                bitmap.recycle()
                ids += store.importImage("bob", session, ByteArrayInputStream(bytes))
            }
        }
        photos = ids.toList()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp))) {
                GogetherTheme { FeedbackPhotoStrip(photos, viewer, activity) }
            }
        }
    }

    private fun open(index: Int = 0) {
        compose.onNodeWithTag("feedback_photo_$index").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("活动照片预览").fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun zoom(id: String) = compose.onNodeWithTag("photo_zoom_$id")
    private fun scale(id: String): Int = zoom(id).fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        .removePrefix("缩放 ").removeSuffix("%").toInt()

    @After fun cleanup() { ids.forEach { store.removeDraft(it, "bob", session) } }

    @Test fun opensSelectedPhotoSwipesBothWaysAndStopsAtEnds() {
        launch(); open(1)
        compose.onNodeWithTag("photo_page_count").assertTextEquals("2 / 3")
        compose.onNodeWithTag("feedback_photo_preview").captureToImage().asAndroidBitmap().let { image ->
            File(context.getExternalFilesDir(null), "feedback-photo-gallery.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("photo_pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("photo_page_count").assertTextEquals("3 / 3")
        compose.onNodeWithText("下一张").assertIsNotEnabled()
        compose.onNodeWithTag("photo_pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("photo_page_count").assertTextEquals("2 / 3")
        compose.onNodeWithText("上一张").performClick()
        compose.onNodeWithTag("photo_page_count").assertTextEquals("1 / 3")
        compose.onNodeWithText("上一张").assertIsNotEnabled()
        compose.onNodeWithText("< 返回").performClick()
        compose.onNodeWithTag("feedback_photo_preview").assertDoesNotExist()
    }

    @Test fun pinchZoomPanAndDoubleTapResetThenSwipeToNextPhoto() {
        launch(); open()
        zoom(ids[0]).performTouchInput {
            pinch(Offset(center.x - 35, center.y), Offset(center.x + 35, center.y),
                Offset(center.x - 130, center.y), Offset(center.x + 130, center.y))
        }
        assertTrue("Pinch enlarges the image", scale(ids[0]) > 100)
        zoom(ids[0]).performTouchInput { swipeLeft() }
        compose.onNodeWithTag("photo_page_count").assertTextEquals("1 / 3")
        zoom(ids[0]).performTouchInput { doubleClick() }
        assertEquals(100, scale(ids[0]))
        compose.onNodeWithTag("photo_pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("photo_page_count").assertTextEquals("2 / 3")
        assertEquals(100, scale(ids[1]))
    }

    @Test fun singlePhotoHasNoNextPage() {
        launch(1); open()
        compose.onNodeWithTag("photo_page_count").assertTextEquals("1 / 1")
        compose.onNodeWithText("上一张").assertIsNotEnabled()
        compose.onNodeWithText("下一张").assertIsNotEnabled()
        compose.onNodeWithTag("photo_pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("photo_page_count").assertTextEquals("1 / 1")
    }

    @Test fun buttonNavigationResetsZoomOnReturningToPreviousPhoto() {
        launch(); open()
        zoom(ids[0]).performTouchInput {
            pinch(Offset(center.x - 35, center.y), Offset(center.x + 35, center.y),
                Offset(center.x - 130, center.y), Offset(center.x + 130, center.y))
        }
        assertTrue(scale(ids[0]) > 100)
        compose.onNodeWithText("下一张").performClick()
        compose.onNodeWithTag("photo_page_count").assertTextEquals("2 / 3")
        compose.onNodeWithText("上一张").performClick()
        assertEquals(100, scale(ids[0]))
    }

    @Test fun changingAccountActivityOrPhotoListClosesPreviewAndCannotReadDraft() {
        launch(); open()
        compose.runOnIdle { viewer = "carol" }
        compose.onNodeWithTag("feedback_photo_preview").assertDoesNotExist()
        compose.onNodeWithTag("feedback_photo_0").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("图片不可用").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("活动照片预览").assertCountEquals(0)
        compose.runOnIdle { viewer = "bob"; activity = "other-event" }
        compose.onNodeWithTag("feedback_photo_preview").assertDoesNotExist()
        compose.onNodeWithTag("feedback_photo_0").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("图片不可用").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("活动照片预览").assertCountEquals(0)
        compose.runOnIdle { activity = session }
        open()
        compose.runOnIdle { photos = photos.drop(1) }
        compose.onNodeWithTag("feedback_photo_preview").assertDoesNotExist()
    }
}

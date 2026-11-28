package com.gogether.app

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.DemoData
import com.gogether.app.core.data.cloud.*
import com.gogether.app.feature.checkin.cloud.CloudFeedbackContent
import com.gogether.app.ui.theme.GogetherTheme
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CloudReviewGalleryUiTest {
    @get:Rule val compose = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private var actor by mutableStateOf("alice")
    private var ids by mutableStateOf(listOf("photo-a", "photo-b", "photo-c"))
    private var readCount = 0
    private fun launch(own: Boolean = false) {
        val bytes = inst.context.assets.open("review-badminton.jpg").use { it.readBytes() }
        val backend = object : CloudOperations {
            override val storage: FirebaseStorage get() = error("No uploads in this test")
            override val sync = MutableStateFlow(CloudSyncState())
            override suspend fun refresh() = Unit
            override suspend fun call(action: String, payload: Map<String, Any?>, requestId: String?): Map<String, Any?> {
                check(action == "mediaRead")
                readCount++
                return mapOf("base64" to Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
        }
        val now = System.currentTimeMillis()
        val event = DemoData.create(now).event("event-1")!!.copy(id = "polish-gallery")
        compose.setContent { GogetherTheme {
            val review = mapOf("id" to "polish-review", "authorName" to "Bob", "rating" to 4,
                "comment" to "Friendly group and clear directions.", "createdAt" to now,
                "details" to mapOf("photos" to ids))
            CloudFeedbackContent(backend, event, actor, false, true, if (own) review else null,
                if (own) emptyList() else listOf(review), false, {}, {}, "Alice", {})
        } }
    }
    private fun assertHorizontalThumbnails(): Float {
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Review photo").fetchSemanticsNodes().size == ids.size }
        val bounds = ids.map { compose.onNodeWithTag("cloud_review_photo_$it").fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (left, right) ->
            assertEquals("Photos share one row", left.top, right.top, 1f)
            assertEquals("Thumbnails have equal widths", left.width, right.width, 1f)
            assertTrue("Photos have a visible gap", right.left > left.right)
        }
        return bounds.first().width
    }
    private fun open(index: Int) {
        assertHorizontalThumbnails()
        compose.onAllNodesWithContentDescription("Review photo")[index].performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Event photo preview").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(200)
        val folder = File(inst.targetContext.getExternalFilesDir(null), "review-photo-row").apply { mkdirs() }
        val bitmap = inst.uiAutomation.takeScreenshot()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun publicReviewSupportsArrowsSwipeZoomAndCornerClose() {
        launch(); assertHorizontalThumbnails()
        capture("01-public-review-thumbnails")
        open(1)
        compose.onNodeWithTag("photo_page_count").assertTextEquals("2 / 3")
        capture("02-cloud-gallery")
        val photo = compose.onNodeWithTag("photo_pager").fetchSemanticsNode().boundsInRoot
        val close = compose.onNodeWithTag("photo_preview_close").fetchSemanticsNode().boundsInRoot
        assertTrue(close.center.x > photo.right && close.center.y < photo.top)
        val zoom = compose.onNodeWithTag("photo_zoom_photo-b")
        zoom.performTouchInput { pinch(Offset(center.x-35,center.y), Offset(center.x+35,center.y),
            Offset(center.x-130,center.y), Offset(center.x+130,center.y)) }
        assertNotEquals("Zoom 100%", zoom.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        zoom.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("photo_page_count").assertTextEquals("2 / 3")
        zoom.performTouchInput { doubleClick() }
        compose.onNodeWithTag("photo_next").performClick()
        compose.onNodeWithTag("photo_page_count").assertTextEquals("3 / 3")
        compose.onNodeWithTag("photo_next").assertIsNotEnabled()
        compose.onNodeWithTag("photo_pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("photo_previous").performClick()
        compose.onNodeWithTag("photo_page_count").assertTextEquals("1 / 3")
        compose.onNodeWithTag("photo_previous").assertIsNotEnabled()
        compose.onNodeWithTag("photo_preview_close").performClick()
        compose.onNodeWithTag("photo_pager").assertDoesNotExist()
    }
    @Test fun ownReviewGalleryClosesAndReloadsWhenViewerOrMediaChanges() {
        launch(own = true)
        val thumbnailWidth = assertHorizontalThumbnails()
        capture("03-own-review-thumbnails")
        open(0)
        compose.runOnIdle { actor = "bob" }
        compose.onNodeWithTag("photo_pager").assertDoesNotExist()
        open(0)
        compose.runOnIdle { assertTrue("Changed viewer must read protected media again", readCount >= 6); ids = ids.drop(1) }
        compose.onNodeWithTag("photo_pager").assertDoesNotExist()
        assertEquals(thumbnailWidth, assertHorizontalThumbnails(), 1f)
        open(0)
        compose.onNodeWithTag("photo_page_count").assertTextEquals("1 / 2")
        compose.onNodeWithTag("photo_preview_close").performClick()
        capture("04-two-photos")
        compose.runOnIdle { ids = ids.take(1) }
        assertEquals(thumbnailWidth, assertHorizontalThumbnails(), 1f)
        capture("05-one-photo")
        open(0)
        compose.onNodeWithTag("photo_page_count").assertTextEquals("1 / 1")
        compose.onNodeWithTag("photo_next").assertDoesNotExist()
        compose.runOnIdle { ids = emptyList() }
        compose.onNodeWithTag("photo_pager").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Review photo").assertCountEquals(0)
    }
}

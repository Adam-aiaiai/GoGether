package com.gogether.app

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.DemoData
import com.gogether.app.core.data.cloud.CloudOperations
import com.gogether.app.core.data.cloud.CloudSyncState
import com.gogether.app.feature.checkin.cloud.CloudFeedbackContent
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.ZonedDateTime

@OptIn(ExperimentalTestApi::class)
class ReviewFeedUiTest {
    @get:Rule val compose = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val time = ZonedDateTime.parse("2026-11-27T12:20:00+08:00").toInstant().toEpochMilli()
    private val event = DemoData.create(time).event("event-1")!!.copy(id = "review-feed-preview")
    private val bytes by lazy { inst.context.assets.open("review-badminton.jpg").use { it.readBytes() } }
    private val backend = object : CloudOperations {
        override val storage: FirebaseStorage get() = error("This fixture never uploads")
        override val sync = MutableStateFlow(CloudSyncState())
        override suspend fun refresh() = Unit
        override suspend fun call(action: String, payload: Map<String, Any?>, requestId: String?): Map<String, Any?> {
            check(action == "mediaRead")
            return mapOf("base64" to Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
    }
    private fun review(id: String, name: String, score: Int, text: String, details: Map<String, Any?> = emptyMap()) =
        mapOf("id" to id, "authorName" to name, "rating" to score, "comment" to text,
            "createdAt" to (time - if (id == "bob") 0L else 86_400_000L), "details" to details)
    private val publicReviews get() = listOf(
        review("bob", "Bob", 4, "Friendly group and clear directions. I'd join again.",
            mapOf("photos" to listOf("photo-a", "photo-b", "photo-c"))),
        review("anonymous", "Private name must not be rendered", 5,
            "A welcoming group and a great pace for beginners.", mapOf("anonymous" to true,
                "organization" to 5, "venue" to 4, "atmosphere" to 5,
                "tags" to listOf("welcoming", "beginner_friendly"))),
        review("carol", "Carol", 4,
            "Really enjoyed the games. A clearer meeting point would help next time.",
            mapOf("tags" to listOf("clearer_meeting"), "improvementComment" to "Allow a little more time to find the court.")),
    )
    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File(inst.targetContext.getExternalFilesDir(null), "review-feed-redesign").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun cloudFeedGroupsPhotosAndDetailsAndNeverShowsAnAnonymousIdentity() {
        compose.setContent { GogetherTheme {
            CloudFeedbackContent(backend,event,"alice",false,true,null,publicReviews,false,{}, {}, "Alice", {})
        } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Review photo").fetchSemanticsNodes().size == 3 }
        compose.onAllNodesWithText("Event reviews").assertCountEquals(1)
        compose.onNodeWithText("Private name must not be rendered").assertDoesNotExist()
        compose.onNodeWithText("Anonymous participant").assertExists()
        compose.onNodeWithText("Bob").assertIsDisplayed()
        val bob = compose.onNodeWithTag("cloud_review_bob").fetchSemanticsNode().boundsInRoot
        val comment = compose.onNodeWithText("Friendly group and clear directions. I'd join again.").fetchSemanticsNode().boundsInRoot
        val photo = compose.onNodeWithTag("cloud_review_photo_photo-a").fetchSemanticsNode().boundsInRoot
        assertTrue(photo.top > comment.bottom && bob.bottom >= photo.bottom)
        capture("01-event-reviews")
        compose.onNodeWithText("Allow a little more time to find the court.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("saved_review_dimensions").assertTextEquals("Organization 5 · Venue 4 · Atmosphere 5")
        compose.onNodeWithText("Welcoming").assertExists()
        compose.onNodeWithText("Beginner friendly").assertExists()
        compose.onNodeWithText("Meeting directions").assertExists()
        capture("02-event-reviews-details")
    }
    @Test fun ownPrivateReviewKeepsAllOptionalFieldsInsideTheReview() {
        val own = review("own", "Bob", 4, "Friendly group and clear directions.", mapOf(
            "anonymous" to true, "visibility" to "ORGANIZER_ONLY", "organization" to 5,
            "venue" to 4, "atmosphere" to 5, "photos" to listOf("photo-a"),
            "tags" to listOf("welcoming", "clearer_meeting"),
            "positiveComment" to "Everyone made beginners feel welcome.",
            "improvementComment" to "Allow more time to find the court."))
        compose.setContent { GogetherTheme {
            CloudFeedbackContent(backend,event,"bob",true,true,own,emptyList(),false,{}, {}, "Alice", {})
        } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Review photo").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Anonymous participant (you)").assertExists()
        compose.onNodeWithText("Bob").assertDoesNotExist()
        compose.onNodeWithText("Organizer only").assertExists()
        compose.onNodeWithText("Everyone made beginners feel welcome.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Allow more time to find the court.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cloud_feedback_saved").assertExists()
        capture("03-private-review")
    }
    @Test fun narrowLargeTextLocalFeedWrapsNamesDatesAndOptionalDetails() {
        val review = VisibleFeedback("Private name", true, false, 5, "Everyone was welcoming.", time,
            FeedbackDetails(5,4,5,listOf("welcoming","beginner_friendly"),anonymous = true,
                visibility = FeedbackVisibility.ORGANIZER_ONLY, positiveComment = "Plenty of support for beginners."))
        val session = PrototypeSession(java.util.UUID.randomUUID().toString(),time-7_200_000,time)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp,568.dp)) then
                DeviceConfigurationOverride.FontScale(1.6f)) {
                GogetherTheme { ActivityReviewsScreen("alice",session,listOf(review),false,"",{}, {}) }
            }
        }
        compose.onNodeWithText("Private name").assertDoesNotExist()
        compose.onNodeWithTag("activity_reviews_list").performScrollToNode(hasText("Anonymous participant"))
        capture("04-local-large-text-header")
        listOf("Anonymous participant", "27 Nov 2026", "Organizer only").forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, substring = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            // Text measures round to integer pixels; inspect the painted lines with
            // one pixel of rounding tolerance instead of the paragraph's float width.
            assertTrue("No clipping for $text: ${layouts.map { "${it.size} width=${it.multiParagraph.width}" }}",
                layouts.isNotEmpty() && layouts.all { layout ->
                    !layout.didOverflowHeight && (0 until layout.lineCount).all { line ->
                        !layout.isLineEllipsized(line) && layout.getLineLeft(line) >= -1f &&
                            layout.getLineRight(line) <= layout.size.width + 1f
                    }
                })
        }
        compose.onNodeWithTag("activity_reviews_list").performScrollToNode(hasText("Plenty of support for beginners."))
        compose.onNodeWithText("Plenty of support for beginners.").assertIsDisplayed()
        capture("05-local-large-text-details")
    }
}

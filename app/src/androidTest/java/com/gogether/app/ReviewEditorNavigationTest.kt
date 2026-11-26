package com.gogether.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.*
import com.gogether.app.core.data.cloud.*
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.AppUi
import com.gogether.app.feature.checkin.cloud.*
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

@OptIn(ExperimentalTestApi::class)
class ReviewEditorNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val clock = System.currentTimeMillis()
    private val id = "review-layout-${UUID.randomUUID()}"
    private val drafts = CloudFeedbackDraftStore(context)
    private val photoStore = FeedbackPhotoStore(context)
    private val photoIds = mutableListOf<String>()
    private val cover = File(context.cacheDir, "$id.jpg")
    private var showing by mutableStateOf(true)
    private var eventId by mutableStateOf(id)
    private var width by mutableStateOf(400)
    private var scale by mutableStateOf(1f)
    private lateinit var repository: FakeGogetherRepository
    private val backend = object : CloudOperations {
        override val storage: FirebaseStorage get() = error("This test must never upload or submit")
        override val sync = MutableStateFlow(CloudSyncState())
        override suspend fun refresh() = Unit
        override suspend fun call(action: String, payload: Map<String, Any?>, requestId: String?): Map<String, Any?> = when (action) {
            "eventInfo" -> mapOf("serverNow" to clock, "meeting" to null, "receipt" to null, "ownFeedback" to null)
            "reviews" -> mapOf("reviews" to emptyList<Any>())
            else -> error("Unexpected action: $action")
        }
    }
    private fun launch(withPhoto: Boolean = false) {
        if (withPhoto) {
            val bytes = instrumentation.context.assets.open("review-badminton.jpg").use { it.readBytes() }
            cover.writeBytes(bytes)
            runBlocking { photoIds += photoStore.importImage("bob", id, bytes.inputStream()) }
            drafts.save("bob", id, FeedbackDraft(4, "Friendly group and clear directions. I'd join again.",
                FeedbackDetails(5,4,5,listOf("well_organized","welcoming"),true,FeedbackVisibility.ORGANIZER_ONLY,photoIds.toList())))
        }
        val base = DemoData.create(clock)
        val event = base.event("event-history")!!.copy(id = id, title = "Campus badminton meetup", organizerId = "alice",
            startAt = clock - 7_200_000, endAt = clock - 3_600_000, category = Category.SPORTS,
            imageUri = if (withPhoto) Uri.fromFile(cover).toString() else "")
        val registrations = listOf(id,"$id-other").flatMap { eventKey ->
            listOf("bob","carol").map { actor -> Registration("$eventKey-$actor",eventKey,actor,RegistrationStatus.CONFIRMED,clock - 10_000,clock - 10_000,0) }
        }
        repository = FakeGogetherRepository(base.copy(currentUserId = "bob", events = listOf(event,event.copy(id="$id-other")),registrations=registrations))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp,860.dp)) then DeviceConfigurationOverride.FontScale(scale)) {
                GogetherTheme { if(showing) CloudCheckInScreen(repository,backend,eventId,clock,{showing=false}) }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("feedback_editor").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(450)
        val directory = File(context.getExternalFilesDir(null),"review-fields").apply { mkdirs() }
        File(directory,"$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
    private fun assertDetailsOverview() {
        compose.onNodeWithText("Review details").assertIsDisplayed()
        compose.onNodeWithText("Campus badminton meetup").assertIsDisplayed()
        compose.onNodeWithText("Specific ratings").assertIsDisplayed()
    }

    @Test fun visibilityDialogKeepsChoicesInDraftWithoutSubmittingAndSupportsLargeText() {
        launch(withPhoto = true)
        compose.reviewNode("feedback_visibility_ORGANIZER_ONLY").assertIsSelected()
        compose.onNodeWithText("Who can see this?").assertIsDisplayed()
        compose.onNodeWithTag("feedback_visibility_done").assertIsDisplayed()
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val folder = File(context.getExternalFilesDir(null), "square-dialogs").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "03-review-visibility.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()

        compose.onNodeWithTag("feedback_visibility_PUBLIC").performClick().assertIsSelected()
        compose.onNodeWithTag("feedback_visibility_done").performClick()
        compose.onNodeWithTag("feedback_visibility_dialog").assertDoesNotExist()
        assertEquals(FeedbackVisibility.PUBLIC, drafts.load("bob", id).details.visibility)
        assertTrue(drafts.load("bob", id).details.anonymous)
        assertEquals(photoIds, drafts.load("bob", id).details.photos)
        assertTrue(repository.state.value.feedback.isEmpty())

        compose.runOnIdle { width = 320; scale = 1.5f }
        compose.reviewNode("feedback_visibility_ORGANIZER_ONLY").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("feedback_visibility_done").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("feedback_visibility_dialog").assertDoesNotExist()
        assertEquals(FeedbackVisibility.ORGANIZER_ONLY, drafts.load("bob", id).details.visibility)
        compose.reviewNode("feedback_visibility_ORGANIZER_ONLY").assertIsSelected()
        Espresso.pressBack()
        compose.onNodeWithTag("feedback_visibility_dialog").assertDoesNotExist()
        assertTrue(repository.state.value.feedback.isEmpty())
    }
    @Test fun singleOptionalEntryOpensRatingsAndTagsAndReturnsWithoutSubmitting() {
        launch(withPhoto=true)
        compose.onNodeWithText("Write a review").assertIsDisplayed()
        compose.onNodeWithTag("feedback_organization_5").assertDoesNotExist()
        compose.onNodeWithTag("cloud_submit_feedback").assertIsDisplayed()
        compose.onNodeWithTag("feedback_visibility").assertIsDisplayed()
        compose.onAllNodesWithTag("feedback_open_details").assertCountEquals(1)
        compose.onAllNodesWithText("Optional").assertCountEquals(1)
        compose.onNodeWithTag("feedback_open_details").assertTextContains("More details")
        compose.onNodeWithTag("feedback_open_ratings").assertDoesNotExist()
        compose.onNodeWithText("Add more detail").assertDoesNotExist()
        val entry = compose.onNodeWithTag("feedback_open_details").fetchSemanticsNode().boundsInRoot
        val rating = compose.onNodeWithTag("feedback_rating_4").fetchSemanticsNode().boundsInRoot
        val comment = compose.onNodeWithTag("cloud_feedback_comment").fetchSemanticsNode().boundsInRoot
        assertTrue(entry.top >= rating.bottom && entry.bottom < comment.top)
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("View photo 1").fetchSemanticsNodes().isNotEmpty() }
        capture("01-write-review-phone")
        compose.onNodeWithTag("feedback_open_details").performClick()
        assertDetailsOverview()
        compose.onNodeWithTag("cloud_submit_feedback").assertDoesNotExist()
        val label=compose.onNodeWithTag("feedback_dimension_label_organization").fetchSemanticsNode().boundsInRoot
        val star=compose.onNodeWithTag("feedback_organization_5").fetchSemanticsNode().boundsInRoot
        assertEquals(label.center.y,star.center.y,2f)
        assertTrue(label.right <= star.left)
        compose.onNodeWithTag("feedback_tag_welcoming").assertIsSelected()
        capture("02-review-details-phone")
        compose.reviewNode("feedback_positive_comment").performTextInput("Everyone made beginners feel welcome.")
        Espresso.closeSoftKeyboard()
        capture("02a-what-went-well")
        compose.reviewNode("feedback_improvement_comment").performTextInput("Allow a little more time for warming up.")
        Espresso.closeSoftKeyboard()
        capture("02b-what-could-improve")
        compose.reviewNode("feedback_venue_3")
        compose.onNodeWithTag("feedback_venue_3").performClick()
        compose.onNodeWithTag("feedback_details_done").performClick()
        compose.onNodeWithTag("cloud_feedback_comment").assertTextContains("Friendly group",substring=true)
        compose.onNodeWithTag("feedback_open_details").performClick()
        assertDetailsOverview()
        compose.reviewNode("feedback_tag_welcoming").assertIsSelected().performClick()
        compose.reviewNode("feedback_venue_3").assertIsSelected()
        Espresso.pressBack()
        compose.onNodeWithText("Write a review").assertIsDisplayed()
        compose.onNodeWithTag("feedback_open_details").performClick()
        assertDetailsOverview()
        compose.reviewNode("feedback_tag_welcoming").assertIsNotSelected()
        compose.onNodeWithTag("feedback_details_done").performClick()
        compose.reviewNode("feedback_photo_0").performClick()
        compose.onNodeWithTag("feedback_photo_preview").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { showing=true }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("feedback_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.reviewNode("feedback_venue_3").assertIsSelected()
        compose.reviewNode("feedback_anonymous").assertIsOn()
        compose.reviewNode("feedback_visibility_ORGANIZER_ONLY").assertIsSelected()
        compose.reviewNode("feedback_photo_0").assertExists()
        compose.reviewNode("feedback_positive_comment").assertTextContains("Everyone made beginners feel welcome.")
        compose.reviewNode("feedback_improvement_comment").assertTextContains("Allow a little more time for warming up.")
        assertEquals(3,drafts.load("bob",id).details.venue)
        assertTrue(repository.state.value.feedback.isEmpty())
    }
    @Test fun accountAndEventSwitchesDoNotCarryOptionalChoicesOrPrivacy() {
        launch()
        compose.reviewNode("feedback_organization_4").performClick()
        compose.reviewNode("feedback_tag_welcoming").performClick()
        compose.reviewNode("feedback_positive_comment").performTextInput("Friendly teammates.")
        Espresso.closeSoftKeyboard()
        compose.reviewNode("feedback_improvement_comment").performTextInput("More warm-up time.")
        Espresso.closeSoftKeyboard()
        compose.reviewNode("cloud_feedback_comment").performTextInput("A welcoming group.")
        Espresso.closeSoftKeyboard()
        compose.reviewNode("feedback_anonymous").performClick()
        compose.reviewNode("feedback_visibility_ORGANIZER_ONLY").performClick()
        compose.reviewNode("cloud_submit_feedback").assertIsNotEnabled()
        runBlocking { repository.login("carol").getOrThrow() }
        compose.reviewNode("cloud_feedback_comment").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.reviewNode("feedback_anonymous").assertIsOff()
        compose.reviewNode("feedback_positive_comment").assertTextContains("0/500")
        compose.reviewNode("feedback_improvement_comment").assertTextContains("0/500")
        runBlocking { repository.login("bob").getOrThrow() }
        compose.reviewNode("cloud_feedback_comment").assertTextContains("A welcoming group.")
        compose.runOnIdle { eventId="$id-other" }
        compose.reviewNode("cloud_feedback_comment").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.reviewNode("feedback_organization_4").assertIsNotSelected()
        compose.reviewNode("feedback_positive_comment").assertTextContains("0/500")
        compose.reviewNode("feedback_improvement_comment").assertTextContains("0/500")
        compose.runOnIdle { eventId=id }
        compose.reviewNode("feedback_organization_4").assertIsSelected()
        compose.reviewNode("feedback_positive_comment").assertTextContains("Friendly teammates.")
        compose.reviewNode("feedback_improvement_comment").assertTextContains("More warm-up time.")
    }
    @Test fun tabletAndLargeTextKeepControlsReachable() {
        launch(withPhoto=true)
        compose.runOnIdle { width=840 }
        capture("03-write-review-tablet")
        compose.onNodeWithTag("feedback_open_details").performClick()
        assertDetailsOverview()
        capture("04-review-details-tablet")
        compose.onNodeWithTag("feedback_details_done").assertIsDisplayed().performClick()
        compose.runOnIdle { width=320; scale=1.6f }
        compose.reviewNode("feedback_rating_5").performClick()
        compose.reviewNode("cloud_submit_feedback").assertIsDisplayed()
        compose.reviewNode("feedback_organization_4").performClick()
        compose.onNodeWithTag("feedback_details_done").assertIsDisplayed().performClick()
        compose.reviewNode("feedback_open_details").performClick()
        compose.reviewNode("feedback_tag_more_equipment").assertIsDisplayed()
        compose.onNodeWithTag("feedback_details_done").performClick()
        compose.reviewNode("feedback_visibility_ORGANIZER_ONLY").assertIsSelected()
    }
    @Test fun reviewActionsUseCapsulesAndDetailsDoneKeepsEightyPercentWidth() {
        launch(withPhoto = true)
        for (pageWidth in listOf(320, 400, 840)) {
            compose.runOnIdle { width = pageWidth }
            val submit = compose.onNodeWithTag("cloud_submit_feedback").assertIsDisplayed()
            submit.assertWidthIsEqualTo(((pageWidth - 40 - 12) / 2f).dp).assertHeightIsEqualTo(52.dp)
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue(submit.fetchSemanticsNode().boundsInRoot.center.x > root.center.x)
            val labelLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText("Submit review").performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(labelLayouts) }
            capture("buttons-${pageWidth}-review-main")
            // Center-aligned line positions include the paragraph offset. Compare the
            // line's extent with the actual button content area, not that offset.
            val pixelsPerDp = root.width / pageWidth
            val availableTextWidth = submit.fetchSemanticsNode().boundsInRoot.width - 24f * pixelsPerDp
            val availableTextHeight = submit.fetchSemanticsNode().boundsInRoot.height - 16f * pixelsPerDp
            assertTrue("Submit review at $pageWidth dp: " + labelLayouts.joinToString {
                "lines=${it.lineCount}, size=${it.size}, right=${it.getLineRight(0)}, bottom=${it.getLineBottom(0)}" },
                labelLayouts.isNotEmpty() && labelLayouts.all {
                    it.lineCount == 1 && !it.isLineEllipsized(0) &&
                        it.getLineRight(0) - it.getLineLeft(0) <= availableTextWidth + 1f &&
                        it.getLineBottom(0) - it.getLineTop(0) <= availableTextHeight + 1f
                })
            assertCapsule(submit)

            compose.onNodeWithTag("feedback_open_details").performClick()
            val done = compose.onNodeWithTag("feedback_details_done").assertIsDisplayed()
            done.assertWidthIsEqualTo((pageWidth * .8f).dp).assertHeightIsEqualTo(52.dp)
            val bounds = done.fetchSemanticsNode().boundsInRoot
            assertEquals(root.center.x, bounds.center.x, 1f)
            assertEquals(.8f, bounds.width / root.width, .002f)
            assertCapsule(done)
            capture("buttons-${pageWidth}-review-details")
            done.performClick()
            compose.onNodeWithText("Write a review").assertIsDisplayed()
            assertEquals(4, drafts.load("bob", id).rating)
        }
    }
    private fun assertCapsule(button: SemanticsNodeInteraction) {
        val pixels = button.captureToImage().toPixelMap()
        val x = (pixels.height * .2f).toInt()
        val y = (pixels.height * .06f).toInt()
        // An 8 dp rounded rectangle fills these corners; a semicircular end does not.
        assertEquals(AppUi.Primary, pixels[pixels.width / 2, y])
        assertNotEquals(AppUi.Primary, pixels[x, y])
        assertNotEquals(AppUi.Primary, pixels[pixels.width - 1 - x, y])
    }
    @Test fun eachOverallRatingShowsItsDescriptionAndOptionalNotesCanStayEmpty() {
        launch()
        compose.onNodeWithTag("cloud_submit_feedback").assertIsNotEnabled()
        listOf("Poor", "Not great", "Okay", "Good", "Excellent").forEachIndexed { index, label ->
            compose.reviewNode("feedback_rating_${index + 1}").performClick()
            compose.onNodeWithTag("feedback_rating_label").assertTextEquals(label)
            compose.onNodeWithTag("cloud_submit_feedback").assertIsEnabled()
        }
        assertEquals("", drafts.load("bob", id).details.positiveComment)
        assertEquals("", drafts.load("bob", id).details.improvementComment)
        compose.reviewNode("feedback_positive_comment").performTextInput("x".repeat(501))
        Espresso.closeSoftKeyboard()
        assertEquals(500, drafts.load("bob", id).details.positiveComment.length)
    }
    @After fun cleanup() {
        listOf(id,"$id-other").forEach { event -> listOf("bob","carol").forEach { drafts.clear(it,event) } }
        photoIds.forEach { photoStore.removeDraft(it,"bob",id) }
        cover.delete()
    }
}

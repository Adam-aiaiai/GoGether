package com.gogether.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

@OptIn(ExperimentalTestApi::class)
class FeedbackPrivacyUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val time = 1_800_000_000_000L
    private val session = PrototypeSession(UUID.randomUUID().toString(), MeetingPoint(31.27, 120.74), time - 7_200_000, time)
    private val store = PreferencesPrototypeStore(context)
    private val prefs = context.getSharedPreferences("checkin_prototype_v1", 0)
    private var selectedUris = emptyList<Uri>()
    private val createdUris = mutableListOf<Uri>()
    private var actor by mutableStateOf("bob")
    private var records by mutableStateOf(listOf(PrototypeHistoryEntry(session, null, null, false)))
    private var message by mutableStateOf("")
    private var failSubmission = false
    private val backend = LocalPrototypeBackend(store, { time }, { 0 })
    private val registry = object : ActivityResultRegistry() {
        @Suppress("UNCHECKED_CAST")
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            dispatchResult(requestCode, selectedUris as O)
        }
    }

    private fun find(tag: String): SemanticsNodeInteraction {
        return compose.reviewNode(tag)
    }

    private fun fixture(): Uri {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "feedback-ui-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        })!!
        createdUris += uri
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(66, 127, 108))
            val paint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 6f }
            drawRect(65f, 45f, 575f, 435f, paint)
            drawLine(320f, 45f, 320f, 435f, paint)
            drawLine(65f, 240f, 575f, 240f, paint)
        }
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return uri
    }

    private fun launch(scale: Float = 1f, systemPicker: Boolean = false): StateRestorationTester {
        listOf("alice", "bob", "carol").forEach { store.rememberSession(it, session) }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val owner = if (systemPicker) requireNotNull(LocalActivityResultRegistryOwner.current) else object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp)) then
                    DeviceConfigurationOverride.FontScale(scale)) {
                    GogetherTheme { PrototypeHistoryScreen(actor, records, time, false, message, {}, { id, score, comment, details ->
                        if (failSubmission) message = "Could not save. Please try again."
                        else { backend.submitFeedback(actor, id, score, comment, details); records = backend.history(actor); message = "" }
                    }, initialSessionId = session.id) }
                }
            }
        }
        return restoration
    }

    @Test fun selectedPhotosPreviewRemovalPrivacyDraftRestorationAndSaveWorkTogether() {
        try {
            selectedUris = listOf(fixture(), fixture(), fixture())
            val restoration = launch()
            compose.onNodeWithTag("feedback_rating_4").performClick()
            find("feedback_add_photo").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("feedback_photo_2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("feedback_add_photo").assertDoesNotExist()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("View photo 1").fetchSemanticsNodes().isNotEmpty() }
            capture("feedback-photos.png")
            compose.onNodeWithTag("feedback_photo_0").performClick()
            compose.onNodeWithTag("feedback_photo_preview").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Back").onLast().performClick()
            compose.onNodeWithTag("feedback_photo_preview").assertDoesNotExist()
            find("feedback_remove_photo_1").performScrollTo().assertIsDisplayed().performClick()
            find("feedback_add_photo").assertExists()
            find("feedback_visibility_PUBLIC").assertIsSelected()
            find("feedback_anonymous").performClick()
            find("feedback_visibility_ORGANIZER_ONLY").performClick()
            capture("feedback-privacy.png")
            restoration.emulateSavedInstanceStateRestore()
            find("feedback_anonymous").assertIsOn()
            find("feedback_visibility_ORGANIZER_ONLY").assertIsSelected()
            find("feedback_photo_1").assertExists()
            compose.runOnIdle { failSubmission = true }
            compose.reviewNode("feedback_submit").performClick()
            compose.onNodeWithText("Could not save. Please try again.").assertIsDisplayed()
            find("feedback_anonymous").assertIsOn()
            compose.runOnIdle { failSubmission = false }
            compose.reviewNode("feedback_submit").performClick()
            compose.reviewNode("feedback_submit").assertDoesNotExist()
            val saved = store.feedback(session.id, "bob")!!
            assertEquals(2, saved.details.photos.size)
            assertTrue(saved.details.anonymous)
            assertEquals(FeedbackVisibility.ORGANIZER_ONLY, saved.details.visibility)
            assertTrue(backend.reviews("carol", session).isEmpty())
            assertEquals("Anonymous participant", backend.reviews("alice", session).single().authorName)
            find("feedback_privacy_saved").assertTextEquals("Anonymous review  |  Organizer only")
            capture("feedback-private-saved.png")
        } finally { cleanup() }
    }

    @Test fun tooManySelectionsCancelAndAccountChangeDoNotLeakDraftOrBlockSubmission() {
        try {
            launch(1.6f)
            compose.onNodeWithTag("history_detail").performScrollToNode(hasTestTag("feedback_rating_4"))
            compose.onNodeWithTag("feedback_rating_4").performClick()
            find("feedback_add_photo").performClick() // Empty result is cancellation.
            compose.reviewNode("feedback_submit").assertIsEnabled()
            selectedUris = List(4) { fixture() }
            compose.onNodeWithTag("feedback_add_photo").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("feedback_photo_error").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("feedback_photo_0").assertDoesNotExist()
            find("feedback_anonymous").performClick()
            find("feedback_visibility_ORGANIZER_ONLY").performClick()
            capture("feedback-privacy-large-text.png")
            compose.runOnIdle { actor = "carol"; message = "" }
            find("feedback_anonymous").assertIsOff()
            find("feedback_visibility_PUBLIC").assertIsSelected()
            find("feedback_add_photo").assertExists()
            compose.onNodeWithTag("feedback_photo_0").assertDoesNotExist()
            compose.reviewNode("feedback_submit").assertIsNotEnabled()
        } finally { cleanup() }
    }

    @Test fun organizerSeesAnonymousPrivateFeedbackAndParticipantsOnlySeePublicReviews() {
        try {
            listOf("alice", "bob", "carol").forEach { store.rememberSession(it, session) }
            backend.submitFeedback("bob", session.id, 3, "Please make the meeting point easier to find.",
                FeedbackDetails(anonymous = true, visibility = FeedbackVisibility.ORGANIZER_ONLY))
            backend.submitFeedback("carol", session.id, 5, "Everyone was really welcoming.")
            actor = "alice"
            compose.setContent { GogetherTheme {
                ActivityReviewsScreen(actor, session, backend.reviews(actor, session), false, "", {}, {})
            } }
            compose.onNodeWithText("Anonymous participant").assertIsDisplayed()
            compose.onNodeWithText("Please make the meeting point easier to find.").assertExists()
            compose.onNodeWithText("Bob").assertDoesNotExist()
            compose.onNodeWithText("Organizer only").assertExists()
            capture("feedback-organizer-reviews.png")
            compose.runOnIdle { actor = "carol" }
            compose.onNodeWithText("Anonymous participant").assertDoesNotExist()
            compose.onNodeWithText("Please make the meeting point easier to find.").assertDoesNotExist()
            compose.onNodeWithText("Carol (you)").assertIsDisplayed()
            compose.onNodeWithText("Everyone was really welcoming.").assertIsDisplayed()
        } finally { cleanup() }
    }

    @Test fun actualSystemPhotoPickerOpensAndCanBeCancelled() {
        try {
            fixture()
            launch(systemPicker = true)
            compose.onNodeWithTag("feedback_rating_4").performClick()
            find("feedback_add_photo").performClick()
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val deadline = android.os.SystemClock.uptimeMillis() + 8_000
            while (android.os.SystemClock.uptimeMillis() < deadline &&
                automation.rootInActiveWindow?.packageName?.toString().let { it == null || it == context.packageName }) {
                android.os.SystemClock.sleep(100)
            }
            val pickerPackage = automation.rootInActiveWindow?.packageName?.toString()
            assertNotNull(pickerPackage)
            assertNotEquals(context.packageName, pickerPackage)
            automation.takeScreenshot()?.let { bitmap ->
                File(context.getExternalFilesDir(null), "feedback-system-picker.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            assertTrue(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            compose.waitUntil(8_000) { compose.onAllNodesWithTag("feedback_submit").fetchSemanticsNodes().isNotEmpty() }
            compose.reviewNode("feedback_submit").assertIsEnabled()
            compose.onNodeWithTag("feedback_photo_0").assertDoesNotExist()
        } finally { cleanup() }
    }

    private fun capture(name: String) {
        if (compose.onAllNodesWithTag("feedback_visibility_done").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("feedback_visibility_done").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun cleanup() {
        createdUris.forEach { context.contentResolver.delete(it, null, null) }
        prefs.edit().also { edit ->
            listOf("alice", "bob", "carol").forEach { edit.remove(FeedbackDraftStore.key(it, session.id)) }
            prefs.all.keys.filter { it.contains(session.id) }.forEach(edit::remove)
        }.commit()
        val directory = File(context.filesDir, "feedback_photos")
        directory.listFiles()?.filter { it.extension == "json" }?.forEach { meta ->
            val data = org.json.JSONObject(meta.readText())
            if (data.optString("session") == session.id) FeedbackPhotoStore(context).removeDraft(meta.nameWithoutExtension, data.getString("owner"), session.id)
        }
    }
}

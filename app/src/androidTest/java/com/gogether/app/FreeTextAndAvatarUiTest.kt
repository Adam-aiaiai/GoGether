package com.gogether.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.cloud.*
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.feature.profile.importProfilePhoto
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real UI and the same durable image import used by Edit profile, with isolated test data. */
@OptIn(ExperimentalTestApi::class)
class FreeTextAndAvatarUiTest {
    @get:Rule val compose = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val now = System.currentTimeMillis()
    private val context get() = inst.targetContext
    private fun capture(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val folder = File(context.getExternalFilesDir(null), "free-text-avatar-20261128").apply { mkdirs() }
        val bitmap = if (dialog) inst.uiAutomation.takeScreenshot() else compose.onRoot().captureToImage().asAndroidBitmap()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun launch(content: @Composable () -> Unit) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 860.dp)) then
                DeviceConfigurationOverride.FontScale(1f)) { GogetherTheme { content() } }
        }
    }
    private fun importAvatar(): String {
        val source = File(context.cacheDir, "avatar-source-${UUID.randomUUID()}.jpg")
        inst.context.assets.open("review-badminton.jpg").use { source.writeBytes(it.readBytes()) }
        val saved = runBlocking { importProfilePhoto(context, Uri.fromFile(source)) }
        source.delete()
        return saved
    }
    @Test fun reviewLimitsTypingAndPastingTo100AndShowsOptionalCounters() {
        var draft by mutableStateOf(FeedbackDraft(4, "Friendly group and clear directions. I'd join again.",
            FeedbackDetails(5, 4, 5, listOf("welcoming"), positiveComment = "Everyone made beginners feel welcome.")))
        launch {
            FeedbackEditor("limit-preview", ReviewEventSummary("Campus badminton meetup", "Alice", now),
                draft, false, "", "", { draft = it }, {}, {}, {}, {}, photos = {
                    FeedbackPhotoStrip(emptyList(), "bob", "limit-preview", false, {}, {}, compact = true)
                })
        }
        capture("01-review-input")
        compose.onNodeWithTag("feedback_comment").performTextReplacement("\u597D".repeat(101))
        assertEquals(100, draft.comment.length)
        compose.onNodeWithTag("feedback_comment").performTextInput("more")
        assertEquals(100, draft.comment.length)
        compose.onNodeWithText("100/100").assertExists()
        Espresso.closeSoftKeyboard()
        capture("02-review-limit")
        compose.onNodeWithTag("feedback_open_details").performScrollTo().performClick()
        compose.onNodeWithTag("feedback_positive_comment").performScrollTo().performTextReplacement("x".repeat(101))
        assertEquals(100, draft.details.positiveComment.length)
        compose.onNodeWithTag("feedback_positive_comment").performTextReplacement("Everyone made beginners feel welcome.")
        compose.onNodeWithTag("feedback_improvement_comment").performScrollTo().performTextReplacement("x".repeat(101))
        assertEquals(100, draft.details.improvementComment.length)
        compose.onNodeWithTag("feedback_improvement_comment").performTextReplacement("Allow more time for warming up.")
        Espresso.closeSoftKeyboard()
        capture("03-optional-inputs")
    }
    @Test fun signatureUses100LimitAndSavesAlongsideImportedAvatar() {
        val repo = FakeGogetherRepository(DemoData.create(now).copy(currentUserId = "bob"), { now })
        runBlocking { repo.updateProfile(ProfileUpdate("Bob", setOf(Category.SPORTS), avatarUri = importAvatar(),
            signature = "See you outdoors!")).getOrThrow() }
        launch { GogetherApp(repo) }
        compose.onNodeWithTag("nav_profile").performClick()
        compose.onNodeWithTag("edit_profile").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Bob's avatar").fetchSemanticsNodes().isNotEmpty() }
        capture("04-edit-profile")
        compose.onNodeWithTag("profile_signature").performTextReplacement("a".repeat(101))
        compose.onNodeWithText("100/100").assertExists()
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("save_profile").performClick()
        compose.waitUntil(5000) { repo.state.value.currentUser?.signature?.length == 100 }
        assertTrue(repo.state.value.currentUser!!.avatarUri.isNotBlank())
    }
    @Test fun reviewUsesImportedAvatarAndAnonymousReviewNeverLoadsIt() {
        val avatar = importAvatar()
        val session = PrototypeSession(UUID.randomUUID().toString(), now - 7200000, now)
        launch { ActivityReviewsScreen("alice", session, listOf(
            VisibleFeedback("Bob", false, false, 4, "Friendly group and clear directions.", now, FeedbackDetails(), avatar),
            VisibleFeedback("Private name", true, false, 5, "A welcoming group for beginners.", now - 60000,
                FeedbackDetails(anonymous = true), avatar)), false, "", {}, {}) }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Bob's avatar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Private name's avatar").assertDoesNotExist()
        compose.onNodeWithText("Private name").assertDoesNotExist()
        compose.onNodeWithText("Anonymous participant").assertIsDisplayed()
        capture("05-review-avatar")
        compose.onNodeWithContentDescription("Bob's avatar").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithContentDescription("Event photo preview").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Close photo").assertIsDisplayed()
        capture("06-avatar-preview", dialog = true)
    }
    @Test fun attendanceUsesImportedAvatarAndRetainsInitialsForOthers() {
        val avatar = importAvatar()
        val event = DemoData.create(now).events.first()
        launch { CloudOrganizerAttendance(event, listOf(
            CloudAttendancePerson("bob", "Bob", now - 120000, avatar),
            CloudAttendancePerson("carol", "Carol", now - 60000),
            CloudAttendancePerson("david", "David", 0)), true, false, "", now, {}, {}, {}) }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Bob's avatar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Carol").assertIsDisplayed()
        capture("07-attendance-avatar")
    }
}

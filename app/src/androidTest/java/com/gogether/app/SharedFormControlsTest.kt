package com.gogether.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.event.CreateEventFormState
import com.gogether.app.feature.event.CreateEventScreen
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

@OptIn(ExperimentalTestApi::class)
class SharedFormControlsTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(500)
        val folder = File(context.getExternalFilesDir(null), "shared-fields").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun eventCoverCanBeChosenPreviewedReplacedCancelledAndRemovedWithoutLosingText() {
        val file = File(context.cacheDir, "cover-controls-${UUID.randomUUID()}.jpg")
        instrumentation.context.assets.open("review-badminton.jpg").use { file.writeBytes(it.readBytes()) }
        var selected: Uri? = Uri.fromFile(file)
        val registry = object : ActivityResultRegistry() {
            @Suppress("UNCHECKED_CAST")
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                assertEquals("image/*", input)
                dispatchResult(requestCode, selected as O)
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        var retained: CreateEventFormState? = null
        try {
            compose.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 860.dp))) {
                        GogetherTheme { CreateEventScreen(false, null, { retained = it }, { error("Do not publish this fixture") }) }
                    }
                }
            }
            compose.onNodeWithTag("create_title").performTextInput("Campus badminton meetup")
            closeSoftKeyboard()
            compose.onNodeWithTag("create_description").performScrollTo().performTextInput("A friendly game for all levels. Bring your racket if you have one.")
            closeSoftKeyboard()
            compose.onNodeWithTag("create_choose_image").performScrollTo().performClick()
            compose.waitUntil(5000) { retained?.imageUri?.isNotBlank() == true }
            compose.onNodeWithText("Create an event").performScrollTo()
            capture("02-event-cover-fields")
            compose.onNodeWithTag("create_cover_preview").performClick()
            compose.onNodeWithTag("event_cover_preview").assertIsDisplayed()
            compose.onNodeWithTag("photo_preview_close").performClick()
            selected = null
            compose.onNodeWithTag("create_choose_image").performClick()
            assertEquals(Uri.fromFile(file).toString(), retained!!.imageUri)
            compose.onNodeWithTag("create_remove_image").performClick()
            compose.waitUntil(5000) { retained?.imageUri == "" }
            compose.onNodeWithTag("create_choose_image").assertIsDisplayed()
            compose.onNodeWithText("Create an event").performScrollTo()
            capture("03-event-add-tile")
            assertEquals("Campus badminton meetup", retained!!.title)
            assertTrue(retained!!.description.startsWith("A friendly game"))
        } finally { file.delete() }
    }
}

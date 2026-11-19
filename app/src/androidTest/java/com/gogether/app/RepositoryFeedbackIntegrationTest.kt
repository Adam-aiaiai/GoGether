package com.gogether.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RepositoryFeedbackIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = PreferencesPrototypeStore(context)
    private val photos = FeedbackPhotoStore(context)
    private val time = System.currentTimeMillis()
    private val seed = DemoData.create(time).let { s -> s.copy(currentUserId = "bob", events = listOf(
        s.event("event-1")!!.copy(createdAt = System.nanoTime(), status = EventStatus.COMPLETED,
            startAt = time + 600_000, endAt = time + 7_800_000))) }
    private val repo = FakeGogetherRepository(seed, { time })
    private val bridge = RegistrationAttendanceBridge({ repo.state.value }, store, { time }, { 0 })
    private val id = bridge.key(seed.events.single())
    private val imported = mutableListOf<String>()

    private fun openFromHistory() {
        compose.onNodeWithTag("nav_profile").performClick()
        compose.onNodeWithTag("event_event-1").performScrollTo().performClick()
        compose.onNodeWithTag("open_checkin").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("feedback_submit").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun find(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("history_detail").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    @After fun cleanup() {
        val prefs = context.getSharedPreferences("checkin_prototype_v1", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        prefs.all.keys.filter { it.contains(id) }.forEach(edit::remove)
        edit.commit()
        imported.forEach { photos.removeDraft(it, "bob", id) }
    }
    @Test fun normalHistoryOpensCompleteUnboundFormRestoresPhotoDraftAndSubmitsOnce() = runBlocking {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        bitmap.recycle()
        val image = photos.importImage("bob", id, ByteArrayInputStream(bytes)).also(imported::add)
        FeedbackDraftStore(context).save("bob", id, FeedbackDraft(4, "A friendly group with plenty of time to chat.", FeedbackDetails(5, 3, 4,
            listOf("welcoming"), true, FeedbackVisibility.ORGANIZER_ONLY, listOf(image))))
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
        openFromHistory()
        find("feedback_organization_5").assertIsSelected()
        find("feedback_photo_0").assertExists()
        find("feedback_anonymous").assertIsOn()
        find("feedback_visibility_ORGANIZER_ONLY").assertIsSelected()
        find("feedback_comment").performTextReplacement("I came back to add a few more thoughts.")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("< Back").performClick()
        compose.onNodeWithTag("open_checkin").performScrollTo().performClick()
        find("feedback_comment").assertTextContains("I came back to add a few more thoughts.")
        find("feedback_photo_0").assertExists()
        compose.onNodeWithTag("feedback_submit").performClick()
        compose.waitUntil(5000) { store.feedback(id, "bob") != null }
        find("feedback_saved").assertExists()
        compose.onNodeWithTag("feedback_submit").assertDoesNotExist()
        val restored = PreferencesPrototypeStore(context)
        val saved = restored.feedback(id, "bob")!!
        assertEquals(listOf(image), saved.details.photos)
        assertTrue(saved.details.anonymous)
        assertEquals(FeedbackVisibility.ORGANIZER_ONLY, saved.details.visibility)
        assertNull(restored.activityInfo(id)!!.session.meeting)
        assertNotNull(photos.bitmap(image, "bob", id))
        assertFalse(photos.canRead(image, "carol", id))
        assertTrue(photos.canRead(image, "alice", id))
        assertEquals(FeedbackDraft(), FeedbackDraftStore(context).load("bob", id))
        assertTrue(repo.state.value.checkIns.isEmpty())
    }

    @Test fun switchingAccountHidesPreviousDraftAndWaitlistedUserCannotSubmit(): Unit = runBlocking {
        FeedbackDraftStore(context).save("bob", id, FeedbackDraft(4, "I enjoyed meeting everyone. I will finish this review later."))
        compose.setContent { GogetherTheme { RepositoryCheckInScreen(repo, "event-1", time, {}) } }
        find("feedback_comment").assertTextContains("I enjoyed meeting everyone. I will finish this review later.")
        repo.login("emma").getOrThrow()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Only confirmed participants can review this event.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("feedback_submit").assertDoesNotExist()
        compose.onAllNodesWithText("I enjoyed meeting everyone. I will finish this review later.", substring = true).assertCountEquals(0)
        repo.login("carol").getOrThrow()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("feedback_submit").fetchSemanticsNodes().isNotEmpty() }
        find("feedback_comment").assertTextContains("")
        compose.onNodeWithTag("feedback_submit").assertIsNotEnabled()
        repo.login("bob").getOrThrow()
        find("feedback_comment").assertTextContains("I enjoyed meeting everyone. I will finish this review later.")
    }

    @Test fun localRecordStorageReadsOldQrFormatAndRestoresUnboundRecords() {
        val old = PrototypeSession(id, MeetingPoint(31.2, 120.6), time, time + 7_200_000)
        val raw = PrototypeCodeCodec.encode(PrototypeCode(old, time, time + 30_000))
        val prefs = context.getSharedPreferences("checkin_prototype_v1", Context.MODE_PRIVATE)
        prefs.edit().putString("session.$id", raw).putBoolean("member.$id.bob", true).commit()
        assertEquals(old, PreferencesPrototypeStore(context).sessions("bob").single { it.id == id })
        store.rememberSession("bob", old)
        assertEquals(old, PreferencesPrototypeStore(context).sessions("bob").single { it.id == id })
        val unbound = old.copy(id = java.util.UUID.randomUUID().toString(), meeting = null)
        assertEquals(unbound, PrototypeSessionStorage.decode(PrototypeSessionStorage.encode(unbound)))
        assertThrows(IllegalArgumentException::class.java) { PrototypeCodeCodec.decode(PrototypeSessionStorage.encode(unbound)) }
    }
}

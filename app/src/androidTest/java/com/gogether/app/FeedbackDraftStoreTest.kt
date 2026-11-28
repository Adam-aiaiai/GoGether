package com.gogether.app

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class FeedbackDraftStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("checkin_prototype_v1", 0)
    private val ids = List(2) { UUID.randomUUID().toString() }
    private val session = PrototypeSession(ids[0], 1000, 2000)
    private val actors = listOf("bob", "carol", "bob.with/separators")
    private val photos = FeedbackPhotoStore(context)
    private val drafts = FeedbackDraftStore(context)
    private val store = PreferencesPrototypeStore(context)
    private val directory = File(context.filesDir, "feedback_photos")
    private val imported = mutableListOf<Triple<String, String, String>>()
    private suspend fun image(actor: String = "bob", activity: String = ids[0]): String {
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        bitmap.recycle()
        return photos.importImage(actor, activity, ByteArrayInputStream(bytes)).also { imported += Triple(it, actor, activity) }
    }
    private fun age(id: String) { assertTrue(File(directory, "$id.json").setLastModified(System.currentTimeMillis() - 2 * FeedbackPhotoStore.ORPHAN_GRACE_MS)) }

    @After fun cleanup() {
        val edit = prefs.edit()
        ids.forEach { id ->
            actors.forEach { edit.remove(FeedbackDraftStore.key(it, id)) }
            prefs.all.keys.filter { it.contains(id) }.forEach(edit::remove)
        }
        edit.commit()
        imported.forEach { (id, actor, activity) -> photos.removeDraft(id, actor, activity) }
    }

    @Test fun completeDraftRestoresAcrossStoreInstancesAndScopes(): Unit = runBlocking {
        val id = image()
        val original = FeedbackDraft(4, "I will finish this review when I have more time.\n" + "a".repeat(501), FeedbackDetails(5, 3, 4,
            listOf("welcoming", "more_guidance"), true, FeedbackVisibility.ORGANIZER_ONLY, listOf(id), "Friendly teammates.", "More warm-up time."))
        drafts.save("bob", ids[0], original)
        drafts.save("carol", ids[0], FeedbackDraft(2, "The venue was comfortable and easy to find."))
        drafts.save("bob", ids[1], FeedbackDraft(3, "The other event had a great atmosphere."))
        drafts.save(actors[2], ids[0], FeedbackDraft(1, "I enjoyed taking part with this account."))
        assertEquals(original, FeedbackDraftStore(context).load("bob", ids[0]))
        assertEquals(2, drafts.load("carol", ids[0]).rating)
        assertEquals("The other event had a great atmosphere.", drafts.load("bob", ids[1]).comment)
        assertEquals("I enjoyed taking part with this account.", drafts.load(actors[2], ids[0]).comment)
        assertNotNull(FeedbackPhotoStore(context).bitmap(id, "bob", ids[0]))
        assertNull(photos.bitmap(id, "carol", ids[0]))
    }

    @Test fun failedSubmissionRetainsDraftSuccessfulSubmissionClearsItAndLateSaveCannotRestoreIt(): Unit = runBlocking {
        val keep = image(); val remove = image()
        store.rememberAttendedSession("bob", session)
        val draft = FeedbackDraft(4, "I will add a few more thoughts later.", FeedbackDetails(photos = listOf(keep, remove),
            positiveComment = "Clear instructions.", improvementComment = "More time to practise."))
        drafts.save("bob", session.id, draft)
        val backend = LocalPrototypeBackend(store, { session.endAt })
        assertThrows(IllegalArgumentException::class.java) { backend.submitFeedback("bob", session.id, 0, "") }
        assertEquals(draft, drafts.load("bob", session.id))
        backend.submitFeedback("bob", session.id, 4, draft.comment, draft.details.copy(photos = listOf(keep)))
        assertEquals(draft.details.copy(photos = listOf(keep)), PreferencesPrototypeStore(context).feedback(session.id, "bob")!!.details)
        assertFalse(prefs.contains(FeedbackDraftStore.key("bob", session.id)))
        drafts.save("bob", session.id, draft) // A stale editor is not allowed to recreate the draft.
        assertEquals(FeedbackDraft(), drafts.load("bob", session.id))
        assertFalse(prefs.contains(FeedbackDraftStore.key("bob", session.id)))
        assertTrue(photos.isOwned(keep, "bob", session.id))
        assertFalse(photos.isOwned(remove, "bob", session.id))
        photos.removeDraft(keep, "bob", session.id)
        assertNotNull(photos.bitmap(keep, "bob", session.id))
    }

    @Test fun cleanupReclaimsOnlyOldUnreferencedFilesInTheExactScope(): Unit = runBlocking {
        val draftPhoto = image(); val orphan = image(); val fresh = image()
        val otherUser = image("carol"); val otherActivity = image(activity = ids[1])
        val interrupted = image()
        File(directory, "$interrupted.jpg").delete()
        File(directory, "$interrupted.tmp").writeText("interrupted copy")
        drafts.save("bob", ids[0], FeedbackDraft(details = FeedbackDetails(photos = listOf(draftPhoto))))
        listOf(draftPhoto, orphan, otherUser, otherActivity, interrupted).forEach(::age)
        photos.cleanUnreferenced("bob", ids[0])
        assertTrue(photos.isOwned(draftPhoto, "bob", ids[0]))
        assertTrue(photos.isOwned(fresh, "bob", ids[0]))
        assertTrue(photos.isOwned(otherUser, "carol", ids[0]))
        assertTrue(photos.isOwned(otherActivity, "bob", ids[1]))
        assertFalse(photos.isOwned(orphan, "bob", ids[0]))
        assertFalse(File(directory, "$interrupted.tmp").exists())
        assertFalse(File(directory, "$interrupted.json").exists())
        photos.cleanUnreferenced("bob", ids[0]) // Idempotent, no broad deletion.
        assertTrue(photos.isOwned(draftPhoto, "bob", ids[0]))
    }

    @Test fun removingOnePhotoMustFirstRemoveItsDurableDraftReference(): Unit = runBlocking {
        val first = image(); val second = image()
        val initial = FeedbackDraft(details = FeedbackDetails(photos = listOf(first, second)))
        drafts.save("bob", ids[0], initial)
        photos.removeDraft(first, "bob", ids[0])
        assertTrue(photos.isOwned(first, "bob", ids[0]))
        drafts.save("bob", ids[0], initial.copy(details = initial.details.copy(photos = listOf(second))))
        photos.removeDraft(first, "bob", ids[0])
        assertFalse(photos.isOwned(first, "bob", ids[0]))
        assertEquals(listOf(second), FeedbackDraftStore(context).load("bob", ids[0]).details.photos)
        assertTrue(photos.isOwned(second, "bob", ids[0]))
    }

    @Test fun cleanupKeepsSubmittedPhotosAndRejectedForeignAttachmentDoesNotOverwriteDraft(): Unit = runBlocking {
        val own = image(); val foreign = image("carol")
        drafts.save("bob", ids[0], FeedbackDraft(3, "Please keep this draft for later."))
        assertThrows(IllegalArgumentException::class.java) {
            drafts.save("bob", ids[0], FeedbackDraft(details = FeedbackDetails(photos = listOf(foreign))))
        }
        assertEquals("Please keep this draft for later.", drafts.load("bob", ids[0]).comment)
        store.saveFeedback(PrototypeFeedback(ids[0], "bob", 3, "Thanks for a great event.", 2000, FeedbackDetails(photos = listOf(own))))
        age(own)
        photos.cleanUnreferenced("bob", ids[0])
        assertNotNull(photos.bitmap(own, "bob", ids[0]))
    }
}

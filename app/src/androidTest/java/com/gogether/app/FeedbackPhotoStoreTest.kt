package com.gogether.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

class FeedbackPhotoStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val session = PrototypeSession(UUID.randomUUID().toString(), MeetingPoint(31.27, 120.74), 1_000, 2_000)
    private val prefs get() = context.getSharedPreferences("checkin_prototype_v1", 0)
    private val photos get() = FeedbackPhotoStore(context)
    private val imported = mutableListOf<Pair<String, String>>()
    private fun imageBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        return ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle(); output.toByteArray() }
    }
    private suspend fun add(actor: String = "bob") = photos.importImage(actor, session.id, ByteArrayInputStream(imageBytes()))
        .also { imported += actor to it }
    private fun cleanup() {
        prefs.edit().also { edit -> prefs.all.keys.filter { it.contains(session.id) }.forEach(edit::remove) }.commit()
        imported.forEach { (actor, id) -> photos.removeDraft(id, actor, session.id) }
    }

    @Test fun privatePhotosPersistAndRespectViewerAndActivityScope(): Unit = runBlocking {
        try {
            val id = add()
            assertTrue(photos.canRead(id, "bob", session.id))
            assertFalse(photos.canRead(id, "alice", session.id)) // Drafts are not shared.
            val store = PreferencesPrototypeStore(context)
            listOf("alice", "bob", "carol").forEach { store.rememberSession(it, session) }
            val service = LocalPrototypeBackend(store, { session.endAt }, { 0 })
            val details = FeedbackDetails(anonymous = true, visibility = FeedbackVisibility.ORGANIZER_ONLY, photos = listOf(id))
            service.submitFeedback("bob", session.id, 4, "Sharing a few photos with the organizer.", details)
            assertEquals(details, PreferencesPrototypeStore(context).feedback(session.id, "bob")!!.details)
            assertTrue(FeedbackPhotoStore(context).canRead(id, "alice", session.id))
            assertFalse(photos.canRead(id, "carol", session.id))
            assertFalse(photos.canRead(id, "alice", "another-activity"))
            assertFalse(photos.canRead(id, "", session.id))
            assertNotNull(FeedbackPhotoStore(context).bitmap(id, "alice", session.id))
            assertNull(photos.bitmap(id, "carol", session.id))
            photos.removeDraft(id, "bob", session.id) // A submitted image must not be removed by draft cleanup.
            assertNotNull(photos.bitmap(id, "bob", session.id))
            assertTrue(service.reviews("carol", session).isEmpty())
            assertEquals("Anonymous participant", service.reviews("alice", session).single().authorName)
        } finally { cleanup() }
    }

    @Test fun publicImagesAreReadableAndCannotBeAttachedByAnotherUserOrActivity(): Unit = runBlocking {
        try {
            val id = add()
            val store = PreferencesPrototypeStore(context)
            listOf("bob", "carol").forEach { store.rememberSession(it, session) }
            val service = LocalPrototypeBackend(store, { session.endAt }, { 0 })
            assertThrows(IllegalArgumentException::class.java) {
                service.submitFeedback("carol", session.id, 5, "", FeedbackDetails(photos = listOf(id)))
            }
            assertFalse(photos.isOwned(id, "bob", "another-activity"))
            service.submitFeedback("bob", session.id, 4, "", FeedbackDetails(photos = listOf(id)))
            assertTrue(photos.canRead(id, "carol", session.id))
            assertNotNull(photos.bitmap(id, "carol", session.id))
            assertThrows(IllegalArgumentException::class.java) { service.submitFeedback("bob", session.id, 5, "") }
        } finally { cleanup() }
    }

    @Test fun invalidEmptyAndOversizedImagesFailAndRemovedDraftIsUnreadable(): Unit = runBlocking {
        try {
            for (bytes in listOf(byteArrayOf(), "not an image".toByteArray())) {
                assertTrue(runCatching { photos.importImage("bob", session.id, ByteArrayInputStream(bytes)) }.isFailure)
            }
            val huge = object : InputStream() {
                private var remaining = FeedbackPhotoStore.MAX_BYTES + 1
                override fun read(): Int = if (remaining-- > 0) 0 else -1
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (remaining <= 0) return -1
                    val count = minOf(length.toLong(), remaining).toInt()
                    buffer.fill(0, offset, offset + count); remaining -= count
                    return count
                }
            }
            val failure = runCatching { photos.importImage("bob", session.id, huge) }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("10 MB"))
            val id = add()
            photos.removeDraft(id, "carol", session.id)
            assertTrue(photos.isOwned(id, "bob", session.id))
            photos.removeDraft(id, "bob", session.id)
            assertNull(photos.bitmap(id, "bob", session.id))
            assertFalse(photos.isOwned("../../bad", "bob", session.id))
        } finally { cleanup() }
    }

    @Test fun realContentUriImportCopiesSelectedImageAndEnforcesRemainingSlots(): Unit = runBlocking {
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "gogether-test-${session.id}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        })!!
        var sourceDeleted = false
        try {
            resolver.openOutputStream(uri)!!.use { it.write(imageBytes()) }
            assertTrue(runCatching { photos.importUris("bob", session.id, listOf(uri, uri), 1) }.isFailure)
            val ids = photos.importUris("bob", session.id, listOf(uri), 3)
            imported += ids.map { "bob" to it }
            resolver.delete(uri, null, null)
            sourceDeleted = true
            assertNotNull(photos.bitmap(ids.single(), "bob", session.id)) // Independent of the original URI.
        } finally {
            try { if (!sourceDeleted) resolver.delete(uri, null, null) }
            finally { cleanup() }
        }
    }
}

package com.gogether.app

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Run write, force-stop the target app, then read in a fresh instrumentation process. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FeedbackDraftRestartTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val actor = "draft-restart-test"
    private val session = "3eac42b8-d83c-4bd0-9859-9c7d8ad49178"
    private val drafts = FeedbackDraftStore(context)
    private val photos = FeedbackPhotoStore(context)

    @Test fun aWriteBeforeProcessExit(): Unit = runBlocking {
        val old = drafts.load(actor, session).details.photos
        drafts.save(actor, session, FeedbackDraft())
        old.forEach { photos.removeDraft(it, actor, session) }
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        bitmap.recycle()
        val id = photos.importImage(actor, session, ByteArrayInputStream(bytes))
        drafts.save(actor, session, FeedbackDraft(4, "退出应用后继续填写", FeedbackDetails(5, 3, 4,
            listOf("welcoming"), true, FeedbackVisibility.ORGANIZER_ONLY, listOf(id))))
        assertEquals(4, drafts.load(actor, session).rating)
    }

    @Test fun bReadAfterProcessRestart(): Unit = runBlocking {
        val restored = drafts.load(actor, session)
        try {
            assertEquals(4, restored.rating)
            assertEquals("退出应用后继续填写", restored.comment)
            assertEquals(5, restored.details.organization)
            assertEquals(3, restored.details.venue)
            assertEquals(4, restored.details.atmosphere)
            assertEquals(listOf("welcoming"), restored.details.tags)
            assertTrue(restored.details.anonymous)
            assertEquals(FeedbackVisibility.ORGANIZER_ONLY, restored.details.visibility)
            assertNotNull(photos.bitmap(restored.details.photos.single(), actor, session))
            assertEquals(FeedbackDraft(), drafts.load("another-user", session))
        } finally {
            drafts.save(actor, session, FeedbackDraft())
            restored.details.photos.forEach { photos.removeDraft(it, actor, session) }
        }
    }
}

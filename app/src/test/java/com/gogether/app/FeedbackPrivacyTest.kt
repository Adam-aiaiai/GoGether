package com.gogether.app

import com.gogether.app.feature.checkin.prototype.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class FeedbackPrivacyTest {
    @Test fun anonymousReviewsNeverExposeUploadedAvatarEvenToTheirAuthor() {
        val review = PrototypeFeedback("activity", "bob", 4, "Good", 1L, FeedbackDetails(anonymous = true))
        listOf("alice", "bob", "carol").forEach { viewer ->
            assertEquals("", review.visibleTo(viewer, "alice", "Bob", "profiles/bob/private-image")!!.avatarUri)
        }
        assertEquals("profiles/bob/photo", review.copy(details = FeedbackDetails())
            .visibleTo("carol", "alice", "Bob", "profiles/bob/photo")!!.avatarUri)
    }
    private val session = PrototypeSession(UUID.randomUUID().toString(), 1_000, 2_000)
    private val store = MemoryPrototypeStore().apply { listOf("alice", "bob", "carol").forEach { rememberSession(it, session); save(PrototypeReceipt(session.id, it, 1_500)) } }
    private fun backend() = LocalPrototypeBackend(store, { 2_000 })

    @Test fun anonymousPublicReviewRedactsIdentityForBothOrganizerAndParticipantsButKeepsInternalAuthor() {
        val saved = backend().submitFeedback("bob", session.id, 4, "I had a great time", FeedbackDetails(anonymous = true))
        assertEquals("bob", saved.userId)
        listOf("alice", "carol", "bob").forEach { viewer ->
            val visible = backend().reviews(viewer, session).single()
            assertEquals("Anonymous participant", visible.authorName)
            assertTrue(visible.anonymous)
            assertEquals(viewer == "bob", visible.own)
        }
        assertThrows(IllegalArgumentException::class.java) { backend().submitFeedback("bob", session.id, 5, "") }
    }

    @Test fun organizerOnlyReviewIsHiddenFromOthersIncludingPhotoReferences() {
        val photo = UUID.randomUUID().toString()
        backend().submitFeedback("bob", session.id, 3, "Please allow more time for introductions.",
            FeedbackDetails(anonymous = true, visibility = FeedbackVisibility.ORGANIZER_ONLY, photos = listOf(photo)))
        assertTrue(backend().reviews("carol", session).isEmpty())
        assertEquals(listOf(photo), backend().reviews("alice", session).single().details.photos)
        assertEquals("Anonymous participant", backend().reviews("alice", session).single().authorName)
        assertEquals(1, backend().reviews("bob", session).size)
        assertNull(store.feedback(session.id, "bob")!!.visibleTo("", "alice"))
        assertThrows(IllegalArgumentException::class.java) { backend().reviews("unknown", session) }
        assertThrows(IllegalArgumentException::class.java) { backend().reviews("bob", session.copy(id = UUID.randomUUID().toString())) }
    }

    @Test fun defaultsAreNamedPublicAndPhotoReferencesAreBoundedAndUnique() {
        val ids = (1..4).map { UUID.randomUUID().toString() }
        listOf(ids, listOf(ids[0], ids[0]), listOf("../../outside")).forEach { photos ->
            assertThrows(IllegalArgumentException::class.java) { backend().submitFeedback("bob", session.id, 4, "", FeedbackDetails(photos = photos)) }
            assertNull(store.feedback(session.id, "bob"))
        }
        backend().submitFeedback("bob", session.id, 4, "", FeedbackDetails(photos = ids.take(3)))
        val visible = backend().reviews("carol", session).single()
        assertEquals("Bob", visible.authorName)
        assertFalse(visible.anonymous)
        assertEquals(FeedbackVisibility.PUBLIC, visible.details.visibility)
    }
}

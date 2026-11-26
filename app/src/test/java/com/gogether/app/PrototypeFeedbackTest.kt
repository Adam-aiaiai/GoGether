package com.gogether.app

import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PrototypeFeedbackTest {
    @Test fun optionalDetailsAreValidatedAndPreservedWithoutChangingOverallScore() {
        val session = session()
        backend().rememberCode("bob", raw(session))
        time = session.endAt
        val details = FeedbackDetails(5, 3, 4, listOf("welcoming", "more_guidance"),
            positiveComment = "Friendly teammates.", improvementComment = "More warm-up time.")
        for (bad in listOf(details.copy(positiveComment = "x".repeat(501)), details.copy(improvementComment = "x".repeat(501)), details.copy(venue = 6), details.copy(organization = -1),
            details.copy(tags = listOf("unknown")), details.copy(tags = listOf("welcoming", "welcoming")))) {
            assertThrows(IllegalArgumentException::class.java) { backend().submitFeedback("bob", session.id, 4, "", bad) }
            assertNull(backend().history("bob").single().feedback)
        }
        backend().submitFeedback("bob", session.id, 4, "", details)
        val saved = backend().history("bob").single().feedback!!
        assertEquals(details, saved.details)
        assertEquals(4, saved.rating)
        assertThrows(IllegalArgumentException::class.java) { backend().submitFeedback("bob", session.id, 4, "", details) }
    }

    private var time = 1_800_000_000_000L
    private val fix = PositionFix(MeetingPoint(31.27, 120.74), 10.0, 0)
    private val store = MemoryPrototypeStore()
    private fun backend() = LocalPrototypeBackend(store, { time }, { 0 })
    private val host = LocalPrototypeBackend(MemoryPrototypeStore(), { time }, { 0 })
    private fun session() = host.createMeeting("alice", fix)
    private fun raw(session: PrototypeSession) = PrototypeCodeCodec.encode(host.issue("alice", session, fix))

    @Test fun scanThenEndThenReviewSurvivesRecreatingBackend() {
        val session = session()
        val service = backend()
        service.submit("bob", raw(session), fix)
        time = session.endAt
        val result = service.submitFeedback("bob", session.id, 4, "  The meeting instructions were clear.  ")
        assertEquals("The meeting instructions were clear.", result.comment)
        val restored = backend().history("bob").single()
        assertNotNull(restored.receipt)
        assertEquals(result, restored.feedback)
        assertEquals(session, restored.session)
        assertThrows(IllegalArgumentException::class.java) { backend().submitFeedback("bob", session.id, 5, "I enjoyed the event and would join again.") }
    }

    @Test fun reviewsOpenExactlyAtEndAndPreserveExistingNoCheckInRequirement() {
        val session = session()
        val service = backend()
        service.rememberCode("bob", raw(session))
        time = session.endAt - 1
        assertThrows(IllegalArgumentException::class.java) { service.submitFeedback("bob", session.id, 3, "") }
        time++
        service.submitFeedback("bob", session.id, 3, "")
        assertNull(service.history("bob").single().receipt)
        assertEquals("", service.history("bob").single().feedback!!.comment)
    }

    @Test fun cancelledUnknownAndOtherUsersEventsCannotBeReviewed() {
        val session = session()
        backend().rememberCode("bob", raw(session))
        time = session.endAt
        assertTrue(backend().history("carol").isEmpty())
        assertThrows(IllegalArgumentException::class.java) { backend().submitFeedback("carol", session.id, 5, "") }
        assertThrows(IllegalArgumentException::class.java) { backend().submitFeedback("emma", session.id, 5, "") }
        assertThrows(IllegalArgumentException::class.java) { backend().submitFeedback("finn", session.id, 5, "") }
        assertThrows(IllegalArgumentException::class.java) { backend().submitFeedback("bob", "missing", 5, "") }
        store.cancel(session.id)
        assertThrows(IllegalArgumentException::class.java) { backend().submitFeedback("bob", session.id, 5, "") }
        assertTrue(backend().history("bob").single().cancelled)
    }

    @Test fun ratingAndCommentValidationAllowsRetryWithoutWritingBadData() {
        val session = session()
        backend().rememberCode("bob", raw(session))
        time = session.endAt
        for (rating in listOf(0, 6)) assertThrows(IllegalArgumentException::class.java) {
            backend().submitFeedback("bob", session.id, rating, "")
        }
        assertThrows(IllegalArgumentException::class.java) { backend().submitFeedback("bob", session.id, 5, "a".repeat(501)) }
        assertNull(backend().history("bob").single().feedback)
        backend().submitFeedback("bob", session.id, 1, "a".repeat(500))
        assertEquals(500, backend().history("bob").single().feedback!!.comment.length)
    }

    @Test fun conflictingMetadataCannotRewriteAnExistingEventsEndTime() {
        val session = session()
        backend().rememberCode("bob", raw(session))
        val altered = session.copy(endAt = session.startAt + 1)
        val alteredCode = PrototypeCode(altered, time, time + 30_000)
        assertThrows(IllegalArgumentException::class.java) {
            backend().rememberCode("bob", PrototypeCodeCodec.encode(alteredCode))
        }
        assertEquals(session, backend().history("bob").single().session)
    }

    @Test fun separateBackendInstancesCannotWriteDuplicateCheckInsOrReviews() = runBlocking {
        val session = session()
        val code = raw(session)
        val checkIns = (1..8).map { async(Dispatchers.Default) { runCatching { backend().submit("bob", code, fix) }.isSuccess } }.awaitAll()
        assertEquals(1, checkIns.count { it })
        time = session.endAt
        val reviews = (1..8).map { async(Dispatchers.Default) { runCatching { backend().submitFeedback("bob", session.id, 4, "") }.isSuccess } }.awaitAll()
        assertEquals(1, reviews.count { it })
    }

    @Test fun malformedAndIneligibleCodesNeverCreateHistory() {
        assertThrows(IllegalArgumentException::class.java) { backend().rememberCode("bob", "https://example.com") }
        val session = session()
        assertThrows(IllegalArgumentException::class.java) { backend().rememberCode("emma", raw(session)) }
        assertThrows(IllegalArgumentException::class.java) { backend().rememberCode("finn", raw(session)) }
        assertTrue(backend().history("bob").isEmpty())
        assertTrue(backend().history("emma").isEmpty())
    }
}

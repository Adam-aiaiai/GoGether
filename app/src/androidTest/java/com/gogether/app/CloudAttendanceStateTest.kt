package com.gogether.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gogether.app.feature.checkin.cloud.*
import com.gogether.app.feature.checkin.prototype.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CloudAttendanceStateTest {
    @Test fun pendingAttemptSurvivesRecreationAndBlocksNewIdentityForSameEvent() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val actor="test-${UUID.randomUUID()}"; val event="event-${UUID.randomUUID()}"
        val attempt=CloudPendingCheckIn("attempt-id","GOGETHER-CLOUD-1|opaque",mapOf("ageMillis" to 1000,"latitude" to 31.2))
        val first=CloudCheckInPendingStore(context)
        try {
            first.save(actor,event,attempt)
            val reopened=CloudCheckInPendingStore(context)
            assertEquals(attempt,reopened.load(actor,event))
            assertNull(reopened.load("another-user",event))
            assertTrue(runCatching{reopened.save(actor,event,attempt.copy(id="new-id"))}.isFailure)
            assertEquals(attempt,reopened.load(actor,event))
            reopened.clear(actor,event)
            assertNull(first.load(actor,event))
        } finally { first.clear(actor,event) }
    }

    @Test fun cloudReviewDraftRestoresEveryFieldAndDoesNotShareBetweenAccounts() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val actor="draft-${UUID.randomUUID()}"; val event="event-${UUID.randomUUID()}"
        val draft=FeedbackDraft(5,"Private comments",FeedbackDetails(4,3,5,listOf("welcoming"),true,FeedbackVisibility.ORGANIZER_ONLY,listOf(UUID.randomUUID().toString())))
        val store=CloudFeedbackDraftStore(context)
        try {
            store.save(actor,event,draft)
            assertEquals(draft,CloudFeedbackDraftStore(context).load(actor,event))
            assertEquals(FeedbackDraft(),store.load("another-user",event))
            store.clear(actor,event)
            assertEquals(FeedbackDraft(),store.load(actor,event))
        } finally { store.clear(actor,event) }
    }
}

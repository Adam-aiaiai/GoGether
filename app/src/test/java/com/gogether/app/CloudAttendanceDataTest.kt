package com.gogether.app

import com.gogether.app.feature.checkin.cloud.*
import com.gogether.app.feature.checkin.prototype.*
import org.junit.Assert.*
import org.junit.Test

class CloudAttendanceDataTest {
    @Test fun positionUsesElapsedAgeAndRejectsMockAndStaleFixes() {
        val fix=PositionFix(MeetingPoint(31.2,121.5),12.0,1000)
        val payload=fix.cloudPayload(2000)
        assertEquals(1000L,payload["ageMillis"])
        assertEquals(false,payload["simulated"])
        assertEquals(31.2,payload["latitude"])
        assertTrue(runCatching{fix.copy(simulated=true).cloudPayload(2000)}.isFailure)
        assertTrue(runCatching{fix.cloudPayload(17000)}.isFailure)
        assertTrue(runCatching{fix.cloudPayload(999)}.isFailure)
        assertTrue(runCatching{fix.copy(accuracyMeters=51.0).cloudPayload(2000)}.isFailure)
    }

    @Test fun feedbackDetailsPreservePrivacyAndDimensionsWithoutInventingAuthor() {
        val source=mapOf("organization" to 4,"venue" to 3L,"atmosphere" to 5.0,"tags" to listOf("welcoming"),
            "anonymous" to true,"visibility" to "ORGANIZER_ONLY","photos" to listOf("opaque-media-id"))
        val detail=details(source)
        assertTrue(detail.anonymous)
        assertEquals(FeedbackVisibility.ORGANIZER_ONLY,detail.visibility)
        assertEquals(4,detail.organization);assertEquals(3,detail.venue);assertEquals(5,detail.atmosphere)
        assertEquals(listOf("opaque-media-id"),detail.photos)
        val payload=detail.payload(listOf("reviews/uid/upload-id"))
        assertEquals(listOf("reviews/uid/upload-id"),payload["photos"])
        assertFalse(payload.containsKey("userId"))
        assertFalse(payload.containsKey("authorId"))
    }
}

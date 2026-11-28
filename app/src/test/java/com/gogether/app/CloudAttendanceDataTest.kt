package com.gogether.app

import com.gogether.app.feature.checkin.cloud.*
import com.gogether.app.feature.checkin.prototype.*
import org.junit.Assert.*
import com.gogether.app.core.model.*
import org.junit.Test

class CloudAttendanceDataTest {
    @Test fun cachedCloudReceiptDisappearsImmediatelyWhenRegistrationChanges() {
        val registration = Registration("reg", "event", "bob", RegistrationStatus.CONFIRMED, 100, 100, 1)
        val receipt = mapOf("checkedInAt" to 100L, "registrationKey" to registration.attendanceKey)
        assertNotNull(currentCloudReceipt(receipt, registration, EventStatus.OPEN))
        assertNull(currentCloudReceipt(receipt, registration.copy(queueOrder = 2), EventStatus.OPEN))
        assertNull(currentCloudReceipt(receipt, registration.copy(status = RegistrationStatus.CANCELLED), EventStatus.OPEN))
        assertNull(currentCloudReceipt(receipt, registration, EventStatus.CANCELLED))
        assertNull(currentCloudReceipt(mapOf("checkedInAt" to 99L), registration, EventStatus.OPEN))
    }

    @Test fun pendingRequestCarriesRegistrationCycleInsteadOfCoordinates() {
        val pending = CloudPendingCheckIn("request-1", "GOGETHER-CLOUD-2|opaque", "registration:2")
        assertEquals("registration:2", pending.registrationKey)
        assertEquals("GOGETHER-CLOUD-2|opaque", pending.code)
    }

    @Test fun feedbackDetailsPreservePrivacyAndDimensionsWithoutInventingAuthor() {
        val source=mapOf("organization" to 4,"venue" to 3L,"atmosphere" to 5.0,"tags" to listOf("welcoming"),
            "anonymous" to true,"visibility" to "ORGANIZER_ONLY","photos" to listOf("opaque-media-id"),
            "positiveComment" to "Friendly teammates.", "improvementComment" to "More time to warm up.")
        val detail=details(source)
        assertTrue(detail.anonymous)
        assertEquals(FeedbackVisibility.ORGANIZER_ONLY,detail.visibility)
        assertEquals(4,detail.organization);assertEquals(3,detail.venue);assertEquals(5,detail.atmosphere)
        assertEquals(listOf("opaque-media-id"),detail.photos)
        val payload=detail.payload(listOf("reviews/uid/upload-id"))
        assertEquals(listOf("reviews/uid/upload-id"),payload["photos"])
        assertEquals("Friendly teammates.", detail.positiveComment)
        assertEquals("More time to warm up.", payload["improvementComment"])
        assertEquals("", details(emptyMap<String, Any>()).positiveComment)
        assertEquals("", details(null).improvementComment)
        assertFalse(payload.containsKey("userId"))
        assertFalse(payload.containsKey("authorId"))
    }
    @Test fun unknownExplicitPrivacyStaysPrivateWhileAbsentLegacyFieldsKeepTheirDefaults() {
        for (value in listOf(null, "", "PUBLIC")) assertEquals(FeedbackVisibility.PUBLIC, details(mapOf("visibility" to value)).visibility)
        for (value in listOf("ORGANIZER_ONLY", "FUTURE_PRIVATE", 42)) assertEquals(FeedbackVisibility.ORGANIZER_ONLY, details(mapOf("visibility" to value)).visibility)
    }

}

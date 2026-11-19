package com.gogether.app

import com.gogether.app.core.data.DemoData
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.CheckInEligibility
import com.gogether.app.feature.checkin.checkInEligibility
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckInEligibilityTest {
    private val initial = DemoData.create(1_800_000_000_000L).copy(currentUserId = "bob")
    private val event = initial.events.first()

    @Test fun explainsEveryRegistrationStatusSeparately() {
        val expected = mapOf(
            RegistrationStatus.CONFIRMED to CheckInEligibility.READY,
            RegistrationStatus.WAITLISTED to CheckInEligibility.WAITLISTED,
            RegistrationStatus.CANCELLED to CheckInEligibility.REGISTRATION_CANCELLED,
            RegistrationStatus.REJECTED to CheckInEligibility.REGISTRATION_REJECTED,
        )
        expected.forEach { (status, result) ->
            val state = initial.copy(registrations = initial.registrations.map {
                if (it.eventId == event.id && it.userId == "bob") it.copy(status = status) else it
            })
            assertEquals(result, checkInEligibility(event, state, event.startAt))
        }
        assertEquals(CheckInEligibility.NOT_REGISTERED,
            checkInEligibility(event, initial, event.startAt, "finn"))
    }

    @Test fun signedOutAndUnknownUsersNeedToSignIn() {
        listOf(null, "missing").forEach { user ->
            assertEquals(CheckInEligibility.SIGN_IN_REQUIRED,
                checkInEligibility(event, initial.copy(currentUserId = user), event.startAt))
        }
    }

    @Test fun timeChangeAloneUpdatesReadinessAndExpiry() {
        assertEquals(CheckInEligibility.NOT_STARTED,
            checkInEligibility(event, initial, event.startAt - 1_800_001))
        assertEquals(CheckInEligibility.READY,
            checkInEligibility(event, initial, event.startAt - 1_800_000))
        assertEquals(CheckInEligibility.READY,
            checkInEligibility(event, initial, event.startAt + 1_800_000))
        assertEquals(CheckInEligibility.EXPIRED,
            checkInEligibility(event, initial, event.startAt + 1_800_001))
    }

    @Test fun existingRecordStaysRecordedAfterTheWindowAndDoesNotBelongToAnotherUser() {
        val state = initial.copy(checkIns = listOf(CheckIn("check-1", event.id, "bob", event.startAt)))
        assertEquals(CheckInEligibility.ALREADY_CHECKED_IN,
            checkInEligibility(event, state, event.startAt + 1_800_001))
        assertEquals(CheckInEligibility.READY,
            checkInEligibility(event, state, event.startAt, "carol"))
    }

    @Test fun eventCancellationAndCompletionOverrideReadiness() {
        assertEquals(CheckInEligibility.EVENT_CANCELLED,
            checkInEligibility(event.copy(status = EventStatus.CANCELLED), initial, event.startAt))
        assertEquals(CheckInEligibility.EVENT_ENDED,
            checkInEligibility(event.copy(status = EventStatus.COMPLETED), initial, event.startAt))
    }
}

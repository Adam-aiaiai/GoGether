package com.gogether.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.gogether.app.core.data.DemoData
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.CheckInScreen
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CheckInScreenTest {
    @get:Rule val compose = createComposeRule()
    private val initial = DemoData.create(1_800_000_000_000L).copy(currentUserId = "bob")
    private val event = initial.events.first()
    private var state by mutableStateOf(initial)
    private var time by mutableLongStateOf(event.startAt)
    private var busy by mutableStateOf(false)
    private var checkInCalls = 0
    private var feedbackCalls = 0

    private fun launch() {
        compose.setContent {
            GogetherTheme {
                CheckInScreen(state.event(event.id)!!, state, time, busy,
                    checkIn = { checkInCalls++ }, feedback = { _, _ -> feedbackCalls++ })
            }
        }
    }

    private fun status(text: String) = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    private fun button() = compose.onNodeWithTag("demo_checkin").performScrollTo()

    @Test fun windowTransitionsUpdateStatusAndButtonWithoutReopeningScreen() {
        time = event.startAt - 1_800_001
        launch()
        status("Check-in has not started")
        button().assertIsNotEnabled()
        compose.runOnIdle { time = event.startAt - 1_800_000 }
        status("Ready to check in")
        button().assertIsEnabled()
        compose.runOnIdle { time = event.startAt + 1_800_000 }
        button().assertIsEnabled()
        compose.runOnIdle { time++ }
        status("Check-in window closed")
        button().assertIsNotEnabled()
        compose.onNodeWithText("Submit feedback").assertDoesNotExist()
        assertEquals(0, checkInCalls)
    }

    @Test fun registrationChangesAndAccountSwitchUpdateEligibility() {
        state = initial.copy(currentUserId = "emma")
        launch()
        status("Waitlisted")
        button().assertIsNotEnabled()
        compose.runOnIdle {
            state = state.copy(registrations = state.registrations.map {
                if (it.eventId == event.id && it.userId == "emma") it.copy(status = RegistrationStatus.CONFIRMED) else it
            })
        }
        status("Ready to check in")
        button().assertIsEnabled()
        compose.runOnIdle {
            state = state.copy(registrations = state.registrations.map {
                if (it.eventId == event.id && it.userId == "emma") it.copy(status = RegistrationStatus.CANCELLED) else it
            })
        }
        status("Registration cancelled")
        button().assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(currentUserId = "finn") }
        status("Confirmed place required")
        button().assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(currentUserId = null) }
        status("Sign in required")
        button().assertIsNotEnabled()
    }

    @Test fun successfulCheckInDisablesRepeatAndSurvivesWindowExpiry() {
        launch()
        button().performClick()
        compose.waitForIdle()
        assertEquals(1, checkInCalls)
        compose.runOnIdle {
            state = state.copy(checkIns = listOf(CheckIn("check-1", event.id, "bob", time)))
        }
        status("You're checked in · Demo")
        button().assertIsNotEnabled()
        compose.runOnIdle { time = event.startAt + 1_800_001 }
        status("You're checked in · Demo")
        button().assertIsNotEnabled()
        assertEquals(1, checkInCalls)
    }

    @Test fun cancellationImmediatelyRemovesCheckInAndFeedbackActions() {
        launch()
        button().assertIsEnabled()
        compose.runOnIdle {
            state = state.copy(events = state.events.map {
                if (it.id == event.id) it.copy(status = EventStatus.CANCELLED) else it
            })
        }
        status("Event cancelled")
        compose.onNodeWithTag("demo_checkin").assertDoesNotExist()
        compose.onNodeWithText("Submit feedback").assertDoesNotExist()
        assertEquals(0, checkInCalls)
    }

    @Test fun shortEventRequiresCheckInBeforeFeedbackAfterItsEnd() {
        state = state.copy(events = state.events.map {
            if (it.id == event.id) it.copy(endAt = event.startAt + 600_000) else it
        })
        launch()
        button().assertIsEnabled()
        compose.runOnIdle { time = event.startAt + 600_000 }
        status("Event ended")
        compose.onNodeWithTag("demo_checkin").assertDoesNotExist()
        compose.onNodeWithText("Submit feedback").assertDoesNotExist()
        status("Only checked-in participants can leave feedback.")
        compose.runOnIdle { state = state.copy(checkIns = listOf(CheckIn("valid", event.id, "bob", event.startAt))) }
        compose.onNodeWithText("Submit feedback").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("4").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Submit feedback").performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(1, feedbackCalls)
    }

    @Test fun completedStatusClosesCheckInBeforeScheduledEnd() {
        launch()
        compose.runOnIdle {
            state = state.copy(events = state.events.map {
                if (it.id == event.id) it.copy(status = EventStatus.COMPLETED) else it
            })
        }
        status("Event ended")
        compose.onNodeWithTag("demo_checkin").assertDoesNotExist()
    }

    @Test fun submissionInProgressDisablesButton() {
        busy = true
        launch()
        status("Ready to check in")
        button().assertIsNotEnabled()
        compose.runOnIdle { busy = false }
        button().assertIsEnabled()
    }

    @Test fun switchingAccountClearsUnsubmittedRatingAndComment() {
        time = event.endAt
        state = initial.copy(registrations = initial.registrations.filterNot { it.eventId == event.id && it.userId == "carol" } +
            initial.registrations.first { it.eventId == event.id && it.userId == "bob" }.copy(id = "carol-test", userId = "carol"))
        state = state.copy(checkIns = listOf("bob", "carol").map { CheckIn("checked-$it", event.id, it, event.startAt) })
        launch()
        compose.onNodeWithText("4").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("The group was welcoming. I will add more later.")
        compose.runOnIdle { state = state.copy(currentUserId = "carol") }
        compose.onNodeWithText("Submit feedback").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("The group was welcoming. I will add more later.").assertDoesNotExist()
        compose.onNodeWithText("4").performScrollTo().assertIsNotSelected()
    }
}

package com.gogether.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.gogether.app.core.data.DemoData
import com.gogether.app.core.model.*
import com.gogether.app.feature.event.EventDetailScreen
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RegistrationConflictUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = 1_800_000_000_000L
    private val state = DemoData.create(now).copy(currentUserId = "bob")
    private var joins = 0

    private fun show(overlap: Boolean = true, full: Boolean = false) {
        val booked = state.events.first()
        val target = state.events[1].copy(
            startAt = if (overlap) booked.startAt else booked.endAt,
            endAt = booked.endAt + 3_600_000,
            capacity = if (full) 1 else 6,
        )
        compose.setContent {
            GogetherTheme {
                EventDetailScreen(target, state.copy(events = state.events.map {
                    if (it.id == target.id) target else it
                }), now, false, { joins++ }, {}, {}, {})
            }
        }
        compose.onNodeWithTag("join_event").performScrollTo().performClick()
    }

    @Test fun warningCanBeDismissedAndRequiresExplicitConfirmation() {
        show()
        compose.onNodeWithText("Schedule conflict").assertIsDisplayed()
        compose.onNodeWithText("Badminton, anyone?", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, joins) }
        compose.onNodeWithTag("dismiss_conflicting_join").performClick()
        compose.runOnIdle { assertEquals(0, joins) }
        compose.onNodeWithTag("join_event").performClick()
        compose.onNodeWithTag("confirm_conflicting_join").performClick()
        compose.onNodeWithText("Schedule conflict").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, joins) }
    }

    @Test fun adjacentEventJoinsDirectly() {
        show(overlap = false)
        compose.onNodeWithText("Schedule conflict").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, joins) }
    }

    @Test fun fullEventExplainsWaitlistBeforeContinuing() {
        show(full = true)
        compose.onNodeWithText("Join waitlist anyway").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, joins) }
    }
}

package com.gogether.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.gogether.app.core.data.DemoData
import com.gogether.app.core.model.RegistrationStatus
import com.gogether.app.feature.registration.ParticipantsCard
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ParticipantsCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun avatarsOverlapThenSpreadAndCollapseInReverseOrder() {
        val snapshot = DemoData.create()
        compose.setContent {
            GogetherTheme { Column { ParticipantsCard(snapshot.events.first(), snapshot) } }
        }
        fun left(user: String) = compose.onNodeWithTag("participant_avatar_$user", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().left.value
        val alice = compose.onNodeWithTag("participant_avatar_alice", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val avatarWidth = (alice.right - alice.left).value
        val compactBob = left("bob")
        val compactCarol = left("carol")
        assertEquals(avatarWidth * 2f / 3f, compactBob - alice.left.value, 1f)
        compose.onNodeWithText("+1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("participant_name_alice", useUnmergedTree = true).assertDoesNotExist()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("participants_toggle").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        // Bob starts after 40 ms, while Carol's 80 ms delay has not elapsed.
        assertTrue(left("bob") > compactBob)
        assertEquals(compactCarol, left("carol"), 1f)
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        listOf("alice", "bob", "carol", "david").forEach {
            compose.onNodeWithTag("participant_name_$it", useUnmergedTree = true).assertIsDisplayed()
        }
        val expandedBob = left("bob")
        val expandedDavid = left("david")
        assertTrue(expandedBob - left("alice") > avatarWidth)

        compose.onNodeWithTag("participants_toggle").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        // David starts returning first. Bob waits for the members to his right.
        assertTrue(left("david") < expandedDavid)
        assertEquals(expandedBob, left("bob"), 1f)
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        compose.onNodeWithTag("participant_name_alice", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(compactBob, left("bob"), 1f)
    }

    @Test fun displayedPeopleFollowCancellationAndPromotion() {
        val snapshot = mutableStateOf(DemoData.create())
        compose.setContent {
            GogetherTheme { Column { ParticipantsCard(snapshot.value.events.first(), snapshot.value) } }
        }
        compose.onNodeWithTag("participants_toggle").performClick()
        compose.onNodeWithTag("participant_name_bob", useUnmergedTree = true).assertTextEquals("Bob")
        compose.runOnIdle {
            snapshot.value = snapshot.value.copy(registrations = snapshot.value.registrations.map {
                when {
                    it.eventId != "event-1" -> it
                    it.userId == "bob" -> it.copy(status = RegistrationStatus.CANCELLED)
                    it.userId == "emma" -> it.copy(status = RegistrationStatus.CONFIRMED)
                    else -> it
                }
            })
        }
        compose.onNodeWithTag("participant_name_bob", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("participant_name_emma", useUnmergedTree = true).assertTextEquals("Emma")
        compose.onNodeWithText("0 on the waitlist").assertIsDisplayed()
    }
}

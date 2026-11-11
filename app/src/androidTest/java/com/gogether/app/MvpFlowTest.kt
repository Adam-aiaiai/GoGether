package com.gogether.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.gogether.app.core.data.FakeGogetherRepository
import com.gogether.app.core.model.RegistrationStatus
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MvpFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var repo: FakeGogetherRepository
    @Before fun launch() {
        repo = FakeGogetherRepository()
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
    }
    // Material snackbars briefly overlay the bottom of scrollable content. Wait for the real
    // touch target to become available instead of bypassing touch input with semantic clicks.
    private fun awaitSnackbar() = compose.waitUntil(15_000) {
        compose.onAllNodesWithTag("snackbar").fetchSemanticsNodes().isEmpty()
    }
    private fun click(tag: String) { awaitSnackbar(); compose.onNodeWithTag(tag).performClick() }
    private fun scrollClick(tag: String) { awaitSnackbar(); compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun text(tag: String, value: String) = compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)

    @Test fun createEventAppearsInExploreAndCreatedTab() {
        scrollClick("login_alice")
        click("nav_create")
        text("create_title", "Team demo walk")
        text("create_description", "A friendly walk around the lake for our team.")
        text("create_location", "Jinji Lake east gate")
        scrollClick("publish_event")
        compose.waitUntil(10_000) { repo.state.value.events.any { it.title == "Team demo walk" } }
        val id = repo.state.value.events.single { it.title == "Team demo walk" }.id
        compose.onNodeWithText("Team demo walk").assertExists()
        click("nav_explore")
        text("search", "Team demo walk")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("event_$id").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("event_$id").performScrollTo().assertIsDisplayed()
        click("nav_my-events")
        compose.onNodeWithText("Created").performClick()
        compose.onNodeWithTag("event_$id").performScrollTo().assertIsDisplayed()
    }

    @Test fun cancellationPromotesEmmaAcrossDetailMyEventsAndProfile() {
        scrollClick("login_bob")
        scrollClick("event_event-1")
        scrollClick("cancel_registration")
        compose.onNodeWithText("Yes, cancel registration").performClick()
        compose.waitUntil(5_000) { repo.state.value.registration("event-1", "emma")?.status == RegistrationStatus.CONFIRMED }
        click("nav_profile")
        scrollClick("switch_emma")
        click("nav_my-events")
        scrollClick("event_event-1")
        compose.onNodeWithText("Your status: confirmed", substring = true).performScrollTo().assertIsDisplayed()
        click("nav_profile")
        compose.onNodeWithText("You're in! A place opened up: Badminton, anyone?").performScrollTo().assertIsDisplayed()
    }

    @Test fun waitlistJoinAndCancelUpdateMyEvents() {
        scrollClick("login_finn")
        scrollClick("event_event-1")
        scrollClick("join_event")
        compose.waitUntil(5_000) { repo.state.value.registration("event-1", "finn")?.status == RegistrationStatus.WAITLISTED }
        compose.onNodeWithTag("waitlist_position").performScrollTo().assertTextEquals("Your waitlist position: 2")
        click("nav_my-events")
        compose.onNodeWithText("Waitlisted").performClick()
        scrollClick("event_event-1")
        scrollClick("cancel_registration")
        compose.onNodeWithText("Yes, cancel registration").performClick()
        compose.waitUntil(5_000) { repo.state.value.registration("event-1", "finn")?.status == RegistrationStatus.CANCELLED }
        assertEquals(listOf("emma"), repo.state.value.waitlist("event-1").map { it.userId })
    }

    @Test fun organizerOpensDemoCheckInAndParticipantChecksInOnce() {
        scrollClick("login_alice")
        scrollClick("event_event-1")
        scrollClick("manage_event")
        scrollClick("toggle_checkin")
        compose.waitUntil(5_000) { repo.state.value.event("event-1")?.demoCheckInOpen == true }
        click("nav_profile")
        scrollClick("switch_bob")
        scrollClick("event_event-1")
        scrollClick("open_checkin")
        scrollClick("demo_checkin")
        compose.waitUntil(5_000) { repo.state.value.checkIns.any { it.userId == "bob" && it.eventId == "event-1" } }
        compose.onNodeWithTag("demo_checkin").assertIsNotEnabled()
        compose.onNodeWithText("You're checked in · Demo").performScrollTo().assertIsDisplayed()
        assertEquals(1, repo.state.value.checkIns.size)
    }

    @Test fun demoRegistrationCreatesProfileAndBlankEventShowsValidation() {
        compose.onNodeWithText("Demo register").performClick()
        text("nickname", "New explorer")
        compose.onNodeWithText("Create Demo profile").performScrollTo().performClick()
        compose.waitUntil(5_000) { repo.state.value.currentUser?.displayName == "New explorer" }
        click("nav_create")
        scrollClick("publish_event")
        compose.onNodeWithTag("create_error").assertTextEquals("Title must contain 3–80 characters.")
        assertEquals(6, repo.state.value.events.size)
    }
}

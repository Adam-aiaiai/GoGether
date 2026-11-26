package com.gogether.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.gogether.app.core.data.FakeGogetherRepository
import com.gogether.app.core.data.DemoData
import com.gogether.app.core.model.RegistrationStatus
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class MvpFlowTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val testName = TestName()
    private lateinit var repo: FakeGogetherRepository
    @Before fun launch() {
        val time = System.currentTimeMillis()
        val seed = DemoData.create(time)
        // Only this flow needs an event inside the window; other flows retain their original fixtures.
        val initial = if (testName.methodName == "normalEntryRequiresBoundMeetingInsteadOfBypassingTheScanner")
            seed.copy(events = seed.events.map {
                if (it.id == "event-1") it.copy(startAt = time + 600_000, endAt = time + 7_800_000) else it
            }) else seed
        repo = FakeGogetherRepository(initial)
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
    }
    // SnackbarHost's dismissal delay and exit animation use the Compose test clock.
    // A wall-clock waitUntil timeout can expire first on a slow emulator. Advance virtual
    // time until the snackbar leaves semantics, then finish Android draw/layout before tapping.
    private fun awaitSnackbar() {
        val start = compose.mainClock.currentTime
        // Keep semantics queries on the test thread: in Compose 1.9, advanceTimeUntil
        // evaluates its condition on the UI thread, where these queries cannot synchronize.
        while (compose.onAllNodesWithTag("snackbar").fetchSemanticsNodes().isNotEmpty()) {
            assertTrue("Snackbar did not disappear within 15000 ms of Compose test time.",
                compose.mainClock.currentTime - start < 15_000)
            compose.mainClock.advanceTimeBy(100)
        }
        compose.waitForIdle()
    }
    private fun click(tag: String) { awaitSnackbar(); compose.onNodeWithTag(tag).performClick() }
    private fun scrollClick(tag: String) { awaitSnackbar(); compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun text(tag: String, value: String) = compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)

    private fun captureDialog(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "square-dialogs").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(folder, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

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
        // Finish typing before scrolling; the IME changes the available list viewport.
        closeSoftKeyboard()
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("event_$id").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("event_$id").performScrollTo().assertIsDisplayed()
        click("nav_my-events")
        compose.onNodeWithText("Created").performClick()
        compose.onNodeWithTag("event_$id").performScrollTo().assertIsDisplayed()
    }

    @Test fun exploreBottomNavigationReturnsFromProfile() {
        scrollClick("login_alice")
        click("nav_profile")
        compose.onNodeWithText("Hey, Alice").assertExists()
        click("nav_explore")
        compose.onNodeWithText("Find your next plan.").assertExists()
        compose.onNodeWithTag("search").assertExists()
    }

    @Test fun createDraftSurvivesBottomNavigation() {
        scrollClick("login_alice")
        click("nav_create")
        text("create_title", "Saved campus plan")
        text("create_description", "This draft should still be here after browsing Explore.")
        text("create_location", "Library entrance")
        click("nav_explore")
        click("nav_create")
        compose.onNodeWithTag("create_title").assertTextContains("Saved campus plan")
        compose.onNodeWithTag("create_description").performScrollTo()
            .assertTextContains("This draft should still be here after browsing Explore.")
        compose.onNodeWithTag("create_location").performScrollTo()
            .assertTextContains("Library entrance")
    }

    @Test fun advancedFiltersExpandAndAcceptOrganizer() {
        scrollClick("login_alice")
        compose.onNodeWithTag("organizer_filter").assertDoesNotExist()
        scrollClick("toggle_filters")
        text("organizer_filter", "Alice")
        closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithTag("event_event-1").performScrollTo().assertIsDisplayed()
    }

    @Test fun organizerReusesConfigurationAsNewEvent() {
        scrollClick("login_alice")
        scrollClick("event_event-3")
        scrollClick("manage_event")
        scrollClick("reuse_event")
        text("reuse_title", "Weekly photo walk")
        text("reuse_location", "Jinji Lake west gate")
        scrollClick("publish_reused_event")
        compose.waitUntil(5_000) { repo.state.value.events.any { it.title == "Weekly photo walk" } }
        val reused = repo.state.value.events.single { it.title == "Weekly photo walk" }
        assertEquals("Jinji Lake west gate", reused.location)
        assertEquals("A phone or camera", reused.requiredItems)
        compose.onNodeWithText("Weekly photo walk").assertExists()
    }

    @Test fun organizerEditsEventAndParticipationInformation() {
        scrollClick("login_alice")
        scrollClick("event_event-3")
        scrollClick("manage_event")
        scrollClick("edit_event")
        text("edit_title", "Beginner photo walk")
        text("edit_required_items", "A phone or camera and water")
        text("edit_spare_equipment", "One spare phone tripod")
        scrollClick("edit_guidance")
        scrollClick("save_event")
        compose.waitUntil(5_000) { repo.state.value.event("event-3")?.title == "Beginner photo walk" }
        compose.onNodeWithText("Beginner photo walk").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bring: A phone or camera and water", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Spare equipment: One spare phone tripod", substring = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun cancellationPromotesEmmaAcrossDetailMyEventsAndProfile() {
        scrollClick("login_bob")
        scrollClick("event_event-1")
        scrollClick("cancel_registration")
        compose.onNodeWithText("Cancel your registration?").assertIsDisplayed()
        captureDialog("01-cancel-registration")
        compose.onNodeWithTag("registration_cancel_dialog").performTouchInput { click(center) }.assertIsDisplayed()
        compose.onNodeWithText("Keep my place").performClick()
        assertEquals(RegistrationStatus.CONFIRMED, repo.state.value.registration("event-1", "bob")?.status)
        assertEquals(RegistrationStatus.WAITLISTED, repo.state.value.registration("event-1", "emma")?.status)
        scrollClick("cancel_registration")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("registration_cancel_dialog").assertDoesNotExist()
        assertEquals(RegistrationStatus.CONFIRMED, repo.state.value.registration("event-1", "bob")?.status)
        scrollClick("cancel_registration")
        compose.onNode(isDialog()).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(center.x, height * .15f))
        }
        compose.onNodeWithTag("registration_cancel_dialog").assertDoesNotExist()
        assertEquals(RegistrationStatus.CONFIRMED, repo.state.value.registration("event-1", "bob")?.status)
        scrollClick("cancel_registration")
        compose.onNodeWithText("Yes, cancel").performClick()
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
        compose.onNodeWithText("Leave the waitlist?").assertIsDisplayed()
        compose.onNodeWithText("You will lose your current position in the queue.").assertIsDisplayed()
        captureDialog("02-leave-waitlist")
        compose.onNodeWithText("Stay on list").performClick()
        assertEquals(listOf("emma", "finn"), repo.state.value.waitlist("event-1").map { it.userId })
        scrollClick("cancel_registration")
        compose.onNodeWithText("Leave waitlist").performClick()
        compose.waitUntil(5_000) { repo.state.value.registration("event-1", "finn")?.status == RegistrationStatus.CANCELLED }
        assertEquals(listOf("emma"), repo.state.value.waitlist("event-1").map { it.userId })
    }

    @Test fun normalEntryRequiresBoundMeetingInsteadOfBypassingTheScanner() {
        scrollClick("login_alice")
        scrollClick("event_event-1")
        scrollClick("manage_event")
        compose.onNodeWithTag("checkin_window").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("toggle_checkin").assertDoesNotExist()
        click("nav_profile")
        scrollClick("switch_bob")
        scrollClick("event_event-1")
        scrollClick("open_checkin")
        compose.onNodeWithTag("demo_checkin").assertDoesNotExist()
        compose.onNodeWithTag("meeting_setup_status").assertTextEquals("The organizer has not set the meeting point. Try again later.")
        assertTrue(repo.state.value.checkIns.isEmpty())
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

    @Test fun organizerAnnouncementReachesParticipantAndCanBeRead() {
        scrollClick("login_alice")
        scrollClick("event_event-1")
        scrollClick("manage_event")
        compose.onNodeWithText("Organizer announcement").performScrollTo().performTextInput("Meet by the east gate")
        closeSoftKeyboard()
        compose.onNodeWithText("Post announcement").performScrollTo().performClick()
        compose.waitUntil(10_000) { repo.state.value.announcements.isNotEmpty() }
        click("nav_profile"); scrollClick("switch_bob")
        click("nav_my-events"); scrollClick("event_event-1")
        compose.onNodeWithText("Meet by the east gate").performScrollTo().assertIsDisplayed()
        click("nav_profile")
        compose.onNodeWithText("Mark as read").performScrollTo().performClick()
        compose.waitUntil(10_000) { repo.state.value.notifications.single { it.userId == "bob" }.isRead }
        assertFalse(repo.state.value.notifications.single { it.userId == "emma" }.isRead)
    }

    @Test fun meetingPickersValidationAndParticipantDisplay() {
        scrollClick("login_alice"); scrollClick("event_event-1"); scrollClick("manage_event")
        scrollClick("edit_meeting")
        text("meeting_location", "x"); closeSoftKeyboard(); scrollClick("save_meeting")
        compose.onNodeWithText("Use 3–150 characters for the location.").performScrollTo().assertIsDisplayed()
        scrollClick("meeting_date")
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.click())
        scrollClick("meeting_time")
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.click())
        text("meeting_location", "South entrance for meeting")
        text("meeting_notes", "Bring water and a racket")
        closeSoftKeyboard(); scrollClick("save_meeting")
        compose.waitUntil(10_000) { repo.state.value.event("event-1")?.meetingLocation == "South entrance for meeting" }
        awaitSnackbar(); compose.onNodeWithTag("save_meeting").assertDoesNotExist()
        click("nav_profile"); scrollClick("switch_bob"); click("nav_my-events"); scrollClick("event_event-1")
        compose.onNodeWithText("South entrance for meeting",substring=true).performScrollTo().assertIsDisplayed()
        scrollClick("open_checkin")
        compose.onNodeWithText("South entrance for meeting",substring=true).performScrollTo().assertIsDisplayed()
    }

    @Test fun notificationEntryFiltersAndMarkAllReadStayUserScoped() {
        kotlinx.coroutines.runBlocking {
            repo.login("alice").getOrThrow(); repo.postAnnouncement("event-1","First notice").getOrThrow()
            repo.postAnnouncement("event-1","Second notice").getOrThrow(); repo.login("bob").getOrThrow()
        }
        click("nav_my-events"); compose.onNodeWithText("Notifications · 2 unread").assertExists()
        scrollClick("open_inbox"); scrollClick("inbox_unread"); scrollClick("read_all")
        compose.waitUntil(10_000) { repo.state.value.notifications.filter { it.userId=="bob" }.all { it.isRead } }
        awaitSnackbar(); compose.onNodeWithText("You're all caught up").performScrollTo().assertIsDisplayed()
        scrollClick("inbox_all"); compose.onNodeWithText("Second notice").performScrollTo().assertIsDisplayed()
        assertTrue(repo.state.value.notifications.filter { it.userId=="emma" }.none { it.isRead })
    }

    @Test fun emptyMyEventsOffersWorkingExploreActionAndHistorySeparatesPastPlans() {
        scrollClick("login_finn"); click("nav_my-events"); scrollClick("empty_events_action")
        compose.onNodeWithTag("search").assertExists()
        click("nav_profile"); scrollClick("switch_bob"); click("nav_my-events")
        compose.onNodeWithTag("event_event-history").assertDoesNotExist()
        scrollClick("personal_tab_HISTORY"); compose.onNodeWithTag("event_event-history").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("event_event-1").assertDoesNotExist()
    }
}

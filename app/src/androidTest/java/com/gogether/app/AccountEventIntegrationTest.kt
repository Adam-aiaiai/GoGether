package com.gogether.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.DemoData
import com.gogether.app.core.data.FakeGogetherRepository
import com.gogether.app.core.data.FileSnapshotStore
import com.gogether.app.core.model.EventStatus
import com.gogether.app.core.model.RegistrationStatus
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercises A's identity/personal pages and B's event pages through the same app and disk store. */
class AccountEventIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var repo: FakeGogetherRepository
    private lateinit var store: FileSnapshotStore
    private lateinit var file: File

    @Before fun launch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        file = File.createTempFile("account-event-integration-", ".json", context.cacheDir).apply { delete() }
        store = FileSnapshotStore(file)
        repo = FakeGogetherRepository(initial = DemoData.create(), store = store)
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
    }

    @After fun cleanUp() { if (::file.isInitialized) file.delete() }

    private fun settle() {
        val start = compose.mainClock.currentTime
        while (compose.onAllNodesWithTag("snackbar").fetchSemanticsNodes().isNotEmpty()) {
            assertTrue("Snackbar did not dismiss", compose.mainClock.currentTime - start < 15_000)
            compose.mainClock.advanceTimeBy(100)
        }
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        if (tag.startsWith("switch_") || tag == "logout") compose.onNodeWithTag("profile_account_menu").performClick()

        settle()
        runCatching { compose.onNodeWithTag(tag).performScrollTo() }; compose.onNodeWithTag(tag).performClick()
    }

    private fun nav(tag: String) {
        closeSoftKeyboard()
        settle()
        compose.onNodeWithTag(tag).performClick()
    }

    private fun text(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    }

    @Test fun newProfilePublishesEditsAndFindsTheSameEventAfterDiskRestore() {
        compose.onNodeWithText("Demo register").performClick()
        text("nickname", "Campus Organizer")
        closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("Create Demo profile").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { repo.state.value.currentUser?.displayName == "Campus Organizer" }
        val userId = repo.state.value.currentUserId!!

        nav("nav_create")
        text("create_title", "Campus welcome walk")
        text("create_description", "Meet classmates and explore our campus together.")
        text("create_location", "Library entrance")
        text("create_required_items", "Water and comfortable shoes")
        text("create_spare_equipment", "Two spare umbrellas")
        click("create_guidance")
        closeSoftKeyboard()
        click("publish_event")
        compose.waitUntil(5_000) { repo.state.value.events.any { it.title == "Campus welcome walk" } }
        val event = repo.state.value.events.single { it.title == "Campus welcome walk" }
        assertEquals(userId, event.organizerId)
        assertTrue(event.beginnerGuidance)
        assertEquals(RegistrationStatus.CONFIRMED, repo.state.value.registration(event.id)?.status)

        nav("nav_profile")
        click("edit_profile")
        text("profile_name", "Campus Host")
        closeSoftKeyboard()
        click("save_profile")
        compose.waitUntil(5_000) { repo.state.value.currentUser?.displayName == "Campus Host" }
        assertEquals(userId, repo.state.value.currentUserId)

        nav("nav_my-events")
        click("personal_tab_CREATED")
        click("event_${event.id}")
        compose.onNodeWithText("Campus Host").performScrollTo().assertIsDisplayed()
        click("manage_event")
        click("edit_event")
        text("edit_title", "Updated campus walk")
        text("edit_location", "Student centre entrance")
        closeSoftKeyboard()
        click("save_event")
        compose.waitUntil(5_000) { repo.state.value.event(event.id)?.title == "Updated campus walk" }

        nav("nav_explore")
        text("search", "Campus Host")
        closeSoftKeyboard()
        compose.onNodeWithTag("event_${event.id}").performScrollTo().assertIsDisplayed()
        nav("nav_my-events")
        click("personal_tab_CREATED")
        compose.onNodeWithText("Updated campus walk").performScrollTo().assertIsDisplayed()

        val restored = FakeGogetherRepository(initial = store.load()!!, store = store).state.value
        assertEquals(userId, restored.currentUserId)
        assertEquals("Campus Host", restored.currentUser?.displayName)
        assertEquals(repo.state.value.event(event.id), restored.event(event.id))
        assertEquals("Water and comfortable shoes", restored.event(event.id)?.requiredItems)
        assertEquals("Two spare umbrellas", restored.event(event.id)?.spareEquipment)
        assertEquals(1, restored.confirmed(event.id).size)
    }

    @Test fun organizerCancellationUpdatesBothPersonalHistoriesAndPersistsAfterLogout() {
        click("login_alice")
        nav("nav_my-events")
        click("personal_tab_CREATED")
        click("event_event-1")
        click("manage_event")
        click("cancel_event")
        compose.onNodeWithText("Yes, cancel event").performClick()
        compose.waitUntil(5_000) { repo.state.value.event("event-1")?.status == EventStatus.CANCELLED }

        nav("nav_my-events")
        click("personal_tab_JOINED")
        compose.onNodeWithTag("event_event-1").assertDoesNotExist()
        click("personal_tab_HISTORY")
        compose.onNodeWithTag("event_event-1").performScrollTo().assertIsDisplayed()
        nav("nav_profile")
        click("switch_bob")
        nav("nav_my-events")
        compose.onNodeWithTag("event_event-1").assertDoesNotExist()
        click("personal_tab_HISTORY")
        click("event_event-1")
        compose.onNodeWithTag("manage_event").assertDoesNotExist()

        nav("nav_explore")
        compose.onNodeWithTag("event_event-1").assertDoesNotExist()
        nav("nav_profile")
        click("logout")
        compose.onAllNodesWithText("Log out").onLast().performClick()
        compose.waitUntil(5_000) { repo.state.value.currentUserId == null }

        val restored = FakeGogetherRepository(initial = store.load()!!, store = store).state.value
        assertNull(restored.currentUser)
        assertEquals(EventStatus.CANCELLED, restored.event("event-1")?.status)
        assertEquals(RegistrationStatus.CANCELLED, restored.registration("event-1", "bob")?.status)
        assertTrue(restored.notifications.any { it.eventId == "event-1" && it.userId == "bob" })
    }

    @Test fun draftSurvivesPersonalPagesAndOtherProfileCannotPublishIt() {
        click("login_alice")
        nav("nav_create")
        text("create_title", "Alice's unfinished walk")
        text("create_description", "A draft retained while visiting personal pages.")
        text("create_location", "North gate")
        nav("nav_my-events")
        click("personal_tab_CREATED")
        nav("nav_profile")
        nav("nav_create")
        compose.onNodeWithTag("create_title").assertTextContains("Alice's unfinished walk")
        compose.onNodeWithTag("create_location").performScrollTo().assertTextContains("North gate")

        nav("nav_profile")
        click("switch_bob")
        nav("nav_create")
        compose.onNodeWithTag("create_title").assert(hasText("Alice's unfinished walk").not())
        click("publish_event")
        compose.onNodeWithTag("create_error").assertTextEquals("Title must contain 3–80 characters.")
        assertFalse(repo.state.value.events.any { it.title == "Alice's unfinished walk" })
        assertEquals("bob", repo.state.value.currentUserId)
    }
}

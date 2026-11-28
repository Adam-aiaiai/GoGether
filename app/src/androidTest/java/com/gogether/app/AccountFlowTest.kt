package com.gogether.app

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import com.gogether.app.core.account.AccountBackend
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlinx.coroutines.runBlocking

private fun ComposeContentTestRule.awaitAccountSnackbar() {
    val start = mainClock.currentTime
    while (onAllNodesWithTag("snackbar").fetchSemanticsNodes().isNotEmpty()) {
        assertTrue("Snackbar did not disappear within 15000 ms of Compose test time.",
            mainClock.currentTime - start < 15_000)
        mainClock.advanceTimeBy(100)
    }
    waitForIdle()
}

class AccountFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var repo: FakeGogetherRepository
    @Before fun launch() {
        repo = FakeGogetherRepository()
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
    }
    private fun awaitSnackbar() = compose.awaitAccountSnackbar()
    private fun click(tag: String) {
        if (tag.startsWith("switch_") || tag == "logout") compose.onNodeWithTag("profile_account_menu").performClick()
        awaitSnackbar()
        runCatching { compose.onNodeWithTag(tag).performScrollTo() }
        compose.onNodeWithTag(tag).performClick()
    }
    private fun nav(tag: String) {        awaitSnackbar()
        compose.onNodeWithTag(tag).performClick()
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "account-ui").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun editProfileUpdatesSharedIdentityAndKeepsBsEventRoutes() {
        click("login_alice")
        nav("nav_profile")
        click("edit_profile")
        compose.onNodeWithTag("profile_name").performTextReplacement("Alice Campus")
        compose.onNodeWithTag("interest_STUDY").performScrollTo().performClick()
        capture("edit-profile")
        click("save_profile")
        compose.waitUntil(5_000) { repo.state.value.currentUser?.displayName == "Alice Campus" }
        compose.onNodeWithText("Alice Campus").assertExists()
        awaitSnackbar()
        capture("profile")
        assertTrue(Category.STUDY in repo.state.value.currentUser!!.interests)
        assertEquals(4, repo.state.value.confirmed("event-1").size)
        nav("nav_explore")
        click("event_event-1")
        click("manage_event")
        compose.onNodeWithText("Edit event").performScrollTo().assertIsDisplayed()
    }
    @Test fun duplicateNameShowsErrorAndDoesNotCloseEditor() {
        click("login_bob")
        nav("nav_profile")
        click("edit_profile")
        compose.onNodeWithTag("profile_name").performTextReplacement("Alice")
        click("save_profile")
        compose.onNodeWithText("That nickname is already in use. Choose another one.").assertExists()
        compose.onNodeWithTag("profile_name").assertExists()
        assertEquals("Bob", repo.state.value.currentUser?.displayName)
    }
    @Test fun historySeparatesPastPlansAndSwitchClearsPersonalView() {
        click("login_bob")
        nav("nav_my-events")
        compose.onNodeWithTag("event_event-history").assertDoesNotExist()
        compose.onNodeWithTag("personal_tab_HISTORY").performScrollTo().performClick()
        compose.onNodeWithTag("event_event-history").performScrollTo().assertIsDisplayed()
        capture("history")
        nav("nav_profile")
        click("switch_finn")
        nav("nav_my-events")
        compose.onNodeWithTag("personal_tab_HISTORY").performScrollTo().performClick()
        compose.onNodeWithText("No history activities yet").assertIsDisplayed()
        compose.onNodeWithTag("event_event-history").assertDoesNotExist()
    }
    @Test fun logoutReturnsToAuthAndCannotRevealThePreviousProfile() {
        click("login_bob")
        nav("nav_profile")
        click("logout")
        compose.onNodeWithText("Log out?", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Log out").onLast().performClick()
        compose.waitUntil(5_000) { repo.state.value.currentUserId == null }
        compose.onNodeWithTag("login_bob").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bob").assertDoesNotExist()
    }
}

class SnapshotPersistenceTest {
    @Test fun jsonRoundTripPreservesEveryBsFieldAndParticipationRecord() {
        val seed = DemoData.create().copy(currentUserId = "bob")
        val data = seed.copy(events = seed.events.map { it.copy(imageUri = "content://test/${it.id}") },
            checkIns = listOf(CheckIn("c", "event-1", "bob", 10)),
            notifications = listOf(Notification("n", "bob", "event-1", NotificationType.ANNOUNCEMENT, "Hello", 20, true)),
            feedback = listOf(Feedback("f", "event-history", "bob", 5, "Good", 30)))
        assertEquals(data, SnapshotJson.decode(SnapshotJson.encode(data)))
    }
    @Test fun actualDiskRestoreKeepsProfileSessionEventEditsAndLogout() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("account-persistence-", ".json", context.cacheDir).apply { delete() }
        try {
            val store = FileSnapshotStore(file)
            val first = FakeGogetherRepository(initial = store.load() ?: DemoData.create(), store = store)
            first.login("alice").getOrThrow()
            first.updateProfile("Alice Saved", setOf(Category.LANGUAGE)).getOrThrow()
            val now = System.currentTimeMillis()
            val event = first.createEvent(EventDraft("Saved workshop", Category.STUDY, "A workshop for our classmates",
                now + 100_000, "Library Room 3", "Suzhou", 5, now + 90_000,
                endAt = now + 200_000, beginnerGuidance = true, requiredItems = "Notebook", spareEquipment = "Two pens")).getOrThrow()
            val second = FakeGogetherRepository(initial = store.load()!!, store = store)
            assertEquals("alice", second.state.value.currentUserId)
            assertEquals("Alice Saved", second.state.value.currentUser?.displayName)
            assertEquals(event, second.state.value.event(event.id))
            second.logout().getOrThrow()
            val third = FakeGogetherRepository(initial = store.load()!!)
            assertNull(third.state.value.currentUser)
            assertEquals(event, third.state.value.event(event.id))
        } finally { file.delete() }
    }
    @Test fun invalidSaveIsReportedAndNotOverwritten() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("invalid-account-", ".json", context.cacheDir)
        try {
            file.writeText("broken saved data")
            assertThrows(Exception::class.java) { FileSnapshotStore(file).load() }
            assertEquals("broken saved data", file.readText())
        } finally { file.delete() }
    }
}


class ConnectedAccountFormTest {
    @get:Rule val compose = createComposeRule()
    @Test fun backendAdapterEnablesEmailFormAndUsesItsCanonicalIdentity() {
        val backend = object : AccountBackend {
            var current: User? = null
            override suspend fun restoreSession(): User? = null
            override suspend fun signIn(email: String, password: String): User {
                require(email == "student@example.com" && password == "test-password")
                return User("verified-test-id", "Test student", setOf(Category.STUDY), 1,
                    UserStatus.ACCOUNT, email).also { current = it }
            }
            override suspend fun signUp(email: String, password: String, name: String, interests: Set<Category>): User = error("unused")
            override suspend fun updateProfile(name: String, interests: Set<Category>): User = requireNotNull(current).copy(displayName = name, interests = interests)
            override suspend fun signOut() { current = null }
        }
        val repo = FakeGogetherRepository(accountBackend = backend)
        compose.setContent { GogetherTheme { GogetherApp(repo) } }
        compose.waitUntil(5_000) { !repo.state.value.sessionRestoring }
        compose.awaitAccountSnackbar()
        compose.onNodeWithTag("login_alice").assertDoesNotExist()
        compose.onNodeWithTag("account_email").performTextInput("student@example.com")
        compose.onNodeWithTag("account_password").performTextInput("test-password")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "account-ui").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "email-form.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithTag("account_submit").performScrollTo().performClick()
        compose.waitUntil(5_000) { repo.state.value.currentUserId == "verified-test-id" }
        compose.awaitAccountSnackbar()
        compose.onNodeWithTag("nav_profile").performClick()
        compose.onNodeWithText("Test student").assertExists()
        assertEquals("student@example.com", repo.state.value.currentUser?.email)
        assertEquals(UserStatus.ACCOUNT, repo.state.value.currentUser?.status)
        compose.onNodeWithTag("profile_account_menu").performClick()
        compose.onNodeWithTag("logout").assertExists()
        compose.onNodeWithTag("switch_alice").assertDoesNotExist()
    }
}

package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalTestApi::class)
class MultiActivityIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("checkin_prototype_v1", 0)
    private val time = 1_800_000_000_000L
    private val fixtures = MultiActivityCheckInScenario(time)
    private val a = fixtures.aliceFirst
    private val b = fixtures.aliceSecond
    private val c = fixtures.carolEvent
    private val store = PreferencesPrototypeStore(context)
    private val imported = mutableListOf<Pair<String, String>>()
    private fun backend(now: Long = a.endAt) = LocalPrototypeBackend(store, { now }, { 0 }, fixtures.registrations, fixtures.activities)
    private fun seed() = fixtures.seed(store)
    private var actor by mutableStateOf("carol")
    private var selected by mutableStateOf(c)

    @After fun cleanup() {
        val edit = prefs.edit()
        fixtures.sessions.forEach { session ->
            listOf("alice", "bob", "carol", "david").forEach { edit.remove(FeedbackDraftStore.key(it, session.id)) }
            prefs.all.keys.filter { it.contains(session.id) }.forEach(edit::remove)
        }
        edit.commit()
        imported.forEach { (id, session) -> FeedbackPhotoStore(context).removeDraft(id, "bob", session) }
    }

    @Test fun persistedCarolPrivatePhotosNeverFallBackToAlicePermission(): Unit = runBlocking {
        seed()
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        bitmap.recycle()
        for (session in listOf(a, c)) {
            val photo = FeedbackPhotoStore(context).importImage("bob", session.id, ByteArrayInputStream(bytes))
            imported += photo to session.id
            backend().submitFeedback("bob", session.id, 4, session.id,
                FeedbackDetails(anonymous = true, visibility = FeedbackVisibility.ORGANIZER_ONLY, photos = listOf(photo)))
        }
        val first = imported[0].first
        val third = imported[1].first
        val restartedPhotos = FeedbackPhotoStore(context) // No injected catalog: persisted B fixture supplies ownership.
        assertNotNull(restartedPhotos.bitmap(first, "alice", a.id))
        assertNull(restartedPhotos.bitmap(first, "carol", a.id))
        assertNotNull(restartedPhotos.bitmap(third, "carol", c.id))
        assertNull(restartedPhotos.bitmap(third, "alice", c.id))
        assertNull(restartedPhotos.bitmap(third, "david", c.id))
        assertNull(restartedPhotos.bitmap(third, "carol", a.id))
        assertNotNull(restartedPhotos.bitmap(third, "bob", c.id))
        val restarted = LocalPrototypeBackend(PreferencesPrototypeStore(context), { a.endAt }, { 0 })
        assertEquals("carol", restarted.activityInfo(c).organizerId)
        assertEquals(c, restarted.hostedSession("carol", c))
        assertNull(restarted.hostedSession("alice", c))
        assertEquals("Anonymous participant", restarted.reviews("carol", c).single().authorName)
        assertEquals(listOf("carol", "bob", "david"), restarted.attendance("carol", c).map { it.userId })
        store.rememberSession("alice", c)
        assertThrows(IllegalArgumentException::class.java) { restarted.submitFeedback("alice", c.id, 5, "I would like to join next time.") }
        val restoredC = StoredMockCheckInRegistrations(PreferencesPrototypeStore(context))
        assertEquals(CheckInRegistration.WAITLISTED, restoredC.status(b.id, "bob"))
        assertEquals(CheckInRegistration.NONE, restoredC.status(c.id, "alice"))
        assertThrows(IllegalArgumentException::class.java) {
            store.rememberActivityInfo(fixtures.activities.activity(c).copy(organizerId = "alice"))
        }
    }

    @Test fun switchingActivitiesAndAccountsShowsOnlyTheirOwnOrganizerEntryAndTitle() {
        seed()
        compose.setContent {
            CompositionLocalProvider(LocalCheckInActivities provides fixtures.activities) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp))) {
                    GogetherTheme {
                        key(actor, selected.id) {
                            PrototypeHistoryScreen(actor, listOf(PrototypeHistoryEntry(selected, null, null, false)), a.endAt,
                                false, "", {}, { _, _, _, _ -> }, initialSessionId = selected.id)
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Easy Jog").assertIsDisplayed()
        compose.onNodeWithText("Hosted by Carol").assertIsDisplayed()
        compose.onNodeWithTag("history_open_attendance").assertExists()
        compose.runOnIdle { selected = a }
        compose.onNodeWithText("Badminton Meetup").assertIsDisplayed()
        compose.onNodeWithText("Hosted by Alice").assertIsDisplayed()
        compose.onNodeWithTag("history_open_attendance").assertDoesNotExist()
        compose.runOnIdle { actor = "alice"; selected = b }
        compose.onNodeWithText("Board Game Evening").assertIsDisplayed()
        compose.onNodeWithTag("history_open_attendance").assertExists()
        compose.runOnIdle { selected = c }
        compose.onNodeWithTag("history_open_attendance").assertDoesNotExist()
        compose.onNodeWithText("Hosted by Carol").assertIsDisplayed()
    }

    @Test fun organizerAndParticipantPanelsSwitchRoleWithoutReusingRoster() {
        seed()
        val local = backend(time)
        compose.setContent {
            CompositionLocalProvider(LocalCheckInActivities provides fixtures.activities) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp))) {
                    GogetherTheme {
                        val hosted = local.hostedSession(actor, selected)
                        val state = PrototypeUiState(session = hosted, participantSession = if (hosted == null) selected else null,
                            ownerUserId = actor, entry = CheckInEntry.Activity(selected),
                            history = local.history(actor), registration = fixtures.registrations.status(selected.id, actor),
                            participants = fixtures.registrations.participants(selected.id),
                            attendees = if (hosted != null) local.attendance(actor, selected) else emptyList(),
                            attendance = AttendanceLoadState(selected, loaded = hosted != null))
                        PrototypeScreen(actor, state, time, {}, {}, {}, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("organizer_panel").assertIsDisplayed()
        compose.onNodeWithTag("participant_panel").assertDoesNotExist()
        compose.runOnIdle { selected = a }
        compose.onNodeWithTag("organizer_panel").assertDoesNotExist()
        compose.onNodeWithTag("participant_panel").assertIsDisplayed()
        compose.onNodeWithTag("participant_event_title").assertTextEquals("Badminton Meetup")
        compose.runOnIdle { actor = "alice"; selected = c }
        compose.onNodeWithTag("participant_status_title").assertTextEquals("Not registered")
        compose.onNodeWithTag("participant_event_title").assertTextEquals("Easy Jog")
        compose.onNodeWithText("Hosted by Carol").assertIsDisplayed()
        compose.runOnIdle { selected = b }
        compose.onNodeWithTag("organizer_panel").assertIsDisplayed()
        compose.onNodeWithTag("participant_panel").assertDoesNotExist()
    }

    @Test fun privateReviewListsSwitchBetweenOrganizersWithoutExposingNamesOrOtherEvents() {
        seed()
        store.rememberSession("alice", c)
        val local = backend()
        local.submitFeedback("bob", a.id, 4, "A longer warm-up would help before badminton.", FeedbackDetails(anonymous = true, visibility = FeedbackVisibility.ORGANIZER_ONLY))
        local.submitFeedback("bob", c.id, 3, "Please consider a slower pace for the next run.", FeedbackDetails(anonymous = true, visibility = FeedbackVisibility.ORGANIZER_ONLY))
        local.submitFeedback("david", c.id, 5, "A lovely route and a friendly running group.")
        compose.setContent { CompositionLocalProvider(LocalCheckInActivities provides fixtures.activities) {
            GogetherTheme { ActivityReviewsScreen(actor, selected, local.reviews(actor, selected), false, "", {}, {}) }
        } }
        compose.onNodeWithText("Please consider a slower pace for the next run.").assertExists()
        compose.onNodeWithText("Anonymous participant").assertExists()
        compose.onNodeWithText("Bob").assertDoesNotExist()
        compose.runOnIdle { actor = "alice" }
        compose.onNodeWithText("Please consider a slower pace for the next run.").assertDoesNotExist()
        compose.onNodeWithText("A lovely route and a friendly running group.").assertExists()
        compose.runOnIdle { selected = a }
        compose.onNodeWithText("A longer warm-up would help before badminton.").assertExists()
        compose.onNodeWithText("A lovely route and a friendly running group.").assertDoesNotExist()
        compose.runOnIdle { actor = "carol" }
        compose.onNodeWithText("A longer warm-up would help before badminton.").assertDoesNotExist()
        compose.onNodeWithText("No reviews to show yet").assertExists()
    }
}

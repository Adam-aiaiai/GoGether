package com.gogether.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.FileSnapshotStore
import com.gogether.app.core.model.CheckIn
import com.gogether.app.core.model.CheckInStatus
import com.gogether.app.core.model.EventStatus
import com.gogether.app.core.model.Registration
import com.gogether.app.core.model.RegistrationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Explicit team setup, not a regression test. Never clears or replaces existing app data. */
class TeamAcceptanceDemoSetup {
    @Test
    fun prepareManualFixtures(): Unit = runBlocking {
        assumeTrue("Use -e prepareTeamDemo true to prepare manual fixtures.",
            InstrumentationRegistry.getArguments().getString("prepareTeamDemo") == "true")
        check(BuildConfig.BACKEND_MODE == "demo") { "Manual fixtures require -Pbackend=demo." }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val disk = FileSnapshotStore(File(context.filesDir, "integrated-snapshot-v1.json"))
        if (disk.load() == null) {
            (context.applicationContext as GogetherApplication).repository.login("alice").getOrThrow()
        }
        val initial = requireNotNull(disk.load())
        check(setOf("alice", "bob", "carol", "david", "emma", "finn")
            .all { id -> initial.users.any { it.id == id } }) { "The standard Demo accounts are required." }

        fun prepareOnce(markerName: String, titles: Set<String>, prepare: () -> Unit) {
            val snapshot = requireNotNull(disk.load())
            val marker = File(context.filesDir, markerName)
            if (marker.exists()) {
                check(titles.all { title -> snapshot.events.count { it.title == title } == 1 }) {
                    "Existing fixtures have been renamed, removed or duplicated. Use a separate test AVD."
                }
                return // Preserve manual edits, read flags, current identity and anonymous aliases.
            }
            check(snapshot.events.none { it.title in titles }) {
                "An older or incomplete fixture set already exists. Use a separate test AVD; no data was reset."
            }
            prepare()
            check(marker.exists()) { "Fixture preparation did not complete: $markerName" }
        }

        prepareOnce("phase1-manual-fixtures.txt", setOf(
            "Phase 1 - Rejoin test", "Phase 1 - Host arrival", "Phase 1 - Review access",
        )) { prepareAttendance(context, disk) }
        prepareOnce("phase2-manual-fixtures.txt", setOf(
            "Phase 2 - Questions", "Phase 2 - Reviews", "Phase 2 - Private feedback",
        )) { PhaseTwoDemoSetup().prepareManualFixtures() }
        prepareOnce("phase3-manual-fixtures.txt", setOf(
            "Phase 3 - Conversations", "Phase 3 - Review notifications",
        )) { PhaseThreeDemoSetup().prepareManualFixtures() }
        prepareOnce("phase4-manual-fixtures.txt", setOf(
            "Phase 4 - Public ratings", "Phase 4 - No public ratings",
        )) { PhaseFourDemoSetup().prepareManualFixtures() }
    }

    private fun prepareAttendance(context: Context, disk: FileSnapshotStore) {
        val saved = requireNotNull(disk.load())
        val now = System.currentTimeMillis()
        val minute = 60_000L
        val prefix = "phase1-${UUID.randomUUID()}"
        val ended = saved.events.first().copy(
            id = "$prefix-review", organizerId = "alice", title = "Phase 1 - Review access",
            description = "Alice and Bob arrived. Carol registered but did not check in.",
            createdAt = now, updatedAt = now, status = EventStatus.COMPLETED,
            startAt = now - 60 * minute, endAt = now - 10 * minute,
            registrationDeadline = now - 70 * minute, capacity = 4,
            meetingAt = null, meetingLocation = null, meetingNotes = "", imageUri = "",
            demoCheckInOpen = false,
        )
        val host = ended.copy(id = "$prefix-host", title = "Phase 1 - Host arrival",
            description = "The organizer has no arrival record and cannot write a review.")
        val rejoin = ended.copy(id = "$prefix-rejoin", title = "Phase 1 - Rejoin test",
            description = "Bob has a fixture check-in. Cancelling and rejoining must invalidate it.",
            status = EventStatus.OPEN, startAt = now + 20 * minute, endAt = now + 60 * minute,
            registrationDeadline = now + 15 * minute)
        val events = listOf(ended, host, rejoin)
        val registrations = events.flatMap { event ->
            listOf("alice", "bob", "carol").mapIndexed { index, user ->
                Registration("${event.id}-$user", event.id, user, RegistrationStatus.CONFIRMED,
                    now - 90 * minute, now - 90 * minute, index + 1L)
            }
        }
        val arrivals = registrations.filter { it.eventId != host.id && it.userId != "carol" }.map {
            CheckIn("${it.id}-arrival", it.eventId, it.userId,
                if (it.eventId == rejoin.id) now else now - 55 * minute,
                if (it.userId == "alice") CheckInStatus.HOST_CONFIRMED else CheckInStatus.QR_RECORDED,
                it.attendanceKey)
        }
        disk.save(saved.copy(currentUserId = "alice", events = saved.events + events,
            registrations = saved.registrations + registrations, checkIns = saved.checkIns + arrivals))
        File(context.filesDir, "phase1-manual-fixtures.txt")
            .writeText(events.joinToString("\n") { "${it.id} | ${it.title}" })
    }
}

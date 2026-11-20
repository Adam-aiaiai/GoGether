package com.gogether.app

import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class CoordinationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = 1_800_000_000_000L
    // Exercise A's actual Android AtomicFile/JSON persistence with D's original invariants.
    private fun repo(store: SnapshotStore? = null) = FakeGogetherRepository(store?.load() ?: DemoData.create(now), { now }, store)

    @Test fun announcementAndReadStatusSurviveRepositoryRecreation() = runBlocking {
        val store = FileSnapshotStore(File(temporary.root, "save.json"))
        val first = repo(store)
        first.login("alice").getOrThrow()
        first.postAnnouncement("event-1", "Meet at the east gate").getOrThrow()
        first.login("bob").getOrThrow()
        val notification = first.state.value.notifications.single { it.userId == "bob" }
        first.markRead(notification.id).getOrThrow()
        val reopened = repo(store).state.value
        assertEquals(first.state.value, reopened)
        assertTrue(reopened.notifications.single { it.id == notification.id }.isRead)
        assertFalse(reopened.notifications.first { it.userId != "bob" }.isRead)
        assertEquals("Meet at the east gate", reopened.announcements.single().content)
    }

    @Test fun meetingUpdatePersistsWithoutChangingEventSchedule() = runBlocking {
        val store = FileSnapshotStore(File(temporary.root, "save.json"))
        val repo = repo(store); repo.login("alice").getOrThrow()
        val before = repo.state.value.event("event-1")!!
        repo.updateMeeting("event-1", MeetingUpdate(before.startAt - 60_000, "East entrance", "Bring water")).getOrThrow()
        val after = repo(store).state.value.event("event-1")!!
        assertEquals(before.startAt, after.startAt)
        assertEquals(before.registrationDeadline, after.registrationDeadline)
        assertEquals("East entrance", after.meetingLocation)
        assertEquals(before.startAt - 60_000, after.meetingAt)
        assertEquals(5, repo.state.value.notifications.count { it.type == NotificationType.MEETING_UPDATED })
    }

    @Test fun participantCannotPublishOrEditAndInvalidInputDoesNotMutate() = runBlocking {
        val repo = repo(); repo.login("bob").getOrThrow()
        val before = repo.state.value
        assertTrue(repo.postAnnouncement("event-1", "hello").isFailure)
        assertTrue(repo.updateMeeting("event-1", MeetingUpdate(now+100, "East gate", "")).isFailure)
        assertEquals(before, repo.state.value)
        repo.login("alice").getOrThrow()
        val organizer = repo.state.value
        assertTrue(repo.postAnnouncement("event-1", " ").isFailure)
        assertTrue(repo.updateMeeting("event-1", MeetingUpdate(now-1, "East gate", "")).isFailure)
        assertEquals(organizer, repo.state.value)
    }

    @Test fun notificationCannotBeMarkedByAnotherUser() = runBlocking {
        val repo=repo(); repo.login("alice").getOrThrow(); repo.postAnnouncement("event-1", "hello").getOrThrow()
        val bob = repo.state.value.notifications.single { it.userId == "bob" }
        assertTrue(repo.markRead(bob.id).isFailure)
        assertFalse(repo.state.value.notifications.single { it.id == bob.id }.isRead)
    }

    @Test fun failedDiskWriteDoesNotPublishOrLeakIntoNextOperation() = runBlocking {
        var fail = false
        val store=object: SnapshotStore {
            override fun load(): AppSnapshot? = null
            override fun save(snapshot: AppSnapshot) { if (fail) throw IOException("Disk full") }
        }
        val repo=repo(store); repo.login("alice").getOrThrow(); val before=repo.state.value
        fail=true; assertTrue(repo.postAnnouncement("event-1", "lost").isFailure)
        assertEquals(before, repo.state.value)
        fail=false; repo.login("bob").getOrThrow()
        assertTrue(repo.state.value.announcements.isEmpty())
        assertTrue(repo.state.value.notifications.isEmpty())
    }

    @Test fun joinedAndWaitlistedChangesPersistAcrossRestart() = runBlocking {
        val store=FileSnapshotStore(File(temporary.root, "save.json")); val repo=repo(store)
        repo.login("bob").getOrThrow(); repo.cancelRegistration("event-1").getOrThrow()
        val after=repo(store).state.value
        assertEquals(RegistrationStatus.CANCELLED, after.registration("event-1", "bob")?.status)
        assertEquals(RegistrationStatus.CONFIRMED, after.registration("event-1", "emma")?.status)
        assertEquals("emma", after.notifications.single().userId)
    }

    @Test fun corruptedSaveIsNotSilentlyReplacedWithDemoData() {
        val file=File(temporary.root,"save.json"); file.writeText("not valid json")
        assertThrows(Exception::class.java) { repo(FileSnapshotStore(file)) }
        assertEquals("not valid json",file.readText())
    }

    @Test fun cancellationDuringSaveKeepsDiskAndPublishedStateConsistent() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        var block = false
        var persisted: AppSnapshot? = null
        val store = object: SnapshotStore {
            override fun load(): AppSnapshot? = null
            override fun save(snapshot: AppSnapshot) {
                if (block) { entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
                persisted = snapshot
            }
        }
        val repo = repo(store); repo.login("alice").getOrThrow(); block = true
        val job = launch(Dispatchers.Default) { repo.postAnnouncement("event-1", "Keep this saved update") }
        try {
            withContext(Dispatchers.IO) { assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
            job.cancel()
        } finally { release.countDown() }
        job.join()
        assertEquals(persisted, repo.state.value)
        assertEquals("Keep this saved update", repo.state.value.announcements.single().content)
    }

    @Test fun markAllReadOnlyTouchesCurrentUserAndPersists() = runBlocking {
        val store=FileSnapshotStore(File(temporary.root,"all-read.json")); val repo=repo(store)
        repo.login("alice").getOrThrow()
        repo.postAnnouncement("event-1", "First update").getOrThrow()
        repo.postAnnouncement("event-1", "Second update").getOrThrow()
        repo.login("bob").getOrThrow(); repo.markAllRead().getOrThrow()
        val saved=repo(store).state.value
        assertTrue(saved.notifications.filter { it.userId == "bob" }.all { it.isRead })
        assertTrue(saved.notifications.filter { it.userId != "bob" }.all { !it.isRead })
        repo.logout().getOrThrow(); assertTrue(repo.markAllRead().isFailure)
    }

    @Test fun upcomingSortsNearestFirstAndHistoryContainsEndedAndCancelled() {
        val seed=DemoData.create(now).copy(currentUserId="alice")
        val events=seed.events.map { if(it.id=="event-3") it.copy(status=EventStatus.CANCELLED) else it }
        val snapshot=seed.copy(events=events)
        val upcoming=com.gogether.app.feature.profile.personalEvents(snapshot,com.gogether.app.feature.profile.PersonalEventTab.JOINED,now)
        assertEquals(upcoming.map { it.startAt }.sorted(),upcoming.map { it.startAt })
        assertFalse(upcoming.any { it.id=="event-3" || it.id=="event-history" })
        val history=com.gogether.app.feature.profile.personalEvents(snapshot,com.gogether.app.feature.profile.PersonalEventTab.HISTORY,now)
        assertTrue(history.any { it.id=="event-3" }); assertTrue(history.any { it.id=="event-history" })
        assertEquals(history.map { it.startAt }.sortedDescending(),history.map { it.startAt })
        assertTrue(com.gogether.app.feature.profile.personalEvents(snapshot.copy(currentUserId=null),com.gogether.app.feature.profile.PersonalEventTab.CREATED,now).isEmpty())
        assertTrue(com.gogether.app.feature.profile.personalEvents(snapshot,com.gogether.app.feature.profile.PersonalEventTab.CREATED,now).any { it.id=="event-history" })
    }
}

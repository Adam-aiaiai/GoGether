package com.gogether.app

import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class QuestionsAndChangesTest {
    private val now = 1_800_000_000_000L
    private fun repo() = FakeGogetherRepository(DemoData.create(now), { now })
    private fun Event.draft() = EventDraft(title, category, description, startAt, location, city, capacity,
        registrationDeadline, notes, endAt, experienceRequirement, beginnerGuidance, requiredItems, spareEquipment, imageUri)

    @Test fun prospectiveParticipantAsksAndOnlyOrganizerReceivesQuestion() = runTest {
        val repo = repo(); repo.login("finn").getOrThrow()
        assertNull(repo.state.value.registration("event-1"))
        repo.askQuestion("event-1", "  Can I borrow a racket?  ").getOrThrow()
        val question = repo.state.value.questions.single()
        assertEquals("Can I borrow a racket?", question.content)
        assertEquals("finn", question.authorId)
        assertEquals(now, question.createdAt)
        val notice = repo.state.value.notifications.single()
        assertEquals("alice", notice.userId)
        assertEquals(question.id, notice.questionId)
        assertEquals(NotificationType.QUESTION_ASKED, notice.type)
        assertTrue(repo.state.value.announcements.isEmpty())
    }

    @Test fun onlyEventOrganizerCanAnswerAndAuthorReceivesReply() = runTest {
        val repo = repo(); repo.login("finn").getOrThrow()
        repo.askQuestion("event-1", "Can I join as a beginner?").getOrThrow()
        val q = repo.state.value.questions.single()
        repo.login("bob").getOrThrow()
        val before = repo.state.value
        assertTrue(repo.answerQuestion(q.id, "Not my event").isFailure)
        assertEquals(before, repo.state.value)
        repo.login("alice").getOrThrow()
        repo.answerQuestion(q.id, " Yes, beginners are welcome. ").getOrThrow()
        assertEquals("Yes, beginners are welcome.", repo.state.value.questions.single().answer)
        assertEquals("alice", repo.state.value.questions.single().answeredBy)
        assertEquals(now, repo.state.value.questions.single().answeredAt)
        val response = repo.state.value.notifications.single { it.type == NotificationType.QUESTION_ANSWERED }
        assertEquals("finn", response.userId)
        assertEquals(q.id, response.questionId)
    }

    @Test fun answeringAgainUpdatesOneAnswerAndIdenticalSaveDoesNotNotify() = runTest {
        val repo = repo(); repo.login("finn").getOrThrow(); repo.askQuestion("event-1", "Equipment?").getOrThrow()
        val id = repo.state.value.questions.single().id
        repo.login("alice").getOrThrow(); repo.answerQuestion(id, "Bring a racket").getOrThrow()
        repo.answerQuestion(id, "Spare rackets are available").getOrThrow()
        val before = repo.state.value
        repo.answerQuestion(id, " Spare rackets are available ").getOrThrow()
        assertEquals(before, repo.state.value)
        assertEquals("Spare rackets are available", repo.state.value.questions.single().answer)
        assertEquals(2, repo.state.value.notifications.count { it.type == NotificationType.QUESTION_ANSWERED })
    }

    @Test fun blankOversizedDuplicateAndMissingTargetsDoNotMutate() = runTest {
        val repo = repo(); repo.login("finn").getOrThrow()
        val initial = repo.state.value
        for (content in listOf("  ", "x".repeat(101))) assertTrue(repo.askQuestion("event-1", content).isFailure)
        assertTrue(repo.askQuestion("missing", "Question").isFailure)
        assertEquals(initial, repo.state.value)
        repo.askQuestion("event-1", "Question").getOrThrow()
        val before = repo.state.value
        assertTrue(repo.askQuestion("event-1", " Question ").isFailure)
        assertEquals(before, repo.state.value)
        repo.login("alice").getOrThrow()
        val organizer = repo.state.value
        assertTrue(repo.askQuestion("event-1", "My own event").isFailure)
        assertTrue(repo.answerQuestion("missing", "Answer").isFailure)
        val id = repo.state.value.questions.single().id
        for (content in listOf(" ", "x".repeat(101))) assertTrue(repo.answerQuestion(id, content).isFailure)
        assertEquals(organizer, repo.state.value)
    }

    @Test fun closedEventRetainsHistoryButRejectsQuestionsAndReplies() = runTest {
        var clock = now
        val repo = FakeGogetherRepository(DemoData.create(now), { clock })
        repo.login("finn").getOrThrow(); repo.askQuestion("event-1", "Question").getOrThrow()
        val id = repo.state.value.questions.single().id
        clock = repo.state.value.event("event-1")!!.endAt
        assertTrue(repo.askQuestion("event-1", "Another question").isFailure)
        repo.login("alice").getOrThrow()
        assertTrue(repo.answerQuestion(id, "Too late").isFailure)
        assertEquals(1, repo.state.value.questions.size)
        clock = now
        repo.cancelEvent("event-1").getOrThrow()
        assertTrue(repo.answerQuestion(id, "Cancelled").isFailure)
        repo.login("finn").getOrThrow()
        assertTrue(repo.askQuestion("event-1", "New question").isFailure)
    }

    @Test fun loggedOutRequestsCannotCreateQuestionsOrAnswers() = runTest {
        val repo = repo()
        assertTrue(repo.askQuestion("event-1", "Question").isFailure)
        assertTrue(repo.answerQuestion("missing", "Answer").isFailure)
        assertTrue(repo.state.value.questions.isEmpty())
    }

    @Test fun failedPersistenceRollsBackQuestionReplyAndTheirNotifications() = runTest {
        var fail = false
        var stored = DemoData.create(now)
        val store = object : SnapshotStore {
            override fun load() = stored
            override fun save(snapshot: AppSnapshot) { if (fail) throw IOException("Disk full"); stored = snapshot }
        }
        val repo = FakeGogetherRepository(stored, { now }, store)
        repo.login("finn").getOrThrow(); val before = repo.state.value; fail = true
        assertTrue(repo.askQuestion("event-1", "Equipment?").isFailure)
        assertEquals(before, repo.state.value); assertEquals(before, stored)
        fail = false; repo.askQuestion("event-1", "Equipment?").getOrThrow()
        repo.login("alice").getOrThrow(); val asked = repo.state.value; fail = true
        assertTrue(repo.answerQuestion(asked.questions.single().id, "Bring a racket").isFailure)
        assertEquals(asked, repo.state.value); assertEquals(asked, stored)
    }

    @Test fun startEndAndLocationChangeNotifiesActiveParticipantsOnceEach() = runTest {
        val repo = repo(); repo.login("alice").getOrThrow()
        val e = repo.state.value.event("event-1")!!
        assertNull(e.meetingAt)
        repo.updateEvent(e.id, e.draft().copy(startAt = e.startAt + 60_000, endAt = e.endAt + 60_000,
            location = "New gym entrance", city = "Shanghai")).getOrThrow()
        val notices = repo.state.value.notifications.filter { it.type == NotificationType.EVENT_UPDATED }
        assertEquals(setOf("alice", "bob", "carol", "david", "emma"), notices.map { it.userId }.toSet())
        assertEquals(5, notices.size)
        assertTrue(notices.all { "Starts:" in it.content && "Ends:" in it.content && "Shanghai" in it.content && "New gym entrance" in it.content })
        assertTrue(notices.none { it.userId == "finn" })
    }

    @Test fun cancelledRegistrationDoesNotReceiveLaterActivityChanges() = runTest {
        val repo = repo(); repo.login("emma").getOrThrow(); repo.cancelRegistration("event-1").getOrThrow()
        repo.login("alice").getOrThrow(); val e = repo.state.value.event("event-1")!!
        repo.updateEvent(e.id, e.draft().copy(location = "New entrance")).getOrThrow()
        assertTrue(repo.state.value.notifications.none { it.userId == "emma" })
        assertEquals(4, repo.state.value.notifications.size)
    }

    @Test fun unchangedSaveAndWhitespaceNormalizationDoNotSpamInbox() = runTest {
        val repo = repo(); repo.login("alice").getOrThrow(); val e = repo.state.value.event("event-1")!!
        repo.updateEvent(e.id, e.draft()).getOrThrow()
        repo.updateEvent(e.id, e.draft().copy(title = " ${e.title} ", location = " ${e.location} ")).getOrThrow()
        assertTrue(repo.state.value.notifications.isEmpty())
    }

    @Test fun explicitMeetingAndActivityChangesShareOneNotificationPerRecipient() = runTest {
        val repo = repo(); repo.login("alice").getOrThrow(); val e = repo.state.value.event("event-1")!!
        repo.updateEvent(e.id, e.draft().copy(location = "New gym", meetingAt = e.startAt - 60_000)).getOrThrow()
        val notices = repo.state.value.notifications
        assertEquals(5, notices.size)
        assertTrue(notices.all { it.type == NotificationType.MEETING_UPDATED && "Meeting time:" in it.content && "Location:" in it.content })
    }

    @Test fun deadlineEquipmentAndDescriptionChangesAreIncluded() = runTest {
        val repo = repo(); repo.login("alice").getOrThrow(); val e = repo.state.value.event("event-1")!!
        repo.updateEvent(e.id, e.draft().copy(registrationDeadline = e.registrationDeadline - 60_000,
            requiredItems = "Water bottle", description = "Updated event description")).getOrThrow()
        assertEquals(5, repo.state.value.notifications.size)
        assertTrue(repo.state.value.notifications.all { "Registration closes:" in it.content && "equipment" in it.content && "Description" in it.content })
    }

    @Test fun failedActivitySaveDoesNotPublishChangeOrNotifications() = runTest {
        var fail = false
        val repo = FakeGogetherRepository(DemoData.create(now), { now }, save = { if (fail) throw IOException("Disk full") })
        repo.login("alice").getOrThrow(); val before = repo.state.value; fail = true
        val e = before.event("event-1")!!
        assertTrue(repo.updateEvent(e.id, e.draft().copy(location = "New entrance")).isFailure)
        assertEquals(before, repo.state.value)
    }
}

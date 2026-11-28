package com.gogether.app

import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class QuestionsPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun questionReplyAndReadStateSurviveFileReopen(): Unit = runBlocking {
        val store = FileSnapshotStore(File(temporary.root, "snapshot.json"))
        val repo = FakeGogetherRepository(store = store)
        repo.login("finn").getOrThrow(); repo.askQuestion("event-1", "Can I borrow equipment?").getOrThrow()
        val id = repo.state.value.questions.single().id
        repo.login("alice").getOrThrow(); repo.answerQuestion(id, "Yes, spare rackets are available.").getOrThrow()
        val event = repo.state.value.event("event-1")!!
        repo.updateEvent(event.id, EventDraft(event.title, event.category, event.description, event.startAt,
            "New gym entrance", event.city, event.capacity, event.registrationDeadline, event.notes, event.endAt,
            event.experienceRequirement, event.beginnerGuidance, event.requiredItems, event.spareEquipment, event.imageUri)).getOrThrow()
        repo.login("finn").getOrThrow(); repo.markAllRead().getOrThrow()
        val reopened = FakeGogetherRepository(store.load()!!, store = store)
        assertEquals(repo.state.value, reopened.state.value)
        assertTrue(reopened.state.value.notifications.single { it.type == NotificationType.QUESTION_ANSWERED }.isRead)
        assertEquals(id, reopened.state.value.notifications.single { it.type == NotificationType.QUESTION_ANSWERED }.questionId)
        assertTrue(reopened.state.value.notifications.any { it.type == NotificationType.EVENT_UPDATED })
        assertEquals(repo.state.value, LocalSnapshotStore.decode(LocalSnapshotStore.encode(repo.state.value)).copy(currentUserId = "finn"))
    }

    @Test fun previousVersionTwoRetainsDataAndStartsWithEmptyQuestions() {
        val original = DemoData.create().copy(notifications = listOf(Notification("old", "bob", "event-1",
            NotificationType.ANNOUNCEMENT, "Old notice", 100, true)))
        val json = JSONObject(SnapshotJson.encode(original))
        json.put("version", 2); json.remove("questions")
        json.getJSONArray("notifications").getJSONObject(0).remove("questionId")
        assertEquals(original, SnapshotJson.decode(json.toString()))
        assertTrue(SnapshotJson.decode(json.toString()).questions.isEmpty())
    }
    @Test fun multipleLegacyAnswersRetainHistoryAcrossSnapshotReaders(): Unit = runBlocking {
        val repo=FakeGogetherRepository()
        repo.login("bob").getOrThrow();repo.askQuestion("event-1","Will there be spare equipment?").getOrThrow()
        val id=repo.state.value.questions.single().id
        repo.login("alice").getOrThrow();repo.answerQuestion(id,"Yes, bring your own if possible.").getOrThrow()
        repo.answerQuestion(id,"We also have two spare rackets.").getOrThrow()
        val original=repo.state.value
        val fileRoundTrip=SnapshotJson.decode(SnapshotJson.encode(original))
        val prefsRoundTrip=LocalSnapshotStore.decode(LocalSnapshotStore.encode(original))
        assertEquals(2,fileRoundTrip.questions.single().replyHistory.size)
        assertEquals(fileRoundTrip.questions,prefsRoundTrip.questions)
        assertEquals(original.questions.single().replyHistory.map {it.id},fileRoundTrip.questions.single().replyHistory.map {it.id})
    }

}

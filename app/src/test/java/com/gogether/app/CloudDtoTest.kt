package com.gogether.app

import com.gogether.app.core.data.cloud.CloudDto
import com.gogether.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class CloudDtoTest {
    private fun own() = mapOf<String, Any?>("id" to "account-a", "displayName" to "Alice", "interests" to listOf("SPORTS"), "createdAt" to 1234.0, "email" to "a@example.test")
    @Test fun numericMillisecondsAndMissingOptionalCollectionsDecode() {
        val decoded = CloudDto.snapshot(mapOf("currentUserId" to "account-a", "users" to listOf(own())), "account-a")
        assertEquals(1234L, decoded.currentUser!!.createdAt)
        assertEquals(UserStatus.ACCOUNT, decoded.currentUser!!.status)
        assertTrue(decoded.questions.isEmpty())
        assertTrue(decoded.events.isEmpty())
    }
    @Test fun differentAccountResponseIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CloudDto.snapshot(mapOf("currentUserId" to "other", "users" to listOf(own())), "account-a")
        }
    }
    @Test fun missingRequiredFieldsAreNotSilentlyTurnedIntoDemoValues() {
        assertThrows(IllegalStateException::class.java) { CloudDto.user(mapOf("id" to "x")) }
        assertThrows(IllegalStateException::class.java) { CloudDto.snapshot(mapOf("currentUserId" to "account-a", "users" to "invalid"), "account-a") }
    }
    @Test fun anotherUsersNotificationIsRejected() {
        val notice = mapOf("id" to "n", "eventId" to "e", "userId" to "other", "type" to "ANNOUNCEMENT", "content" to "Private", "createdAt" to 10L)
        assertThrows(IllegalArgumentException::class.java) {
            CloudDto.snapshot(mapOf("currentUserId" to "account-a", "users" to listOf(own()), "notifications" to listOf(notice)), "account-a")
        }
    }
    @Test fun publicProfileDoesNotRequireEmail() {
        val public = own() - "email"
        assertNull(CloudDto.user(public).email)
    }
    @Test fun eventDraftKeepsAllMembersFields() {
        val draft = EventDraft("Friendly match", Category.SPORTS, "A friendly event", 100_000, "Sports centre", "Suzhou", 5,
            90_000, "Bring water", 200_000, ExperienceRequirement.BASIC, true, "Racket", "Two spares", "covers/u/image",
            95_000, "East gate", "Look for the organizer")
        val encoded = CloudDto.draft(draft)
        assertEquals("BASIC", encoded["experienceRequirement"])
        assertEquals(95_000L, encoded["meetingAt"])
        assertEquals("Two spares", encoded["spareEquipment"])
        assertEquals("covers/u/image", encoded["imageUri"])
    }
    @Test fun legacyAnswerAndNewReplyHistorySurviveCloudSnapshotDecoding() {
        val first = mapOf("id" to "answer-q", "authorId" to "host", "content" to "First", "createdAt" to 2L)
        val second = mapOf("id" to "r2", "authorId" to "host", "content" to "Second", "createdAt" to 3.0)
        val question = mapOf("id" to "q", "eventId" to "e", "authorId" to "account-a", "content" to "Question", "createdAt" to 1L,
            "answer" to "Second", "answeredBy" to "host", "answeredAt" to 3L, "replyHistory" to listOf(first, second))
        fun decode(q: Map<String, Any>) = CloudDto.snapshot(mapOf("currentUserId" to "account-a", "users" to listOf(own()), "questions" to listOf(q)), "account-a").questions.single()
        assertEquals(listOf(QuestionReply("answer-q", "host", "First", 2), QuestionReply("r2", "host", "Second", 3)), decode(question).replyHistory)
        assertEquals("Second", decode(question).answer)
        assertTrue(decode(question - "replyHistory").replyHistory.isEmpty())
        assertEquals("Second", decode(question - "replyHistory").answer)
    }

}

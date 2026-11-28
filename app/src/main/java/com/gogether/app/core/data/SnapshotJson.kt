package com.gogether.app.core.data

import com.gogether.app.core.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned local demo snapshot. Contains no passwords or authentication tokens. */
object SnapshotJson {
    const val CURRENT_VERSION = 7
    private fun obj(vararg fields: Pair<String, Any?>) = JSONObject().apply {
        fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }
    private fun <T> list(values: List<T>, encode: (T) -> JSONObject) = JSONArray().apply { values.forEach { put(encode(it)) } }
    private fun JSONObject.text(key: String) = optString(key, "")
    private fun JSONObject.nullable(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    private fun <T> JSONObject.items(key: String, decode: (JSONObject) -> T): List<T> {
        val array = getJSONArray(key)
        return (0 until array.length()).map { decode(array.getJSONObject(it)) }
    }

    fun encode(s: AppSnapshot): String = obj(
        "version" to CURRENT_VERSION, "currentUserId" to s.currentUserId,
        "users" to list(s.users) { u -> obj("id" to u.id, "name" to u.displayName,
            "interests" to JSONArray(u.interests.map { it.name }), "createdAt" to u.createdAt,
            "status" to u.status.name, "email" to u.email, "avatarUri" to u.avatarUri,
            "coverUri" to u.coverUri, "gender" to u.gender.name, "showGender" to u.showGender,
            "signature" to u.signature) },
        "events" to list(s.events) { e -> obj("id" to e.id, "organizerId" to e.organizerId,
            "title" to e.title, "category" to e.category.name, "description" to e.description,
            "startAt" to e.startAt, "endAt" to e.endAt, "location" to e.location, "city" to e.city,
            "capacity" to e.capacity, "deadline" to e.registrationDeadline, "notes" to e.notes,
            "createdAt" to e.createdAt, "updatedAt" to e.updatedAt, "status" to e.status.name,
            "checkInOpen" to e.demoCheckInOpen, "experience" to e.experienceRequirement.name,
            "guidance" to e.beginnerGuidance, "requiredItems" to e.requiredItems,
            "spareEquipment" to e.spareEquipment, "imageUri" to e.imageUri,
            "meetingAt" to e.meetingAt, "meetingLocation" to e.meetingLocation, "meetingNotes" to e.meetingNotes) },
        "registrations" to list(s.registrations) { r -> obj("id" to r.id, "eventId" to r.eventId,
            "userId" to r.userId, "status" to r.status.name, "joinedAt" to r.joinedAt,
            "updatedAt" to r.updatedAt, "queueOrder" to r.queueOrder) },
        "checkIns" to list(s.checkIns) { c -> obj("id" to c.id, "eventId" to c.eventId,
            "userId" to c.userId, "at" to c.checkedInAt, "status" to c.status.name,
            "registrationKey" to c.registrationKey) },
        "notifications" to list(s.notifications) { n -> obj("id" to n.id, "userId" to n.userId,
            "eventId" to n.eventId, "type" to n.type.name, "content" to n.content,
            "createdAt" to n.createdAt, "isRead" to n.isRead, "announcementId" to n.announcementId, "questionId" to n.questionId,
            "contentId" to n.contentId, "rootId" to n.rootId, "actorName" to n.actorName, "actorAvatarUri" to n.actorAvatarUri) },
        "announcements" to list(s.announcements) { a -> obj("id" to a.id, "eventId" to a.eventId,
            "authorId" to a.authorId, "content" to a.content, "createdAt" to a.createdAt) },
        "questions" to list(s.questions) { q -> obj("id" to q.id, "eventId" to q.eventId,
            "authorId" to q.authorId, "content" to q.content, "createdAt" to q.createdAt,
            "answer" to q.answer, "answeredBy" to q.answeredBy, "answeredAt" to q.answeredAt,
            "replyHistory" to list(q.replyHistory) { r -> obj("id" to r.id,"authorId" to r.authorId,"content" to r.content,"createdAt" to r.createdAt) }) },
        "eventRatings" to list(s.eventRatings.entries.toList()) { (eventId,summary) -> obj("eventId" to eventId,"ratingSum" to summary.ratingSum,"reviewCount" to summary.reviewCount) },
        "feedback" to list(s.feedback) { f -> obj("id" to f.id, "eventId" to f.eventId,
            "userId" to f.userId, "rating" to f.rating, "comment" to f.comment,
            "createdAt" to f.createdAt, "status" to f.status.name) },
    ).toString()

    fun decode(text: String): AppSnapshot {
        val root = JSONObject(text)
        require(root.getInt("version") in 1..CURRENT_VERSION) { "This saved data version is not supported." }
        val users = root.items("users") { u ->
            val interests = u.getJSONArray("interests")
            User(u.getString("id"), u.getString("name"), (0 until interests.length()).map {
                Category.valueOf(interests.getString(it))
            }.toSet(), u.getLong("createdAt"), UserStatus.valueOf(u.getString("status")), u.nullable("email"),
                u.text("avatarUri"), u.text("coverUri"),
                Gender.entries.find { it.name == u.text("gender") } ?: Gender.UNDISCLOSED,
                u.optBoolean("showGender", false), u.text("signature"))
        }
        return AppSnapshot(users = users,
            currentUserId = root.nullable("currentUserId")?.takeIf { id -> users.any { it.id == id } },
            events = root.items("events") { e -> Event(
                id = e.getString("id"), organizerId = e.getString("organizerId"), title = e.getString("title"),
                category = Category.valueOf(e.getString("category")), description = e.getString("description"),
                startAt = e.getLong("startAt"), endAt = e.getLong("endAt"), location = e.getString("location"),
                city = e.getString("city"), capacity = e.getInt("capacity"), registrationDeadline = e.getLong("deadline"),
                notes = e.text("notes"), createdAt = e.getLong("createdAt"), updatedAt = e.getLong("updatedAt"),
                status = EventStatus.valueOf(e.getString("status")), demoCheckInOpen = e.optBoolean("checkInOpen"),
                experienceRequirement = ExperienceRequirement.valueOf(e.getString("experience")),
                beginnerGuidance = e.optBoolean("guidance"), requiredItems = e.text("requiredItems"),
                spareEquipment = e.text("spareEquipment"), imageUri = e.text("imageUri"),
                meetingAt = if (e.has("meetingAt") && !e.isNull("meetingAt")) e.getLong("meetingAt") else null,
                meetingLocation = e.nullable("meetingLocation"), meetingNotes = e.text("meetingNotes")) },
            registrations = root.items("registrations") { r -> Registration(r.getString("id"), r.getString("eventId"),
                r.getString("userId"), RegistrationStatus.valueOf(r.getString("status")), r.getLong("joinedAt"),
                r.getLong("updatedAt"), r.getLong("queueOrder")) },
            checkIns = root.items("checkIns") { c -> CheckIn(c.getString("id"), c.getString("eventId"),
                c.getString("userId"), c.getLong("at"), CheckInStatus.valueOf(c.getString("status")), c.nullable("registrationKey")) },
            notifications = root.items("notifications") { n -> Notification(n.getString("id"), n.getString("userId"),
                n.getString("eventId"), NotificationType.valueOf(n.getString("type")), n.getString("content"),
                n.getLong("createdAt"), n.getBoolean("isRead"), n.nullable("announcementId"), n.nullable("questionId"), n.nullable("contentId"), n.nullable("rootId"), n.nullable("actorName"), n.text("actorAvatarUri")) },
            announcements = if (root.has("announcements")) root.items("announcements") { a ->
                Announcement(a.getString("id"), a.getString("eventId"), a.getString("authorId"),
                    a.getString("content"), a.getLong("createdAt"))
            } else emptyList(),
            questions = if (root.has("questions")) root.items("questions") { q ->
                EventQuestion(q.getString("id"), q.getString("eventId"), q.getString("authorId"),
                    q.getString("content"), q.getLong("createdAt"), q.nullable("answer"), q.nullable("answeredBy"),
                    if (q.has("answeredAt") && !q.isNull("answeredAt")) q.getLong("answeredAt") else null,
                    if(q.has("replyHistory")) q.items("replyHistory") { r -> QuestionReply(r.getString("id"),r.getString("authorId"),r.getString("content"),r.getLong("createdAt")) } else emptyList())
            } else emptyList(),
            eventRatings = if(root.has("eventRatings")) root.items("eventRatings") { r -> r.getString("eventId") to EventRatingSummary(r.getInt("ratingSum"),r.getInt("reviewCount")) }.toMap() else emptyMap(),
            feedback = root.items("feedback") { f -> Feedback(f.getString("id"), f.getString("eventId"),
                f.getString("userId"), f.getInt("rating"), f.getString("comment"), f.getLong("createdAt"),
                FeedbackStatus.valueOf(f.getString("status"))) })
    }
}

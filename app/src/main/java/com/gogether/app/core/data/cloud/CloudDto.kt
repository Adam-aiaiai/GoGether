package com.gogether.app.core.data.cloud

import com.gogether.app.core.model.*

/** Explicit JSON DTO conversion: numeric milliseconds, enums and absent optional fields stay predictable. */
object CloudDto {
    @Suppress("UNCHECKED_CAST")
    fun objectValue(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: error("Invalid server response.")
    private fun Map<String, Any?>.text(key: String, default: String? = null): String =
        this[key] as? String ?: default ?: error("Missing server field: $key")
    private fun Map<String, Any?>.number(key: String, default: Long? = null): Long =
        (this[key] as? Number)?.toLong() ?: default ?: error("Missing server field: $key")
    private fun Map<String, Any?>.flag(key: String) = this[key] as? Boolean ?: false
    private fun Map<String, Any?>.optionalText(key: String) = this[key] as? String
    private fun Map<String, Any?>.rows(key: String): List<Map<String, Any?>> = when (val value = this[key]) {
        null -> emptyList()
        is List<*> -> value.map(::objectValue)
        else -> error("Invalid server collection: $key")
    }
    fun user(v: Map<String, Any?>) = User(v.text("id"), v.text("displayName"),
        (v["interests"] as? List<*>)?.map { Category.valueOf(it as String) }?.toSet() ?: emptySet(),
        v.number("createdAt"), UserStatus.ACCOUNT, v.optionalText("email"))
    fun event(v: Map<String, Any?>) = Event(v.text("id"), v.text("organizerId"), v.text("title"),
        Category.valueOf(v.text("category")), v.text("description"), v.number("startAt"), v.number("endAt"),
        v.text("location"), v.text("city"), v.number("capacity").toInt(), v.number("registrationDeadline"),
        v.text("notes", ""), v.number("createdAt"), v.number("updatedAt"), EventStatus.valueOf(v.text("status", "OPEN")),
        false, ExperienceRequirement.valueOf(v.text("experienceRequirement", "NONE")), v.flag("beginnerGuidance"),
        v.text("requiredItems", ""), v.text("spareEquipment", ""), v.text("imageUri", ""),
        (v["meetingAt"] as? Number)?.toLong(), v.optionalText("meetingLocation"), v.text("meetingNotes", ""))
    fun registration(v: Map<String, Any?>) = Registration(v.text("id"), v.text("eventId"), v.text("userId"),
        RegistrationStatus.valueOf(v.text("status")), v.number("joinedAt"), v.number("updatedAt"), v.number("queueOrder"))
    fun feedback(v: Map<String, Any?>) = Feedback(v.text("id"), v.text("eventId"), v.text("userId"),
        v.number("rating").toInt(), v.text("comment", ""), v.number("createdAt"))
    fun snapshot(v: Map<String, Any?>, uid: String): AppSnapshot {
        require(v.text("currentUserId") == uid) { "The server returned a different account. Sign in again." }
        val result = AppSnapshot(users = v.rows("users").map(::user), currentUserId = uid,
            events = v.rows("events").map(::event), registrations = v.rows("registrations").map(::registration),
            checkIns = v.rows("checkIns").map { CheckIn(it.text("id"), it.text("eventId"), it.text("userId"),
                it.number("checkedInAt"), CheckInStatus.valueOf(it.text("status", "QR_RECORDED"))) },
            notifications = v.rows("notifications").map { Notification(it.text("id"), it.text("userId"), it.text("eventId"),
                NotificationType.valueOf(it.text("type")), it.text("content"), it.number("createdAt"), it.flag("isRead"),
                it.optionalText("announcementId"), it.optionalText("questionId")) },
            feedback = v.rows("feedback").map(::feedback),
            announcements = v.rows("announcements").map { Announcement(it.text("id"), it.text("eventId"), it.text("authorId"),
                it.text("content"), it.number("createdAt")) },
            questions = v.rows("questions").map { EventQuestion(it.text("id"), it.text("eventId"), it.text("authorId"),
                it.text("content"), it.number("createdAt"), it.optionalText("answer"), it.optionalText("answeredBy"),
                (it["answeredAt"] as? Number)?.toLong()) })
        require(result.currentUser != null) { "Your account profile is unavailable. Retry the connection." }
        require(result.notifications.all { it.userId == uid }) { "Unexpected notification ownership." }
        return result
    }
    fun draft(v: EventDraft): Map<String, Any?> = mapOf("title" to v.title, "category" to v.category.name,
        "description" to v.description, "startAt" to v.startAt, "endAt" to v.endAt, "location" to v.location,
        "city" to v.city, "capacity" to v.capacity, "registrationDeadline" to v.registrationDeadline, "notes" to v.notes,
        "experienceRequirement" to v.experienceRequirement.name, "beginnerGuidance" to v.beginnerGuidance,
        "requiredItems" to v.requiredItems, "spareEquipment" to v.spareEquipment, "imageUri" to v.imageUri,
        "meetingAt" to v.meetingAt, "meetingLocation" to v.meetingLocation, "meetingNotes" to v.meetingNotes)
}

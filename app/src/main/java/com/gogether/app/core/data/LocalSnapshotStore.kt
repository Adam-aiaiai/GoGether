package com.gogether.app.core.data

import android.content.Context
import com.gogether.app.core.model.*
import org.json.JSONArray
import org.json.JSONObject

/** One local C snapshot. This is persistence, not a server or multi-device synchronization.
 * A single checked write keeps registration, promotion and notification in one transaction.
 * The Demo login is deliberately not restored as authenticated identity.
 */
class LocalSnapshotStore(context: Context) {
    private val preferences = context.getSharedPreferences("ce_repository_v1", Context.MODE_PRIVATE)
    fun load(): AppSnapshot? = preferences.getString("snapshot", null)?.let { decode(it).copy(currentUserId = null) }
    fun save(state: AppSnapshot) {
        check(preferences.edit().putString("snapshot", encode(state)).commit()) { "Could not save local event data. Try again." }
    }
    companion object {
        private fun obj(vararg fields: Pair<String, Any?>) = JSONObject().also { value -> fields.forEach { value.put(it.first, it.second) } }
        private fun <T> rows(values: List<T>, convert: (T) -> JSONObject) = JSONArray(values.map(convert))
        private fun <T> JSONObject.rows(key: String, convert: (JSONObject) -> T): List<T> = getJSONArray(key).let { array ->
            (0 until array.length()).map { convert(array.getJSONObject(it)) }
        }
        fun encode(s: AppSnapshot): String = obj("version" to 1,
            "users" to rows(s.users) { obj("id" to it.id, "name" to it.displayName, "created" to it.createdAt,
                "interests" to JSONArray(it.interests.map { c -> c.name })) },
            "events" to rows(s.events) { obj("id" to it.id, "owner" to it.organizerId, "title" to it.title,
                "category" to it.category.name, "description" to it.description, "start" to it.startAt, "end" to it.endAt,
                "location" to it.location, "city" to it.city, "capacity" to it.capacity, "deadline" to it.registrationDeadline,
                "notes" to it.notes, "created" to it.createdAt, "updated" to it.updatedAt, "status" to it.status.name) },
            "registrations" to rows(s.registrations) { obj("id" to it.id, "event" to it.eventId, "user" to it.userId,
                "status" to it.status.name, "joined" to it.joinedAt, "updated" to it.updatedAt, "order" to it.queueOrder) },
            "checkIns" to rows(s.checkIns) { obj("id" to it.id, "event" to it.eventId, "user" to it.userId, "time" to it.checkedInAt) },
            "notifications" to rows(s.notifications) { obj("id" to it.id, "user" to it.userId, "event" to it.eventId,
                "type" to it.type.name, "content" to it.content, "created" to it.createdAt, "read" to it.isRead) },
            "feedback" to rows(s.feedback) { obj("id" to it.id, "user" to it.userId, "event" to it.eventId,
                "rating" to it.rating, "comment" to it.comment, "created" to it.createdAt) }).toString()

        fun decode(raw: String): AppSnapshot {
            val root = JSONObject(raw)
            require(root.getInt("version") == 1) { "This local event data version is not supported." }
            return AppSnapshot(
                users = root.rows("users") { row -> User(row.getString("id"), row.getString("name"),
                    row.getJSONArray("interests").let { a -> (0 until a.length()).map { Category.valueOf(a.getString(it)) }.toSet() }, row.getLong("created")) },
                events = root.rows("events") { Event(it.getString("id"), it.getString("owner"), it.getString("title"),
                    Category.valueOf(it.getString("category")), it.getString("description"), it.getLong("start"), it.getLong("end"),
                    it.getString("location"), it.getString("city"), it.getInt("capacity"), it.getLong("deadline"), it.getString("notes"),
                    it.getLong("created"), it.getLong("updated"), EventStatus.valueOf(it.getString("status"))) },
                registrations = root.rows("registrations") { Registration(it.getString("id"), it.getString("event"), it.getString("user"),
                    RegistrationStatus.valueOf(it.getString("status")), it.getLong("joined"), it.getLong("updated"), it.getLong("order")) },
                checkIns = root.rows("checkIns") { CheckIn(it.getString("id"), it.getString("event"), it.getString("user"), it.getLong("time")) },
                notifications = root.rows("notifications") { Notification(it.getString("id"), it.getString("user"), it.getString("event"),
                    NotificationType.valueOf(it.getString("type")), it.getString("content"), it.getLong("created"), it.getBoolean("read")) },
                feedback = root.rows("feedback") { Feedback(it.getString("id"), it.getString("event"), it.getString("user"),
                    it.getInt("rating"), it.getString("comment"), it.getLong("created")) })
        }
    }
}

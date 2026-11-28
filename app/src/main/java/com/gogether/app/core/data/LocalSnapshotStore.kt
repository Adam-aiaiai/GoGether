package com.gogether.app.core.data

import android.content.Context
import com.gogether.app.core.model.*
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
        private fun <T> JSONObject.rows(key: String, convert: (JSONObject) -> T): List<T> = getJSONArray(key).let { array ->
            (0 until array.length()).map { convert(array.getJSONObject(it)) }
        }
        // New writes use the full shared schema; keep the original CE reader for upgrades.
        fun encode(s: AppSnapshot): String = SnapshotJson.encode(s.copy(currentUserId = null))

        fun decode(raw: String): AppSnapshot {
            val root = JSONObject(raw)
            require(root.getInt("version") in 1..SnapshotJson.CURRENT_VERSION) { "This local event data version is not supported." }
            if (root.getInt("version") >= 2) return SnapshotJson.decode(raw).copy(currentUserId = null)
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

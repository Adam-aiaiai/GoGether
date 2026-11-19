package com.gogether.app.feature.checkin.prototype

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/** Persist only request identity and activity binding, never QR credentials or participant location. */
class PreferencesPendingCheckInStore(context: Context) : PendingCheckInStore {
    private val prefs = context.getSharedPreferences("checkin_pending_v1", Context.MODE_PRIVATE)
    private fun key(actor: String, sessionId: String) = "${Uri.encode(actor)}:${Uri.encode(sessionId)}"
    override fun get(actor: String, sessionId: String): CheckInAttempt? = synchronized(lock) {
        prefs.getString(key(actor, sessionId), null)?.let { raw ->
            val value = JSONObject(raw)
            CheckInAttempt(value.getString("request"), actor, PrototypeSession(sessionId,
                MeetingPoint(value.getDouble("lat"), value.getDouble("lon")),
                value.getLong("start"), value.getLong("end")))
        }
    }
    override fun save(attempt: CheckInAttempt) = synchronized(lock) {
        val previous = get(attempt.actor, attempt.session.id)
        check(previous == null || previous == attempt) { "Check the result of your previous check-in first." }
        val value = JSONObject().put("request", attempt.id).put("lat", requireNotNull(attempt.session.meeting).latitude)
            .put("lon", requireNotNull(attempt.session.meeting).longitude).put("start", attempt.session.startAt).put("end", attempt.session.endAt)
        check(prefs.edit().putString(key(attempt.actor, attempt.session.id), value.toString()).commit()) {
            "Could not save check-in progress. Try again later."
        }
    }
    override fun remove(attempt: CheckInAttempt) = synchronized(lock) {
        if (get(attempt.actor, attempt.session.id) == attempt) {
            check(prefs.edit().remove(key(attempt.actor, attempt.session.id)).commit()) { "Could not update check-in progress. Check the result again." }
        }
    }
    companion object { private val lock = Any() }
}

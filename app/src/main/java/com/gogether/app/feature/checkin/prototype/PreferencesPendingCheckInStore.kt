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
        check(previous == null || previous == attempt) { "请先核对上次签到结果。" }
        val value = JSONObject().put("request", attempt.id).put("lat", attempt.session.meeting.latitude)
            .put("lon", attempt.session.meeting.longitude).put("start", attempt.session.startAt).put("end", attempt.session.endAt)
        check(prefs.edit().putString(key(attempt.actor, attempt.session.id), value.toString()).commit()) {
            "无法保存签到进度，请稍后重试。"
        }
    }
    override fun remove(attempt: CheckInAttempt) = synchronized(lock) {
        if (get(attempt.actor, attempt.session.id) == attempt) {
            check(prefs.edit().remove(key(attempt.actor, attempt.session.id)).commit()) { "无法更新签到进度，请重新核对。" }
        }
    }
    companion object { private val lock = Any() }
}

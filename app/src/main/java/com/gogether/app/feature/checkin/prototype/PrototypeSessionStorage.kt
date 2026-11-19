package com.gogether.app.feature.checkin.prototype

import org.json.JSONObject

/** Local record format, deliberately distinct from a scannable credential. Reads old QR-backed records. */
internal object PrototypeSessionStorage {
    fun encode(session: PrototypeSession): String = JSONObject()
        .put("version", 1).put("id", session.id).put("start", session.startAt).put("end", session.endAt)
        .apply { session.meeting?.let { put("lat", it.latitude); put("lon", it.longitude) } }.toString()

    fun decode(raw: String): PrototypeSession {
        if (!raw.startsWith("{")) return PrototypeCodeCodec.decode(raw).session
        val value = JSONObject(raw)
        require(value.getInt("version") == 1) { "This event record version is not supported." }
        val point = if (value.has("lat") || value.has("lon"))
            MeetingPoint(value.getDouble("lat"), value.getDouble("lon")) else null
        return PrototypeSession(value.getString("id"), point, value.getLong("start"), value.getLong("end"))
    }
}

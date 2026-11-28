package com.gogether.app.feature.checkin.prototype

import org.json.JSONObject

/** Reads old saved records without accepting their old QR credentials. Coordinates are discarded. */
internal object PrototypeSessionStorage {
    fun encode(session: PrototypeSession): String = JSONObject()
        .put("version", 2).put("id", session.id).put("start", session.startAt).put("end", session.endAt).toString()
    fun decode(raw: String): PrototypeSession {
        if (!raw.startsWith("{")) {
            val old = raw.split('|')
            if (old.size == 8 && old[0] == "GOGETHER-LOCAL-QR-1")
                return PrototypeSession(old[1], old[4].toLong(), old[5].toLong())
            return PrototypeCodeCodec.decode(raw).session
        }
        val value = JSONObject(raw)
        require(value.getInt("version") in 1..2) { "This event record version is not supported." }
        return PrototypeSession(value.getString("id"), value.getLong("start"), value.getLong("end"))
    }
}

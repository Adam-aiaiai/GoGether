package com.gogether.app

import com.gogether.app.feature.checkin.prototype.*

/** Existing review UI/storage tests start after attendance; lifecycle behavior has separate acceptance tests. */
internal fun PrototypeStore.rememberAttendedSession(actor: String, session: PrototypeSession) {
    rememberSession(actor, session)
    if (receipt(session.id, actor) == null) save(PrototypeReceipt(session.id, actor, session.startAt,
        "initial:${session.id}:$actor"))
}

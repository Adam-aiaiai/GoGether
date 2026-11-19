package com.gogether.app.feature.checkin.prototype

import android.content.Context
import android.content.Intent

/** Public E-only integration entry. No account IDs, registration flags or credentials in extras. */
object CheckInEntryIntent {
    private const val ACTIVITY = "com.gogether.checkin.ACTIVITY"
    private const val GENERAL = "com.gogether.checkin.GENERAL"
    fun general(context: Context) = Intent(context, PrototypeCheckInActivity::class.java).setAction(GENERAL)
    fun activity(context: Context, session: PrototypeSession): Intent =
        Intent(context, PrototypeCheckInActivity::class.java).setAction(ACTIVITY)
            .putExtra("eventId", session.id).putExtra("latitude", session.meeting.latitude)
            .putExtra("longitude", session.meeting.longitude).putExtra("startAt", session.startAt).putExtra("endAt", session.endAt)

    fun read(intent: Intent): CheckInEntry {
        // Preserve the existing Profile launcher, which has no action or extras.
        if (intent.action == null || intent.action == GENERAL) {
            require(!intent.hasExtra("eventId")) { "活动入口信息不完整。" }
            return CheckInEntry.General
        }
        require(intent.action == ACTIVITY) { "无法识别签到入口。" }
        return CheckInEntry.Activity(PrototypeSession(requireNotNull(intent.getStringExtra("eventId")) { "缺少活动编号。" },
            MeetingPoint(intent.getDoubleExtra("latitude", Double.NaN), intent.getDoubleExtra("longitude", Double.NaN)),
            intent.getLongExtra("startAt", 0), intent.getLongExtra("endAt", 0)))
    }
}

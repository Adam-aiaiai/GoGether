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
            .putExtra("eventId", session.id).putExtra("latitude", requireNotNull(session.meeting).latitude)
            .putExtra("longitude", requireNotNull(session.meeting).longitude).putExtra("startAt", session.startAt).putExtra("endAt", session.endAt)

    fun read(intent: Intent): CheckInEntry {
        // Preserve the existing Profile launcher, which has no action or extras.
        if (intent.action == null || intent.action == GENERAL) {
            require(!intent.hasExtra("eventId")) { "Event entry details are incomplete." }
            return CheckInEntry.General
        }
        require(intent.action == ACTIVITY) { "Unrecognized check-in entry." }
        return CheckInEntry.Activity(PrototypeSession(requireNotNull(intent.getStringExtra("eventId")) { "Missing event ID." },
            MeetingPoint(intent.getDoubleExtra("latitude", Double.NaN), intent.getDoubleExtra("longitude", Double.NaN)),
            intent.getLongExtra("startAt", 0), intent.getLongExtra("endAt", 0)))
    }
}

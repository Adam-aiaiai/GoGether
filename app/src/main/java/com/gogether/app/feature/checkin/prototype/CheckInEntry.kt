package com.gogether.app.feature.checkin.prototype

/** E's entry contract. B supplies a snapshot for an activity; General accepts any valid activity. */
sealed interface CheckInEntry {
    data object General : CheckInEntry
    data class Activity(val session: PrototypeSession) : CheckInEntry
}

/** Read-only A seam. Never accept a user ID from a QR code or an Intent. */
fun interface CheckInIdentitySource { fun currentUserId(): String? }

enum class CheckInRegistration { CONFIRMED, WAITLISTED, NONE }

/** Read-only C seam; both the UI and mock backend use the same source. */
interface CheckInRegistrationSource {
    fun status(activityId: String?, userId: String): CheckInRegistration
    fun participants(activityId: String?): List<PrototypeAttendee>
}

object MockCheckInRegistrations : CheckInRegistrationSource {
    override fun status(activityId: String?, userId: String) = when {
        prototypeParticipants.any { it.userId == userId } -> CheckInRegistration.CONFIRMED
        userId == "emma" -> CheckInRegistration.WAITLISTED
        else -> CheckInRegistration.NONE
    }
    override fun participants(activityId: String?) = prototypeParticipants
}

/** A pending camera result belongs to a user, activity scope and entry generation. */
internal data class PendingCheckInScan(val id: Long, val entry: CheckInEntry, val generation: Long)

package com.gogether.app.feature.checkin.prototype

/** E-owned read-only B seam. Organizer identity never comes from the scanning user or QR payload. */
data class CheckInActivityInfo(val session: PrototypeSession, val organizerId: String,
    val title: String = "羽毛球交流", val meetingName: String = "组织者指定集合点",
    val introduction: String = "一起挥拍，认识新朋友。活动前先到集合点碰面，再一起前往场地。") {
    val organizerName: String get() = prototypeParticipants.find { it.userId == organizerId }?.name ?: organizerId
}

interface CheckInActivitySource {
    fun activity(session: PrototypeSession): CheckInActivityInfo
    fun isCatalogActivity(session: PrototypeSession): Boolean = false
}

/** Compatibility adapter for previously created offline Alice activities, not a production authority. */
object LegacyCheckInActivities : CheckInActivitySource {
    override fun activity(session: PrototypeSession) = CheckInActivityInfo(session, "alice")
}

/** Strict B fixture: unknown IDs and altered snapshots fail closed. */
class MockActivityCatalog(records: List<CheckInActivityInfo>) : CheckInActivitySource {
    private val records = records.associateBy { it.session.id }
    init {
        require(this.records.size == records.size)
        require(records.all { it.organizerId.isNotBlank() })
    }
    override fun activity(session: PrototypeSession): CheckInActivityInfo {
        val info = requireNotNull(records[session.id]) { "未找到本场活动资料。" }
        require(info.session == session) { "活动资料已变化，请重新进入。" }
        return info
    }
    override fun isCatalogActivity(session: PrototypeSession) = records[session.id]?.session == session
}

/** C fixture uses an exact activity/user pair; an unknown activity never inherits another roster. */
class MockActivityRegistrations(private val rosters: Map<String, List<PrototypeAttendee>>,
    private val waitlists: Map<String, Set<String>> = emptyMap()) : CheckInRegistrationSource {
    override fun status(activityId: String?, userId: String) = when {
        rosters[activityId].orEmpty().any { it.userId == userId } -> CheckInRegistration.CONFIRMED
        userId in waitlists[activityId].orEmpty() -> CheckInRegistration.WAITLISTED
        else -> CheckInRegistration.NONE
    }
    override fun participants(activityId: String?) = rosters[activityId].orEmpty().map { it.copy(checkedInAt = null) }
}

data class CheckInRegistrationSnapshot(val participants: List<PrototypeAttendee>, val waitlisted: Set<String>)

/** Restored catalog activities cannot inherit the legacy activity-independent C roster. */
class StoredMockCheckInRegistrations(private val store: PrototypeStore) : CheckInRegistrationSource {
    override fun status(activityId: String?, userId: String): CheckInRegistration {
        val snapshot = activityId?.let(store::registrationSnapshot)
        if (snapshot != null) return when {
            snapshot.participants.any { it.userId == userId } -> CheckInRegistration.CONFIRMED
            userId in snapshot.waitlisted -> CheckInRegistration.WAITLISTED
            else -> CheckInRegistration.NONE
        }
        if (activityId != null && store.activityInfo(activityId) != null) return CheckInRegistration.NONE
        return MockCheckInRegistrations.status(activityId, userId)
    }
    override fun participants(activityId: String?): List<PrototypeAttendee> {
        activityId?.let { id ->
            store.registrationSnapshot(id)?.let { return it.participants }
            if (store.activityInfo(id) != null) return emptyList()
        }
        return MockCheckInRegistrations.participants(activityId)
    }
}

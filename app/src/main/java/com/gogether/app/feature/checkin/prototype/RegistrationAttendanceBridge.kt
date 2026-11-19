package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.model.*
import com.gogether.app.core.data.LocalDataLock
import java.util.UUID

/** C/E integration: reads the live C snapshot, never a copied roster or QR-provided identity.
 * The UUID is a stable adapter key, not a replacement for C's event ID. Including createdAt
 * prevents a reset Demo event from acquiring an older event's receipts or private feedback.
 */
class RegistrationAttendanceBridge(
    private val snapshot: () -> AppSnapshot,
    private val store: PrototypeStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsedNow: () -> Long,
) : CheckInRegistrationSource, CheckInActivitySource {
    fun key(event: Event): String = UUID.nameUUIDFromBytes(
        "gogether-ce-v1:${event.id}:${event.createdAt}".toByteArray(Charsets.UTF_8)).toString()

    private fun event(key: String?) = snapshot().events.find { key(it) == key }
    fun session(event: Event): PrototypeSession? = store.activityInfo(key(event))?.session?.takeIf {
        it.startAt == event.startAt && it.endAt == event.endAt
    }
    override fun status(activityId: String?, userId: String): CheckInRegistration {
        val event = event(activityId) ?: return CheckInRegistration.NONE
        return when (snapshot().registration(event.id, userId)?.status) {
            RegistrationStatus.CONFIRMED -> CheckInRegistration.CONFIRMED
            RegistrationStatus.WAITLISTED -> CheckInRegistration.WAITLISTED
            else -> CheckInRegistration.NONE
        }
    }
    override fun participants(activityId: String?): List<PrototypeAttendee> {
        val event = event(activityId) ?: return emptyList()
        val state = snapshot()
        return state.confirmed(event.id).sortedBy { it.queueOrder }.map {
            PrototypeAttendee(it.userId, state.userName(it.userId))
        }
    }
    override fun activity(session: PrototypeSession): CheckInActivityInfo {
        val event = requireNotNull(event(session.id)) { "当前活动已不存在，请返回活动列表。" }
        require(this.session(event) == session) { "活动或集合点已变化，请重新进入。" }
        return CheckInActivityInfo(session, event.organizerId, event.title,
            "${event.city} ${event.location}", event.description, event.id, snapshot().userName(event.organizerId))
    }
    override fun isCatalogActivity(session: PrototypeSession) = runCatching { activity(session) }.isSuccess

    /** B currently supplies a text address. Only this event's organizer may fix its GPS point.
     * We keep B's ID, owner and times; this never invents a new ten-minute Demo activity.
     */
    fun prepare(actor: String, eventId: String, fix: PositionFix): PrototypeSession {
        requireActor(actor)
        val event = requireNotNull(snapshot().event(eventId)) { "活动不存在。" }
        require(event.organizerId == actor) { "只有本场组织者可以设置集合点。" }
        require(event.status == EventStatus.OPEN && now() < event.endAt) { "活动已关闭，不能设置集合点。" }
        validatePosition(fix, elapsedNow())
        session(event)?.let { return it }
        val session = PrototypeSession(key(event), fix.point, event.startAt, event.endAt)
        store.rememberActivityInfo(CheckInActivityInfo(session, event.organizerId, event.title,
            "${event.city} ${event.location}", event.description, event.id, snapshot().userName(event.organizerId)))
        store.rememberSession(actor, session)
        return session
    }

    private fun requireActor(actor: String) {
        require(snapshot().currentUserId == actor && snapshot().users.any { it.id == actor }) { "账号已变化，请重新进入。" }
    }
    private fun requireOpen(session: PrototypeSession) {
        activity(session)
        require(event(session.id)?.status == EventStatus.OPEN) { "活动已取消或完成，不能签到。" }
    }
    private val liveStore = object : PrototypeStore by store {
        override fun isCancelled(id: String) = event(id)?.status == EventStatus.CANCELLED || store.isCancelled(id)
    }
    private val local = LocalPrototypeBackend(liveStore, now, elapsedNow, this, this)

    /** Local trusted boundary only. No network synchronization or signed tokens are claimed. */
    val backend: PrototypeCheckInBackend = object : PrototypeCheckInBackend by local {
        override fun isCheckInClosed(session: PrototypeSession) = event(session.id)?.status != EventStatus.OPEN ||
            !isCatalogActivity(session) || local.isCancelled(session)
        override fun hostedSession(actor: String): PrototypeSession? {
            requireActor(actor)
            return snapshot().events.filter { it.organizerId == actor }.mapNotNull(::session).maxByOrNull { it.startAt }
        }
        override fun hostedSession(actor: String, session: PrototypeSession): PrototypeSession? {
            requireActor(actor)
            return session.takeIf { activity(it).organizerId == actor }?.also { store.rememberSession(actor, it) }
        }
        override fun history(actor: String): List<PrototypeHistoryEntry> {
            requireActor(actor)
            snapshot().events.filter { it.organizerId == actor || status(key(it), actor) == CheckInRegistration.CONFIRMED }
                .mapNotNull(::session).forEach { store.rememberSession(actor, it) }
            return local.history(actor).filter { isCatalogActivity(it.session) }.map { record ->
                val event = requireNotNull(event(record.session.id))
                val previous = snapshot().feedback.find { it.eventId == event.id && it.userId == actor }
                record.copy(receipt = record.receipt ?: receipt(event, actor),
                    feedback = record.feedback ?: previous?.let {
                        PrototypeFeedback(record.session.id, actor, it.rating, it.comment, it.createdAt)
                    })
            }
        }
        override fun issue(actor: String, session: PrototypeSession, fix: PositionFix): PrototypeCode {
            requireActor(actor); requireOpen(session)
            return local.issue(actor, session, fix)
        }
        override fun rememberCode(actor: String, raw: String): PrototypeSession {
            requireActor(actor); requireOpen(PrototypeCodeCodec.decode(raw).session)
            return local.rememberCode(actor, raw)
        }
        override fun submit(actor: String, raw: String, fix: PositionFix): PrototypeReceipt = synchronized(LocalDataLock.monitor) {
            requireActor(actor); requireOpen(PrototypeCodeCodec.decode(raw).session)
            val event = requireNotNull(event(PrototypeCodeCodec.decode(raw).session.id))
            require(snapshot().checkIns.none { it.eventId == event.id && it.userId == actor }) { "你已签到，无需重复提交。" }
            local.submit(actor, raw, fix)
        }
        override fun attendance(actor: String, session: PrototypeSession): List<PrototypeAttendee> {
            requireActor(actor)
            require(activity(session).organizerId == actor) { "只有组织者可以查看名单。" }
            val event = requireNotNull(event(session.id))
            // Preserve actual attendance even if C later removes a confirmed registration.
            val checked = snapshot().users.mapNotNull { user ->
                receipt(event, user.id)?.let { PrototypeAttendee(user.id, user.displayName, it.checkedInAt) }
            }
            return (participants(session.id).map { person ->
                person.copy(checkedInAt = receipt(event, person.userId)?.checkedInAt)
            } + checked).distinctBy { it.userId }
        }
        override fun submitFeedback(actor: String, sessionId: String, rating: Int, comment: String,
            details: FeedbackDetails): PrototypeFeedback = synchronized(LocalDataLock.monitor) {
            requireActor(actor)
            val event = requireNotNull(event(sessionId)) { "活动不存在。" }
            require(snapshot().feedback.none { it.eventId == event.id && it.userId == actor }) { "你已评价，无需重复提交。" }
            history(actor)
            local.submitFeedback(actor, sessionId, rating, comment, details)
        }
        override fun reviews(actor: String, session: PrototypeSession): List<VisibleFeedback> {
            requireActor(actor); return local.reviews(actor, session)
        }
        override fun cancel(actor: String, session: PrototypeSession) {
            requireActor(actor)
            throw IllegalArgumentException("请返回活动管理取消活动，报名和候补会同步更新。")
        }
        override fun createMeeting(actor: String, fix: PositionFix): PrototypeSession =
            throw IllegalArgumentException("请从具体活动设置集合点。")
    }

    fun receipt(event: Event, userId: String): PrototypeReceipt? = store.receipt(key(event), userId)
        ?: snapshot().checkIns.find { it.eventId == event.id && it.userId == userId }
            ?.let { PrototypeReceipt(key(event), userId, it.checkedInAt) }

    /** Read-only projection for the original management UI. Feedback stays in E's privacy layer. */
    fun withAttendance(state: AppSnapshot): AppSnapshot = state.copy(checkIns =
        (state.checkIns + state.events.flatMap { event -> state.users.mapNotNull { user ->
            store.receipt(key(event), user.id)?.let {
                CheckIn("ce:${it.sessionId}:${it.userId}", event.id, user.id, it.checkedInAt)
            }
        } }).distinctBy { it.eventId to it.userId })
}

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
        it.meeting != null && it.startAt == event.startAt && it.endAt == event.endAt
    }
    private fun recordSession(event: Event): PrototypeSession? {
        val stored = store.activityInfo(key(event))?.session
        return if (stored == null) PrototypeSession(key(event), null, event.startAt, event.endAt)
            else stored.takeIf { it.startAt == event.startAt && it.endAt == event.endAt }
    }
    fun hasEnded(event: Event, time: Long) = event.status == EventStatus.COMPLETED || time >= event.endAt

    /** Feedback does not require GPS or prior check-in. This record cannot issue a QR credential. */
    fun prepareFeedback(actor: String, eventId: String): PrototypeSession {
        requireActor(actor)
        val event = requireNotNull(snapshot().event(eventId)) { "Event not found." }
        require(hasEnded(event, now()) || event.status == EventStatus.CANCELLED) { "You can review after the event ends." }
        val record = requireNotNull(recordSession(event)) { "Event details have changed. Reopen the event." }
        rememberRecord(actor, record)
        return record
    }
    private fun rememberRecord(actor: String, record: PrototypeSession) {
        val info = activity(record)
        if (store.activityInfo(record.id) == null) store.rememberActivityInfo(info)
        store.rememberSession(actor, record)
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
        val event = requireNotNull(event(session.id)) { "This event no longer exists. Return to the event list." }
        require(recordSession(event) == session) { "The event or meeting point has changed. Reopen the event." }
        store.activityInfo(session.id)?.let {
            require(it.organizerId == event.organizerId && it.sourceEventId == event.id) { "The event organizer has changed. Reopen the event." }
        }
        return CheckInActivityInfo(session, event.organizerId, event.title,
            "${event.city} ${event.location}", event.description, event.id, snapshot().userName(event.organizerId))
    }
    override fun isCatalogActivity(session: PrototypeSession) = runCatching { activity(session) }.isSuccess

    /** B currently supplies a text address. Only this event's organizer may fix its GPS point.
     * We keep B's ID, owner and times; this never invents a new ten-minute Demo activity.
     */
    fun prepare(actor: String, eventId: String, fix: PositionFix): PrototypeSession {
        requireActor(actor)
        val event = requireNotNull(snapshot().event(eventId)) { "Event not found." }
        require(event.organizerId == actor) { "Only this event's organizer can set the meeting point." }
        require(event.status == EventStatus.OPEN && now() < event.endAt) { "This event is closed. The meeting point cannot be set." }
        validatePosition(fix, elapsedNow())
        session(event)?.let { return it }
        val session = PrototypeSession(key(event), fix.point, event.startAt, event.endAt)
        store.rememberActivityInfo(CheckInActivityInfo(session, event.organizerId, event.title,
            "${event.city} ${event.location}", event.description, event.id, snapshot().userName(event.organizerId)))
        store.rememberSession(actor, session)
        return session
    }

    private fun requireActor(actor: String) {
        require(snapshot().currentUserId == actor && snapshot().users.any { it.id == actor }) { "Your account has changed. Reopen this page." }
    }
    private fun requireOpen(session: PrototypeSession) {
        activity(session)
        requireNotNull(session.meeting) { "The check-in meeting point has not been set." }
        require(event(session.id)?.status == EventStatus.OPEN) { "This event is cancelled or completed. Check-in is closed." }
    }
    private val liveStore = object : PrototypeStore by store {
        override fun isCancelled(id: String) = event(id)?.status == EventStatus.CANCELLED || store.isCancelled(id)
    }
    private val local = LocalPrototypeBackend(liveStore, now, elapsedNow, this, this,
        completed = { event(it.id)?.status == EventStatus.COMPLETED },
        userName = { id -> snapshot().users.find { it.id == id }?.displayName ?: "Participant" })

    /** Local trusted boundary only. No network synchronization or signed tokens are claimed. */
    val backend: PrototypeCheckInBackend = object : PrototypeCheckInBackend by local {
        override fun isCheckInClosed(session: PrototypeSession) = session.meeting == null || event(session.id)?.status != EventStatus.OPEN ||
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
                .mapNotNull { event -> session(event) ?: recordSession(event).takeIf { hasEnded(event, now()) } }
                .forEach { rememberRecord(actor, it) }
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
            require(snapshot().checkIns.none { it.eventId == event.id && it.userId == actor }) { "You are already checked in." }
            local.submit(actor, raw, fix)
        }
        override fun attendance(actor: String, session: PrototypeSession): List<PrototypeAttendee> {
            requireActor(actor)
            require(activity(session).organizerId == actor) { "Only the organizer can view attendance." }
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
            val event = requireNotNull(event(sessionId)) { "Event not found." }
            require(snapshot().feedback.none { it.eventId == event.id && it.userId == actor }) { "You have already reviewed this event." }
            history(actor)
            local.submitFeedback(actor, sessionId, rating, comment, details)
        }
        override fun reviews(actor: String, session: PrototypeSession): List<VisibleFeedback> {
            requireActor(actor)
            val current = local.reviews(actor, session)
            val info = activity(session)
            val savedUsers = store.feedbackForSession(session.id).map { it.userId }.toSet()
            val older = snapshot().feedback.filter { it.eventId == info.sourceEventId && it.userId !in savedUsers }
                .mapNotNull { old -> PrototypeFeedback(session.id, old.userId, old.rating, old.comment, old.createdAt)
                    .visibleTo(actor, info.organizerId, snapshot().users.find { it.id == old.userId }?.displayName ?: "Participant") }
            return (current + older).sortedByDescending { it.createdAt }
        }
        override fun cancel(actor: String, session: PrototypeSession) {
            requireActor(actor)
            throw IllegalArgumentException("Cancel from event management to update registrations and the waitlist together.")
        }
        override fun createMeeting(actor: String, fix: PositionFix): PrototypeSession =
            throw IllegalArgumentException("Set the meeting point from an event.")
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

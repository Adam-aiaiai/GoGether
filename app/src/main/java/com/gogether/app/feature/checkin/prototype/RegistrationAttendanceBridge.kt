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
) : CheckInRegistrationSource, CheckInActivitySource {
    fun key(event: Event): String = UUID.nameUUIDFromBytes(
        "gogether-ce-v1:${event.id}:${event.createdAt}".toByteArray(Charsets.UTF_8)).toString()

    private fun event(key: String?) = snapshot().events.find { key(it) == key }
    private fun meetingName(event: Event) = "${event.city} ${event.meetingLocation ?: event.location}"
    fun session(event: Event): PrototypeSession = PrototypeSession(key(event), event.startAt, event.endAt)
    private fun recordSession(event: Event): PrototypeSession = session(event)
    fun hasEnded(event: Event, time: Long) = event.status == EventStatus.COMPLETED || time >= event.endAt

    /** Open a saved event record; submission separately requires a current valid check-in. */
    fun prepareFeedback(actor: String, eventId: String): PrototypeSession {
        requireActor(actor)
        val event = requireNotNull(snapshot().event(eventId)) { "Event not found." }
        require(hasEnded(event, now()) || event.status == EventStatus.CANCELLED) { "You can review after the event ends." }
        val record = requireNotNull(recordSession(event)) { "Event details have changed. Reopen the event." }
        rememberRecord(actor, record)
        return record
    }
    private fun rememberRecord(actor: String, record: PrototypeSession) {
        val event = requireNotNull(event(record.id))
        val info = info(event, record)
        val previous = store.activityInfo(record.id)
        if (previous == null) store.rememberActivityInfo(info)
        else {
            require(previous.organizerId == event.organizerId && previous.sourceEventId == event.id) { "The event organizer has changed. Reopen the event." }
            if (previous.session != record || previous.meetingName != info.meetingName) store.updateLinkedActivity(info)
        }
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
            PrototypeAttendee(it.userId, state.userName(it.userId), avatarUri = avatarUri(it.userId), organizer = it.userId == event.organizerId)
        }
    }
    override fun activity(session: PrototypeSession): CheckInActivityInfo {
        val event = requireNotNull(event(session.id)) { "This event no longer exists. Return to the event list." }
        require(recordSession(event) == session) { "The event or meeting point has changed. Reopen the event." }
        store.activityInfo(session.id)?.let {
            require(it.organizerId == event.organizerId && it.sourceEventId == event.id) { "The event organizer has changed. Reopen the event." }
        }
        require(session.startAt == event.startAt && session.endAt == event.endAt) { "Event times have changed. Reopen the event." }
        return info(event, session)
    }
    private fun info(event: Event, session: PrototypeSession) = CheckInActivityInfo(session, event.organizerId, event.title,
        meetingName(event), event.description, event.id, snapshot().userName(event.organizerId))
    override fun isCatalogActivity(session: PrototypeSession) = runCatching { activity(session) }.isSuccess
    override fun avatarUri(userId: String) = snapshot().users.find { it.id == userId }?.avatarUri.orEmpty()

    /** Prepare the event record; arrival is confirmed only when a code is successfully issued. */
    fun prepare(actor: String, eventId: String): PrototypeSession {
        requireActor(actor)
        val event = requireNotNull(snapshot().event(eventId)) { "Event not found." }
        require(event.organizerId == actor) { "Only this event's organizer can prepare check-in." }
        require(event.status == EventStatus.OPEN && now() < event.endAt) { "This event is closed." }
        return session(event).also { rememberRecord(actor, it) }
    }

    override fun registrationKey(activityId: String?, userId: String): String? = event(activityId)?.let {
        snapshot().registration(it.id, userId)?.takeIf { r -> r.status == RegistrationStatus.CONFIRMED }?.attendanceKey
    }
    override fun isCurrentReceipt(receipt: PrototypeReceipt): Boolean {
        val event = event(receipt.sessionId) ?: return false
        if (event.status == EventStatus.CANCELLED) return false
        val registration = snapshot().registration(event.id, receipt.userId)
            ?.takeIf { it.status == RegistrationStatus.CONFIRMED } ?: return false
        return receipt.registrationKey?.let { it == registration.attendanceKey }
            ?: (receipt.checkedInAt >= registration.joinedAt)
    }

    private fun requireActor(actor: String) {
        require(snapshot().currentUserId == actor && snapshot().users.any { it.id == actor }) { "Your account has changed. Reopen this page." }
    }
    private fun requireOpen(session: PrototypeSession) {
        activity(session)
        require(session(event(session.id)!!) == session) { "The meeting point or event time has changed. Ask the organizer for a new code." }
        require(event(session.id)?.status == EventStatus.OPEN) { "This event is cancelled or completed. Check-in is closed." }
    }
    private val liveStore = object : PrototypeStore by store {
        override fun isCancelled(id: String) = event(id)?.status == EventStatus.CANCELLED || store.isCancelled(id)
    }
    private val local = LocalPrototypeBackend(liveStore, now, this, this,
        completed = { event(it.id)?.status == EventStatus.COMPLETED },
        userName = { id -> snapshot().users.find { it.id == id }?.displayName ?: "Participant" }, userAvatar = ::avatarUri,
        receiptLookup = { id, actor -> event(id)?.let { receipt(it, actor) } })

    /** Local trusted boundary only. No network synchronization or signed tokens are claimed. */
    val backend: PrototypeCheckInBackend = object : PrototypeCheckInBackend by local {
        override fun isCheckInClosed(session: PrototypeSession) = event(session.id)?.let { session(it) != session } != false || event(session.id)?.status != EventStatus.OPEN ||
            !isCatalogActivity(session) || now() >= session.endAt || local.isCancelled(session)
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
                .map(::session)
                .forEach { rememberRecord(actor, it) }
            return local.history(actor).filter { isCatalogActivity(it.session) }.map { record ->
                val event = requireNotNull(event(record.session.id))
                val previous = snapshot().feedback.find { it.eventId == event.id && it.userId == actor && !store.isDeleted(com.gogether.app.feature.social.LocalSocialStore.legacyReviewId(it.id)) }
                record.copy(receipt = record.receipt ?: receipt(event, actor),
                    feedback = record.feedback ?: previous?.let {
                        PrototypeFeedback(record.session.id, actor, it.rating, it.comment, it.createdAt, publicId=com.gogether.app.feature.social.LocalSocialStore.legacyReviewId(it.id))
                    })
            }
        }
        override fun issue(actor: String, session: PrototypeSession): PrototypeCode {
            requireActor(actor); requireOpen(session)
            return synchronized(LocalDataLock.monitor) { local.issue(actor, session) }
        }
        override fun rememberCode(actor: String, raw: String): PrototypeSession {
            requireActor(actor); validateCurrentCode(raw)
            return local.rememberCode(actor, raw)
        }
        override fun submit(actor: String, raw: String, registrationKey: String?): PrototypeReceipt = synchronized(LocalDataLock.monitor) {
            requireActor(actor); validateCurrentCode(raw)
            val event = requireNotNull(event(PrototypeCodeCodec.decode(raw).session.id))
            require(receipt(event, actor) == null) { "You are already checked in." }
            local.submit(actor, raw, registrationKey)
        }
        override fun attendance(actor: String, session: PrototypeSession): List<PrototypeAttendee> {
            requireActor(actor)
            require(activity(session).organizerId == actor) { "Only the organizer can view attendance." }
            val event = requireNotNull(event(session.id))
            return participants(session.id).map { person ->
                person.copy(checkedInAt = receipt(event, person.userId)?.checkedInAt,
                    organizer = person.userId == event.organizerId)
            }
        }
        override fun submitFeedback(actor: String, sessionId: String, rating: Int, comment: String,
            details: FeedbackDetails): PrototypeFeedback = synchronized(LocalDataLock.monitor) {
            requireActor(actor)
            val event = requireNotNull(event(sessionId)) { "Event not found." }
            require(snapshot().feedback.none { it.eventId == event.id && it.userId == actor && !store.isDeleted(com.gogether.app.feature.social.LocalSocialStore.legacyReviewId(it.id)) }) { "You have already reviewed this event." }
            history(actor)
            local.submitFeedback(actor, sessionId, rating, comment, details)
        }
        override fun reviews(actor: String, session: PrototypeSession): List<VisibleFeedback> {
            requireActor(actor)
            val current = local.reviews(actor, session)
            val info = activity(session)
            val savedUsers = store.feedbackForSession(session.id).map { it.userId }.toSet()
            val older = snapshot().feedback.filter { it.eventId == info.sourceEventId && it.userId !in savedUsers && !store.isDeleted(com.gogether.app.feature.social.LocalSocialStore.legacyReviewId(it.id)) && !store.isBlocked(actor,it.userId) }
                .mapNotNull { old -> PrototypeFeedback(session.id, old.userId, old.rating, old.comment, old.createdAt)
                    .visibleTo(actor, info.organizerId, snapshot().users.find { it.id == old.userId }?.displayName ?: "Participant", avatarUri(old.userId))?.copy(id=com.gogether.app.feature.social.LocalSocialStore.legacyReviewId(old.id)) }
            return (current + older).sortedByDescending { it.createdAt }
        }
        override fun cancel(actor: String, session: PrototypeSession) {
            requireActor(actor)
            throw IllegalArgumentException("Cancel from event management to update registrations and the waitlist together.")
        }
        override fun createMeeting(actor: String): PrototypeSession =
            throw IllegalArgumentException("Open check-in from an event.")
    }

    private fun validateCurrentCode(raw: String) {
        val code = PrototypeCodeCodec.decode(raw)
        requireOpen(code.session)
        require(code.issuedAt >= requireNotNull(event(code.session.id)).updatedAt) {
            "The event has been updated. Scan the organizer's latest code."
        }
    }

    fun receipt(event: Event, userId: String): PrototypeReceipt? = store.receipt(key(event), userId)?.takeIf(::isCurrentReceipt)
        ?: snapshot().activeCheckIn(event.id, userId)?.let {
            PrototypeReceipt(key(event), userId, it.checkedInAt, it.registrationKey, it.status == CheckInStatus.HOST_CONFIRMED)
        }

    /** Read-only projection for the original management UI. Feedback stays in E's privacy layer. */
    fun withAttendance(state: AppSnapshot): AppSnapshot = state.copy(checkIns =
        (state.checkIns.filter { state.activeCheckIn(it.eventId, it.userId) == it } + state.events.flatMap { event -> state.users.mapNotNull { user ->
            receipt(event, user.id)?.let {
                CheckIn("ce:${it.sessionId}:${it.userId}", event.id, user.id, it.checkedInAt,
                    if (it.organizer) CheckInStatus.HOST_CONFIRMED else CheckInStatus.QR_RECORDED, it.registrationKey)
            }
        } }).distinctBy { it.eventId to it.userId },
        feedback = (state.feedback.filterNot { store.isDeleted(com.gogether.app.feature.social.LocalSocialStore.legacyReviewId(it.id)) } + state.events.mapNotNull { event ->
            val actor = state.currentUserId ?: return@mapNotNull null
            val info = store.activityInfo(key(event)) ?: return@mapNotNull null
            if (info.organizerId != event.organizerId || info.sourceEventId != event.id) return@mapNotNull null
            store.feedback(key(event), actor)?.let {
                // Only the current user's rating is projected; private text/photos stay in E.
                Feedback("ce:${it.sessionId}:$actor", event.id, actor, it.rating, "", it.createdAt)
            }
        }).distinctBy { it.eventId to it.userId })
}

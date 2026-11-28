package com.gogether.app.feature.checkin.prototype

import java.util.UUID

/** Short-lived QR policy shared by local check-in flows. */
object PrototypePolicy {
    const val CODE_TTL_MS = 30_000L
    const val REFRESH_MS = 10_000L
    const val HALF_HOUR_MS = 1_800_000L
}

data class PrototypeSession(val id: String, val startAt: Long, val endAt: Long) {
    init {
        require(UUID.fromString(id).toString() == id) { "Invalid event ID." }
        require(startAt in 1..4_102_444_800_000L && endAt > startAt && endAt - startAt <= 86_400_000) {
            "Invalid event time."
        }
    }
    fun requireWindow(now: Long) {
        require(now < endAt) { "This event has ended. You cannot check in." }
        require(now >= startAt - PrototypePolicy.HALF_HOUR_MS) { "Check-in has not opened yet." }
        require(now <= startAt + PrototypePolicy.HALF_HOUR_MS) { "The check-in window has closed." }
    }
}

data class PrototypeCode(val session: PrototypeSession, val issuedAt: Long, val expiresAt: Long) {
    fun requireValid(now: Long) {
        session.requireWindow(now)
        require(issuedAt >= session.startAt - PrototypePolicy.HALF_HOUR_MS &&
            issuedAt <= session.startAt + PrototypePolicy.HALF_HOUR_MS &&
            expiresAt > issuedAt && expiresAt - issuedAt <= PrototypePolicy.CODE_TTL_MS &&
            expiresAt <= session.endAt && expiresAt <= session.startAt + PrototypePolicy.HALF_HOUR_MS + 1) {
            "The code has invalid time data. Scan again."
        }
        require(now >= issuedAt - 5_000) { "The phones have different times. Enable automatic date and time." }
        require(now < expiresAt) { "This code has expired. Scan the organizer's latest code." }
    }
}

/** Local demo credential; production uses opaque server-issued tokens. Old QR versions are rejected. */
object PrototypeCodeCodec {
    private const val PREFIX = "GOGETHER-LOCAL-QR-2"
    fun encode(code: PrototypeCode): String = with(code) {
        listOf(PREFIX, session.id, session.startAt, session.endAt, issuedAt, expiresAt).joinToString("|")
    }
    fun decode(raw: String): PrototypeCode {
        require(raw.length <= 512) { "This is not a valid Gogether check-in code." }
        try {
            val p = raw.split('|')
            require(p.size == 6 && p[0] == PREFIX)
            return PrototypeCode(PrototypeSession(p[1], p[2].toLong(), p[3].toLong()), p[4].toLong(), p[5].toLong())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid or old Gogether code. Scan the organizer's latest code.")
        }
    }
}

data class PrototypeReceipt(val sessionId: String, val userId: String, val checkedInAt: Long,
    val registrationKey: String? = null, val organizer: Boolean = false)
data class PrototypeFeedback(val sessionId: String, val userId: String, val rating: Int,
    val comment: String, val createdAt: Long, val details: FeedbackDetails = FeedbackDetails(), val anonymousName: String = "", val publicId: String = "") {
    val contentId: String get() = publicId.ifBlank { com.gogether.app.feature.social.LocalSocialStore.reviewId(sessionId,userId,createdAt) }
}
data class PrototypeHistoryEntry(val session: PrototypeSession, val receipt: PrototypeReceipt?,
    val feedback: PrototypeFeedback?, val cancelled: Boolean,
    val completed: Boolean = false, val eligible: Boolean = true) {
    fun hasEnded(now: Long) = feedbackHasEnded(session, now, completed)
}

internal fun feedbackHasEnded(session: PrototypeSession, now: Long, completed: Boolean) =
    completed || now >= session.endAt

/** Confirmed registrations only; replace these profiles with A/C data when integrating. */
data class PrototypeAttendee(val userId: String, val name: String, val checkedInAt: Long? = null, val avatarUri: String = "", val organizer: Boolean = false)

internal val prototypeParticipants = listOf(
    PrototypeAttendee("alice", "Alice"), PrototypeAttendee("bob", "Bob"),
    PrototypeAttendee("carol", "Carol"), PrototypeAttendee("david", "David"),
)

interface PrototypeStore {
    var hostedSession: PrototypeSession?
    fun activityInfo(id: String): CheckInActivityInfo? = null
    fun rememberActivityInfo(info: CheckInActivityInfo) {}
    /** Trusted repository adapter only; preserve receipts, reviews, drafts and member links. */
    fun updateLinkedActivity(info: CheckInActivityInfo) {
        throw IllegalArgumentException("This store cannot update linked event details.")
    }
    fun registrationSnapshot(id: String): CheckInRegistrationSnapshot? = null
    fun rememberRegistrations(id: String, snapshot: CheckInRegistrationSnapshot) {}
    fun isCancelled(id: String): Boolean
    fun cancel(id: String)
    fun receipt(sessionId: String, userId: String): PrototypeReceipt?
    fun save(receipt: PrototypeReceipt)
    fun rememberSession(userId: String, session: PrototypeSession)
    fun sessions(userId: String): List<PrototypeSession>
    fun feedback(sessionId: String, userId: String): PrototypeFeedback?
    fun saveFeedback(feedback: PrototypeFeedback)
    fun isDeleted(id: String): Boolean = false
    fun isBlocked(viewer: String, author: String): Boolean = false
    fun feedbackForSession(sessionId: String): List<PrototypeFeedback>
}

/** A/B/C/backend seam. These calls will become authenticated network requests. */
interface PrototypeCheckInBackend {
    fun hostedSession(): PrototypeSession?
    fun activityInfo(session: PrototypeSession): CheckInActivityInfo = LegacyCheckInActivities.activity(session)
    fun hostedSession(actor: String): PrototypeSession? = hostedSession()?.takeIf { activityInfo(it).organizerId == actor }
    fun hostedSession(actor: String, session: PrototypeSession): PrototypeSession? = hostedSession(actor)?.takeIf { it == session }
    fun createMeeting(actor: String): PrototypeSession
    fun issue(actor: String, session: PrototypeSession): PrototypeCode
    fun submit(actor: String, raw: String, registrationKey: String? = null): PrototypeReceipt
    fun cancel(actor: String, session: PrototypeSession)
    fun isCancelled(session: PrototypeSession): Boolean
    fun isCheckInClosed(session: PrototypeSession): Boolean = isCancelled(session)
    fun attendance(actor: String, session: PrototypeSession): List<PrototypeAttendee>
    fun rememberCode(actor: String, raw: String): PrototypeSession
    fun history(actor: String): List<PrototypeHistoryEntry>
    fun reviews(actor: String, session: PrototypeSession): List<VisibleFeedback>
    fun submitFeedback(actor: String, sessionId: String, rating: Int, comment: String,
        details: FeedbackDetails = FeedbackDetails()): PrototypeFeedback
}

class LocalPrototypeBackend(private val store: PrototypeStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val registrations: CheckInRegistrationSource = MockCheckInRegistrations,
    private val activities: CheckInActivitySource = LegacyCheckInActivities,
    private val completed: (PrototypeSession) -> Boolean = { false },
    private val userName: (String) -> String = { id -> prototypeParticipants.find { it.userId == id }?.name ?: "Participant" },
    private val userAvatar: (String) -> String = { "" },
    private val receiptLookup: ((String, String) -> PrototypeReceipt?)? = null,
) : PrototypeCheckInBackend {
    private val scopedRegistrations = if (registrations === MockCheckInRegistrations) StoredMockCheckInRegistrations(store) else registrations
    override fun hostedSession() = store.hostedSession
    override fun activityInfo(session: PrototypeSession): CheckInActivityInfo {
        val info = if (activities === LegacyCheckInActivities) store.activityInfo(session.id) ?: activities.activity(session)
            else activities.activity(session)
        require(info.session == session) { "Event details have changed. Reopen the event." }
        if (activities !== LegacyCheckInActivities) {
            val saved = store.activityInfo(session.id)
            if (saved == null) store.rememberActivityInfo(info)
            else require(saved.session == info.session && saved.organizerId == info.organizerId &&
                saved.sourceEventId == info.sourceEventId) { "The organizer or meeting point has changed. Reopen the event." }
        }
        return info
    }
    private fun catalogActivity(session: PrototypeSession) = activities.isCatalogActivity(session) ||
        (activities === LegacyCheckInActivities && store.activityInfo(session.id)?.session == session)
    private fun owns(actor: String, session: PrototypeSession) = runCatching {
        actor.isNotBlank() && activityInfo(session).organizerId == actor &&
            (store.hostedSession == session || session in store.sessions(actor))
    }.getOrDefault(false)
    override fun hostedSession(actor: String): PrototypeSession? = store.hostedSession?.takeIf { owns(actor, it) }
        ?: store.sessions(actor).filter { catalogActivity(it) && owns(actor, it) }.maxByOrNull { it.startAt }
    override fun hostedSession(actor: String, session: PrototypeSession): PrototypeSession? =
        if (catalogActivity(session) && owns(actor, session)) session else hostedSession(actor)?.takeIf { it == session }
    override fun isCancelled(session: PrototypeSession) = store.isCancelled(session.id)
    private fun currentReceipt(sessionId: String, actor: String): PrototypeReceipt? =
        (receiptLookup?.invoke(sessionId, actor) ?: store.receipt(sessionId, actor))?.takeIf { !store.isCancelled(sessionId) && scopedRegistrations.isCurrentReceipt(it) }

    private fun requireConfirmed(actor: String, activityId: String) {
        val status = scopedRegistrations.status(activityId, actor)
        require(status == CheckInRegistration.CONFIRMED) {
            if (status == CheckInRegistration.WAITLISTED) "You are waitlisted and cannot check in or review yet." else "You do not have a confirmed place at this event."
        }
    }
    override fun rememberCode(actor: String, raw: String): PrototypeSession {
        val code = PrototypeCodeCodec.decode(raw)
        activityInfo(code.session)
        requireConfirmed(actor, code.session.id)
        code.requireValid(now())
        require(!isCancelled(code.session)) { "This event has been cancelled." }
        // Keep the activity record even if submission fails; it does not grant review eligibility.
        store.rememberSession(actor, code.session)
        return code.session
    }
    override fun history(actor: String): List<PrototypeHistoryEntry> = store.sessions(actor)
        .map { PrototypeHistoryEntry(it, currentReceipt(it.id, actor), store.feedback(it.id, actor), isCancelled(it),
            completed(it), scopedRegistrations.status(it.id, actor) == CheckInRegistration.CONFIRMED) }
        .sortedByDescending { it.session.startAt }

    override fun reviews(actor: String, session: PrototypeSession): List<VisibleFeedback> {
        require(actor.isNotBlank() && session in store.sessions(actor)) { "Open the event record for your current account to review." }
        val organizer = activityInfo(session).organizerId
        return store.feedbackForSession(session.id).filterNot { store.isBlocked(actor,it.userId) }.mapNotNull { it.visibleTo(actor, organizer, userName(it.userId), userAvatar(it.userId)) }.sortedByDescending { it.createdAt }
    }

    @Synchronized override fun submitFeedback(actor: String, sessionId: String, rating: Int, comment: String,
        details: FeedbackDetails): PrototypeFeedback {
        requireConfirmed(actor, sessionId)
        require(currentReceipt(sessionId, actor) != null) { "Check in at this event before leaving a review." }
        val session = requireNotNull(store.sessions(actor).find { it.id == sessionId }) { "Your event record could not be found." }
        activityInfo(session)
        require(!isCancelled(session)) { "This event is cancelled. You cannot review it." }
        val time = now()
        require(feedbackHasEnded(session, time, completed(session))) { "You can review after the event ends." }
        require(rating in 1..5) { "Choose a rating from 1 to 5." }
        require(comment.length <= 100) { "Comments must be 100 characters or less." }
        details.requireValid(maxTextLength = 100)
        require(actor != activityInfo(session).organizerId || !details.anonymous) { "Organizers must use a named review." }
        val feedback = PrototypeFeedback(sessionId, actor, rating, comment.trim(), time,
            details.copy(tags = details.tags.toList(), photos = details.photos.toList()), publicId = java.util.UUID.randomUUID().toString())
        store.saveFeedback(feedback)
        return feedback
    }
    override fun attendance(actor: String, session: PrototypeSession): List<PrototypeAttendee> {
        require(owns(actor, session)) {
            "Only this event's organizer can view attendance."
        }
        return scopedRegistrations.participants(session.id).map { it.copy(checkedInAt = currentReceipt(session.id, it.userId)?.checkedInAt, organizer = it.userId == activityInfo(session).organizerId) }
    }
    override fun createMeeting(actor: String): PrototypeSession {
        require(activities === LegacyCheckInActivities && actor == "alice") { "Open this event from its details page." }
        val time = now()
        return PrototypeSession(UUID.randomUUID().toString(), time + 600_000, time + 7_800_000)
            .also { store.rememberSession(actor, it); store.hostedSession = it }
    }
    override fun issue(actor: String, session: PrototypeSession): PrototypeCode {
        require(owns(actor, session)) { "Only this event's organizer can show a check-in code." }
        require(hostedSession(actor, session) == session) { "The test event has changed. Reopen this page." }
        require(!isCancelled(session)) { "Event cancelled. No more codes will be issued." }
        val time = now()
        session.requireWindow(time)
        requireConfirmed(actor, session.id)
        val code = PrototypeCode(session, time, minOf(time + PrototypePolicy.CODE_TTL_MS,
            session.endAt, session.startAt + PrototypePolicy.HALF_HOUR_MS + 1))
        if (currentReceipt(session.id, actor) == null) {
            store.save(PrototypeReceipt(session.id, actor, time,
                scopedRegistrations.registrationKey(session.id, actor), organizer = true))
        }
        return code
    }
    @Synchronized override fun submit(actor: String, raw: String, registrationKey: String?): PrototypeReceipt {
        val code = PrototypeCodeCodec.decode(raw)
        activityInfo(code.session)
        requireConfirmed(actor, code.session.id)
        val time = now()
        code.requireValid(time)
        require(!isCancelled(code.session)) { "This event is cancelled. You cannot check in." }
        require(actor != activityInfo(code.session).organizerId) { "Confirm your arrival when showing the code." }
        val currentKey = scopedRegistrations.registrationKey(code.session.id, actor)
        require(registrationKey == null || registrationKey == currentKey) { "Your registration changed. Scan again." }
        require(currentReceipt(code.session.id, actor) == null) { "You are already checked in." }
        store.rememberSession(actor, code.session)
        return PrototypeReceipt(code.session.id, actor, time, currentKey).also(store::save)
    }
    override fun cancel(actor: String, session: PrototypeSession) {
        require(owns(actor, session) && hostedSession(actor, session) == session) { "Only the organizer can cancel this event." }
        require(now() < session.startAt && !isCancelled(session)) { "Events can only be cancelled before they start." }
        store.cancel(session.id)
    }
}

package com.gogether.app.feature.checkin.prototype

import java.util.UUID
import kotlin.math.*

/** Prototype-only policy. The radius is agreed; accuracy/age/TTL are adjustable trial values. */
object PrototypePolicy {
    const val RADIUS_METERS = 200.0
    const val MAX_ACCURACY_METERS = 50.0
    const val MAX_FIX_AGE_MS = 15_000L
    const val CODE_TTL_MS = 30_000L
    const val REFRESH_MS = 10_000L
    const val HALF_HOUR_MS = 1_800_000L
}

data class MeetingPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0) { "Invalid meeting point coordinates." }
    }
    // Android LocationManager and this prototype use WGS84 throughout; no map SDK conversion.
    fun distanceTo(other: MeetingPoint): Double {
        val lat = Math.toRadians(other.latitude - latitude)
        val lon = Math.toRadians(other.longitude - longitude)
        val a = sin(lat / 2).pow(2) + cos(Math.toRadians(latitude)) *
            cos(Math.toRadians(other.latitude)) * sin(lon / 2).pow(2)
        return 6_371_000.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

data class PositionFix(val point: MeetingPoint, val accuracyMeters: Double,
                       val elapsedMillis: Long, val simulated: Boolean = false)

fun validatePosition(fix: PositionFix, elapsedNow: Long, meeting: MeetingPoint? = null): Double {
    require(!fix.simulated) { "Mock location detected. Use your phone's real location." }
    require(fix.accuracyMeters.isFinite() && fix.accuracyMeters in 0.0..PrototypePolicy.MAX_ACCURACY_METERS) {
        "Location is not accurate enough. Try in an open area; accuracy must be within 50 m."
    }
    require(fix.elapsedMillis >= 0 && elapsedNow >= fix.elapsedMillis && elapsedNow - fix.elapsedMillis <= PrototypePolicy.MAX_FIX_AGE_MS) {
        "Your location is out of date. Get a new location."
    }
    val distance = meeting?.distanceTo(fix.point) ?: 0.0
    require(distance <= PrototypePolicy.RADIUS_METERS + 0.000001) {
        "You are about ${distance.roundToInt()} m away. Move within 200 m and try again."
    }
    return distance
}

data class PrototypeSession(val id: String, val meeting: MeetingPoint?, val startAt: Long, val endAt: Long) {
    init {
        require(UUID.fromString(id).toString() == id) { "Invalid event ID." }
        require(startAt in 1..4_102_444_800_000L && endAt > startAt && endAt - startAt <= 86_400_000) {
            "Invalid event time."
        }
    }
    fun requireWindow(now: Long) {
        requireNotNull(meeting) { "The check-in meeting point has not been set." }
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

/** Portable mock event data, NOT signed or trusted. Replace the whole codec/backend for production. */
object PrototypeCodeCodec {
    private const val PREFIX = "GOGETHER-LOCAL-QR-1"
    fun encode(code: PrototypeCode): String = with(code) {
        val point = requireNotNull(session.meeting) { "A check-in code requires a meeting point." }
        listOf(PREFIX, session.id, point.latitude, point.longitude,
            session.startAt, session.endAt, issuedAt, expiresAt).joinToString("|")
    }
    fun decode(raw: String): PrototypeCode {
        require(raw.length <= 512) { "This is not a valid Gogether check-in code." }
        try {
            val p = raw.split('|')
            require(p.size == 8 && p[0] == PREFIX)
            return PrototypeCode(PrototypeSession(p[1], MeetingPoint(p[2].toDouble(), p[3].toDouble()),
                p[4].toLong(), p[5].toLong()), p[6].toLong(), p[7].toLong())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid Gogether check-in code. Scan the code shown by the organizer.")
        }
    }
}

data class PrototypeReceipt(val sessionId: String, val userId: String, val checkedInAt: Long)
data class PrototypeFeedback(val sessionId: String, val userId: String, val rating: Int,
    val comment: String, val createdAt: Long, val details: FeedbackDetails = FeedbackDetails())
data class PrototypeHistoryEntry(val session: PrototypeSession, val receipt: PrototypeReceipt?,
    val feedback: PrototypeFeedback?, val cancelled: Boolean,
    val completed: Boolean = false, val eligible: Boolean = true) {
    fun hasEnded(now: Long) = feedbackHasEnded(session, now, completed)
}

internal fun feedbackHasEnded(session: PrototypeSession, now: Long, completed: Boolean) =
    completed || now >= session.endAt

/** Confirmed registrations only; replace these profiles with A/C data when integrating. */
data class PrototypeAttendee(val userId: String, val name: String, val checkedInAt: Long? = null, val avatarUri: String = "")

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
    fun feedbackForSession(sessionId: String): List<PrototypeFeedback>
}

/** A/B/C/backend seam. These calls will become authenticated network requests. */
interface PrototypeCheckInBackend {
    fun hostedSession(): PrototypeSession?
    fun activityInfo(session: PrototypeSession): CheckInActivityInfo = LegacyCheckInActivities.activity(session)
    fun hostedSession(actor: String): PrototypeSession? = hostedSession()?.takeIf { activityInfo(it).organizerId == actor }
    fun hostedSession(actor: String, session: PrototypeSession): PrototypeSession? = hostedSession(actor)?.takeIf { it == session }
    fun createMeeting(actor: String, fix: PositionFix): PrototypeSession
    fun issue(actor: String, session: PrototypeSession, fix: PositionFix): PrototypeCode
    fun submit(actor: String, raw: String, fix: PositionFix): PrototypeReceipt
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
    private val elapsedNow: () -> Long,
    private val registrations: CheckInRegistrationSource = MockCheckInRegistrations,
    private val activities: CheckInActivitySource = LegacyCheckInActivities,
    private val completed: (PrototypeSession) -> Boolean = { false },
    private val userName: (String) -> String = { id -> prototypeParticipants.find { it.userId == id }?.name ?: "Participant" },
    private val userAvatar: (String) -> String = { "" },
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
        // Mock C membership is remembered even when location fails: feedback does not require check-in.
        store.rememberSession(actor, code.session)
        return code.session
    }
    override fun history(actor: String): List<PrototypeHistoryEntry> = store.sessions(actor)
        .map { PrototypeHistoryEntry(it, store.receipt(it.id, actor), store.feedback(it.id, actor), isCancelled(it),
            completed(it), scopedRegistrations.status(it.id, actor) == CheckInRegistration.CONFIRMED) }
        .sortedByDescending { it.session.startAt }

    override fun reviews(actor: String, session: PrototypeSession): List<VisibleFeedback> {
        require(actor.isNotBlank() && session in store.sessions(actor)) { "Open the event record for your current account to review." }
        val organizer = activityInfo(session).organizerId
        return store.feedbackForSession(session.id).mapNotNull { it.visibleTo(actor, organizer, userName(it.userId), userAvatar(it.userId)) }.sortedByDescending { it.createdAt }
    }

    @Synchronized override fun submitFeedback(actor: String, sessionId: String, rating: Int, comment: String,
        details: FeedbackDetails): PrototypeFeedback {
        requireConfirmed(actor, sessionId)
        val session = requireNotNull(store.sessions(actor).find { it.id == sessionId }) { "Your event record could not be found." }
        activityInfo(session)
        require(!isCancelled(session)) { "This event is cancelled. You cannot review it." }
        val time = now()
        require(feedbackHasEnded(session, time, completed(session))) { "You can review after the event ends." }
        require(rating in 1..5) { "Choose a rating from 1 to 5." }
        require(comment.length <= 100) { "Comments must be 100 characters or less." }
        details.requireValid(maxTextLength = 100)
        val feedback = PrototypeFeedback(sessionId, actor, rating, comment.trim(), time,
            details.copy(tags = details.tags.toList(), photos = details.photos.toList()))
        store.saveFeedback(feedback)
        return feedback
    }
    override fun attendance(actor: String, session: PrototypeSession): List<PrototypeAttendee> {
        require(owns(actor, session)) {
            "Only this event's organizer can view attendance."
        }
        return scopedRegistrations.participants(session.id).map { it.copy(checkedInAt = store.receipt(session.id, it.userId)?.checkedInAt) }
    }
    override fun createMeeting(actor: String, fix: PositionFix): PrototypeSession {
        require(activities === LegacyCheckInActivities && actor == "alice") { "Open this event from its details page." }
        validatePosition(fix, elapsedNow())
        val time = now()
        return PrototypeSession(UUID.randomUUID().toString(), fix.point, time + 600_000, time + 7_800_000)
            .also { store.rememberSession(actor, it); store.hostedSession = it }
    }
    override fun issue(actor: String, session: PrototypeSession, fix: PositionFix): PrototypeCode {
        require(owns(actor, session)) { "Only this event's organizer can show a check-in code." }
        require(hostedSession(actor, session) == session) { "The test event has changed. Reopen this page." }
        require(!isCancelled(session)) { "Event cancelled. No more codes will be issued." }
        val time = now()
        session.requireWindow(time)
        validatePosition(fix, elapsedNow(), session.meeting)
        return PrototypeCode(session, time, minOf(time + PrototypePolicy.CODE_TTL_MS,
            session.endAt, session.startAt + PrototypePolicy.HALF_HOUR_MS + 1))
    }
    @Synchronized override fun submit(actor: String, raw: String, fix: PositionFix): PrototypeReceipt {
        val code = PrototypeCodeCodec.decode(raw)
        activityInfo(code.session)
        requireConfirmed(actor, code.session.id)
        val time = now()
        code.requireValid(time)
        require(!isCancelled(code.session)) { "This event is cancelled. You cannot check in." }
        validatePosition(fix, elapsedNow(), code.session.meeting)
        require(store.receipt(code.session.id, actor) == null) { "You are already checked in." }
        store.rememberSession(actor, code.session)
        return PrototypeReceipt(code.session.id, actor, time).also(store::save)
    }
    override fun cancel(actor: String, session: PrototypeSession) {
        require(owns(actor, session) && hostedSession(actor, session) == session) { "Only the organizer can cancel this event." }
        require(now() < session.startAt && !isCancelled(session)) { "Events can only be cancelled before they start." }
        store.cancel(session.id)
    }
}

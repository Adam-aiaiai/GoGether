package com.gogether.app.feature.checkin.prototype

/** The Profile entry preserves old offline demos while routing linked events through C.
 * A formerly linked event never falls back to the legacy Alice/mock roster when it disappears.
 */
class CombinedCheckInBackend(private val store: PrototypeStore, private val linked: PrototypeCheckInBackend,
    private val legacy: PrototypeCheckInBackend) : PrototypeCheckInBackend {
    private fun source(id: String) = if (store.activityInfo(id)?.sourceEventId != null) linked else legacy
    private fun source(session: PrototypeSession) = source(session.id)
    override fun hostedSession() = legacy.hostedSession()
    override fun hostedSession(actor: String) = legacy.hostedSession(actor)?.takeIf { source(it) === legacy }
        ?: linked.hostedSession(actor)
    override fun hostedSession(actor: String, session: PrototypeSession) = source(session).hostedSession(actor, session)
    override fun activityInfo(session: PrototypeSession) = source(session).activityInfo(session)
    override fun createMeeting(actor: String, fix: PositionFix) = legacy.createMeeting(actor, fix)
    override fun issue(actor: String, session: PrototypeSession, fix: PositionFix) = source(session).issue(actor, session, fix)
    override fun submit(actor: String, raw: String, fix: PositionFix) = source(PrototypeCodeCodec.decode(raw).session).submit(actor, raw, fix)
    override fun rememberCode(actor: String, raw: String) = source(PrototypeCodeCodec.decode(raw).session).rememberCode(actor, raw)
    override fun cancel(actor: String, session: PrototypeSession) = source(session).cancel(actor, session)
    override fun isCancelled(session: PrototypeSession) = source(session).isCancelled(session)
    override fun isCheckInClosed(session: PrototypeSession) = source(session).isCheckInClosed(session)
    override fun attendance(actor: String, session: PrototypeSession) = source(session).attendance(actor, session)
    override fun history(actor: String) = (legacy.history(actor).filter { source(it.session) === legacy } + linked.history(actor))
        .distinctBy { it.session.id }.sortedByDescending { it.session.startAt }
    override fun reviews(actor: String, session: PrototypeSession) = source(session).reviews(actor, session)
    override fun submitFeedback(actor: String, sessionId: String, rating: Int, comment: String, details: FeedbackDetails) =
        source(sessionId).submitFeedback(actor, sessionId, rating, comment, details)
}

class CombinedCheckInRegistrations(private val store: PrototypeStore, private val linked: CheckInRegistrationSource,
    private val legacy: CheckInRegistrationSource) : CheckInRegistrationSource {
    private fun source(id: String?) = if (id != null && store.activityInfo(id)?.sourceEventId != null) linked else legacy
    override fun status(activityId: String?, userId: String) = source(activityId).status(activityId, userId)
    override fun participants(activityId: String?) = source(activityId).participants(activityId)
}

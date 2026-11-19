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
            longitude.isFinite() && longitude in -180.0..180.0) { "集合点坐标无效。" }
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
    require(!fix.simulated) { "检测到模拟位置，请用真实手机定位试用。" }
    require(fix.accuracyMeters.isFinite() && fix.accuracyMeters in 0.0..PrototypePolicy.MAX_ACCURACY_METERS) {
        "定位精度不足，请到开阔处重新定位（试验要求误差估计不超过 50 米）。"
    }
    require(fix.elapsedMillis >= 0 && elapsedNow >= fix.elapsedMillis && elapsedNow - fix.elapsedMillis <= PrototypePolicy.MAX_FIX_AGE_MS) {
        "位置已过期，请重新定位。"
    }
    val distance = meeting?.distanceTo(fix.point) ?: 0.0
    require(distance <= PrototypePolicy.RADIUS_METERS + 0.000001) {
        "距集合点约 ${distance.roundToInt()} 米，超过 200 米，请靠近后重试。"
    }
    return distance
}

data class PrototypeSession(val id: String, val meeting: MeetingPoint, val startAt: Long, val endAt: Long) {
    init {
        require(UUID.fromString(id).toString() == id) { "活动编号无效。" }
        require(startAt in 1..4_102_444_800_000L && endAt > startAt && endAt - startAt <= 86_400_000) {
            "活动时间无效。"
        }
    }
    fun requireWindow(now: Long) {
        require(now < endAt) { "活动已结束，不能签到。" }
        require(now >= startAt - PrototypePolicy.HALF_HOUR_MS) { "尚未到签到时间。" }
        require(now <= startAt + PrototypePolicy.HALF_HOUR_MS) { "签到窗口已结束。" }
    }
}

data class PrototypeCode(val session: PrototypeSession, val issuedAt: Long, val expiresAt: Long) {
    fun requireValid(now: Long) {
        session.requireWindow(now)
        require(issuedAt >= session.startAt - PrototypePolicy.HALF_HOUR_MS &&
            issuedAt <= session.startAt + PrototypePolicy.HALF_HOUR_MS &&
            expiresAt > issuedAt && expiresAt - issuedAt <= PrototypePolicy.CODE_TTL_MS &&
            expiresAt <= session.endAt && expiresAt <= session.startAt + PrototypePolicy.HALF_HOUR_MS + 1) {
            "二维码时间数据无效，请重新扫码。"
        }
        require(now >= issuedAt - 5_000) { "两台手机时间不一致，请开启自动日期和时间。" }
        require(now < expiresAt) { "二维码已过期，请扫描组织者的最新二维码。" }
    }
}

/** Portable mock event data, NOT signed or trusted. Replace the whole codec/backend for production. */
object PrototypeCodeCodec {
    private const val PREFIX = "GOGETHER-LOCAL-QR-1"
    fun encode(code: PrototypeCode): String = with(code) {
        listOf(PREFIX, session.id, session.meeting.latitude, session.meeting.longitude,
            session.startAt, session.endAt, issuedAt, expiresAt).joinToString("|")
    }
    fun decode(raw: String): PrototypeCode {
        require(raw.length <= 512) { "不是有效的 Gogether 原型签到码。" }
        try {
            val p = raw.split('|')
            require(p.size == 8 && p[0] == PREFIX)
            return PrototypeCode(PrototypeSession(p[1], MeetingPoint(p[2].toDouble(), p[3].toDouble()),
                p[4].toLong(), p[5].toLong()), p[6].toLong(), p[7].toLong())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("不是有效的 Gogether 原型签到码，请扫描组织者的活动码。")
        }
    }
}

data class PrototypeReceipt(val sessionId: String, val userId: String, val checkedInAt: Long)
data class PrototypeFeedback(val sessionId: String, val userId: String, val rating: Int,
    val comment: String, val createdAt: Long, val details: FeedbackDetails = FeedbackDetails())
data class PrototypeHistoryEntry(val session: PrototypeSession, val receipt: PrototypeReceipt?,
    val feedback: PrototypeFeedback?, val cancelled: Boolean)

/** Confirmed registrations only; replace these profiles with A/C data when integrating. */
data class PrototypeAttendee(val userId: String, val name: String, val checkedInAt: Long? = null)

internal val prototypeParticipants = listOf(
    PrototypeAttendee("alice", "Alice"), PrototypeAttendee("bob", "Bob"),
    PrototypeAttendee("carol", "Carol"), PrototypeAttendee("david", "David"),
)

interface PrototypeStore {
    var hostedSession: PrototypeSession?
    fun activityInfo(id: String): CheckInActivityInfo? = null
    fun rememberActivityInfo(info: CheckInActivityInfo) {}
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
) : PrototypeCheckInBackend {
    private val scopedRegistrations = if (registrations === MockCheckInRegistrations) StoredMockCheckInRegistrations(store) else registrations
    override fun hostedSession() = store.hostedSession
    override fun activityInfo(session: PrototypeSession): CheckInActivityInfo {
        val info = if (activities === LegacyCheckInActivities) store.activityInfo(session.id) ?: activities.activity(session)
            else activities.activity(session)
        require(info.session == session) { "活动资料已变化，请重新进入。" }
        if (activities !== LegacyCheckInActivities) store.rememberActivityInfo(info)
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
            if (status == CheckInRegistration.WAITLISTED) "当前账号是候补，不能签到或评价。" else "当前账号未正式报名此活动。"
        }
    }
    override fun rememberCode(actor: String, raw: String): PrototypeSession {
        val code = PrototypeCodeCodec.decode(raw)
        activityInfo(code.session)
        requireConfirmed(actor, code.session.id)
        code.requireValid(now())
        require(!isCancelled(code.session)) { "活动已取消。" }
        // Mock C membership is remembered even when location fails: feedback does not require check-in.
        store.rememberSession(actor, code.session)
        return code.session
    }
    override fun history(actor: String): List<PrototypeHistoryEntry> = store.sessions(actor)
        .map { PrototypeHistoryEntry(it, store.receipt(it.id, actor), store.feedback(it.id, actor), isCancelled(it)) }
        .sortedByDescending { it.session.startAt }

    override fun reviews(actor: String, session: PrototypeSession): List<VisibleFeedback> {
        require(actor.isNotBlank() && session in store.sessions(actor)) { "请从当前账号的活动记录进入评价。" }
        val organizer = activityInfo(session).organizerId
        return store.feedbackForSession(session.id).mapNotNull { it.visibleTo(actor, organizer) }.sortedByDescending { it.createdAt }
    }

    @Synchronized override fun submitFeedback(actor: String, sessionId: String, rating: Int, comment: String,
        details: FeedbackDetails): PrototypeFeedback {
        requireConfirmed(actor, sessionId)
        val session = requireNotNull(store.sessions(actor).find { it.id == sessionId }) { "未找到你的活动记录。" }
        activityInfo(session)
        require(!isCancelled(session)) { "活动已取消，不能评价。" }
        val time = now()
        require(time >= session.endAt) { "活动结束后才能评价。" }
        require(rating in 1..5) { "请选择 1 至 5 分。" }
        require(comment.length <= 500) { "评论不能超过 500 字。" }
        details.requireValid()
        val feedback = PrototypeFeedback(sessionId, actor, rating, comment.trim(), time,
            details.copy(tags = details.tags.toList(), photos = details.photos.toList()))
        store.saveFeedback(feedback)
        return feedback
    }
    override fun attendance(actor: String, session: PrototypeSession): List<PrototypeAttendee> {
        require(owns(actor, session)) {
            "只有组织者可以查看本场名单。"
        }
        return scopedRegistrations.participants(session.id).map { it.copy(checkedInAt = store.receipt(session.id, it.userId)?.checkedInAt) }
    }
    override fun createMeeting(actor: String, fix: PositionFix): PrototypeSession {
        require(activities === LegacyCheckInActivities && actor == "alice") { "请通过活动资料提供本场活动。" }
        validatePosition(fix, elapsedNow())
        val time = now()
        return PrototypeSession(UUID.randomUUID().toString(), fix.point, time + 600_000, time + 7_800_000)
            .also { store.rememberSession(actor, it); store.hostedSession = it }
    }
    override fun issue(actor: String, session: PrototypeSession, fix: PositionFix): PrototypeCode {
        require(owns(actor, session)) { "只有本场组织者可以出示签到码。" }
        require(hostedSession(actor, session) == session) { "测试活动已更换，请重新打开页面。" }
        require(!isCancelled(session)) { "活动已取消，停止出码。" }
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
        require(!isCancelled(code.session)) { "活动已取消，不能签到。" }
        validatePosition(fix, elapsedNow(), code.session.meeting)
        require(store.receipt(code.session.id, actor) == null) { "你已签到，无需重复提交。" }
        store.rememberSession(actor, code.session)
        return PrototypeReceipt(code.session.id, actor, time).also(store::save)
    }
    override fun cancel(actor: String, session: PrototypeSession) {
        require(owns(actor, session) && hostedSession(actor, session) == session) { "只有组织者可以取消。" }
        require(now() < session.startAt && !isCancelled(session)) { "沿用现有规则：只能在开始前取消活动。" }
        store.cancel(session.id)
    }
}

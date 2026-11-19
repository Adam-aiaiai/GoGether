package com.gogether.app.feature.checkin.prototype

/** E-only network seam. A missing/stale read is Unknown, never permission to submit again. */
data class CheckInAttempt(val id: String, val actor: String, val session: PrototypeSession)

sealed interface CheckInResolution {
    data class Saved(val receipt: PrototypeReceipt) : CheckInResolution
    /** The service guarantees this attempt cannot subsequently commit. */
    data object NotSubmitted : CheckInResolution
    data object Unknown : CheckInResolution
}

class CheckInRejected(message: String) : Exception(message)

interface CheckInSubmissionSource {
    suspend fun submit(attempt: CheckInAttempt, raw: String, fix: PositionFix): PrototypeReceipt
    suspend fun resolve(attempt: CheckInAttempt): CheckInResolution
}

/** Synchronous local writes are final before this adapter returns. Not a remote HTTP adapter. */
class LocalCheckInSubmissionSource(private val backend: PrototypeCheckInBackend) : CheckInSubmissionSource {
    override suspend fun submit(attempt: CheckInAttempt, raw: String, fix: PositionFix): PrototypeReceipt {
        try {
            require(PrototypeCodeCodec.decode(raw).session == attempt.session) { "活动信息不一致，请重新扫码。" }
            return backend.submit(attempt.actor, raw, fix)
        } catch (e: IllegalArgumentException) { throw CheckInRejected(e.message ?: "签到未通过校验。") }
    }

    override suspend fun resolve(attempt: CheckInAttempt): CheckInResolution {
        val record = backend.history(attempt.actor).find { it.session == attempt.session }
        return record?.receipt?.let { CheckInResolution.Saved(it) } ?: CheckInResolution.NotSubmitted
    }
}

interface PendingCheckInStore {
    fun get(actor: String, sessionId: String): CheckInAttempt?
    fun save(attempt: CheckInAttempt)
    fun remove(attempt: CheckInAttempt)
}

class MemoryPendingCheckInStore : PendingCheckInStore {
    private val pending = mutableMapOf<Pair<String, String>, CheckInAttempt>()
    override fun get(actor: String, sessionId: String) = pending[actor to sessionId]
    override fun save(attempt: CheckInAttempt) {
        val key = attempt.actor to attempt.session.id
        check(pending[key] == null || pending[key] == attempt) { "请先核对上次签到结果。" }
        pending[key] = attempt
    }
    override fun remove(attempt: CheckInAttempt) {
        if (get(attempt.actor, attempt.session.id) == attempt) pending.remove(attempt.actor to attempt.session.id)
    }
}

package com.gogether.app.feature.checkin.prototype

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.util.UUID

data class PrototypeNotice(val id: Long, val text: String)
enum class ParticipantScanStage { READY, VERIFYING, RESOLVING, SUCCESS, ATTENTION }

data class PrototypeUiState(
    val session: PrototypeSession? = null,
    val code: PrototypeCode? = null,
    val scanned: PrototypeCode? = null,
    val candidate: PositionFix? = null,
    val receipt: PrototypeReceipt? = null,
    val busy: Boolean = false,
    val message: String = "请选择操作。",
    val cancelled: Boolean = false,
    val checkInClosed: Boolean = false,
    val attendees: List<PrototypeAttendee> = emptyList(),
    val history: List<PrototypeHistoryEntry> = emptyList(),
    val notice: PrototypeNotice? = null,
    val scanStage: ParticipantScanStage = ParticipantScanStage.READY,
    val locationRetryAvailable: Boolean = false,
    val pendingSubmission: CheckInAttempt? = null,
    val participantSession: PrototypeSession? = null,
    val ownerUserId: String? = null,
    val identityExpired: Boolean = false,
    val entry: CheckInEntry = CheckInEntry.General,
    val registration: CheckInRegistration? = null,
    val participants: List<PrototypeAttendee>? = null,
    val attendance: AttendanceLoadState = AttendanceLoadState(),
    val reviewSessionId: String? = null,
    val reviews: List<VisibleFeedback> = emptyList(),
)

class PrototypeViewModel(
    val actor: String,
    private val backend: PrototypeCheckInBackend,
    private val position: PositionSource,
    latestReceipt: PrototypeReceipt? = null,
    private val now: () -> Long = System::currentTimeMillis,
    entry: CheckInEntry = CheckInEntry.General,
    private val identity: CheckInIdentitySource = CheckInIdentitySource { actor },
    private val registrations: CheckInRegistrationSource = MockCheckInRegistrations,
    private val attendanceSource: AttendanceSource = AttendanceSource { user, session -> backend.attendance(user, session) },
    private val submissions: CheckInSubmissionSource = LocalCheckInSubmissionSource(backend),
    private val pendingSubmissions: PendingCheckInStore = MemoryPendingCheckInStore(),
) : ViewModel() {
    val activitySource = object : CheckInActivitySource {
        override fun activity(session: PrototypeSession) = backend.activityInfo(session)
    }
    private val authorized = identity.currentUserId() == actor
    private fun isOrganizer(session: PrototypeSession?) = session != null &&
        runCatching { backend.activityInfo(session).organizerId == actor }.getOrDefault(false)
    private val initial = if (authorized) {
        if (entry is CheckInEntry.Activity) backend.hostedSession(actor, entry.session) else backend.hostedSession(actor)
    } else null
    private val initialHistory = if (authorized) backend.history(actor) else emptyList()
    private val ownLatest = latestReceipt?.takeIf { authorized && it.userId == actor }
    private val initialParticipant = (entry as? CheckInEntry.Activity)?.session
        ?: initialHistory.find { it.session.id == ownLatest?.sessionId }?.session
        ?: initialHistory.firstOrNull()?.session
    private val initialRecord = initialHistory.find { it.session.id == initialParticipant?.id }
    private val initialPending = if (authorized && initialParticipant != null)
        pendingSubmissions.get(actor, initialParticipant.id) else null
    private val mutableState = MutableStateFlow(PrototypeUiState(session = initial,
        receipt = (if (initialRecord != null) initialRecord.takeIf { it.session == initialParticipant }?.receipt else ownLatest)
            ?.takeIf { it.userId == actor && it.sessionId == initialParticipant?.id },
        cancelled = initialParticipant?.let(backend::isCancelled) ?: false,
        checkInClosed = initialParticipant?.let(backend::isCheckInClosed) ?: false,
        history = initialHistory,
        participantSession = if (initial != null || isOrganizer(initialParticipant) || !authorized) null else initialParticipant,
        ownerUserId = actor, identityExpired = !authorized, entry = entry,
        registration = registrations.status(initialParticipant?.id, actor),
        participants = registrations.participants(initialParticipant?.id), pendingSubmission = initialPending,
        scanStage = if (initialPending == null) ParticipantScanStage.READY else ParticipantScanStage.ATTENTION,
        message = if (initialPending == null) "请选择操作。" else "上次签到结果待确认，请先核对。"))
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var displaying: Job? = null
    private var displayGeneration = 0
    private var noticeSequence = 0L
    private var entryGeneration = 0L
    private var scanSequence = 0L
    private var pendingScan: PendingCheckInScan? = null
    private var operationGeneration = 0L
    private val seenCheckIns = mutableSetOf<String>()
    private var attendanceJob: Job? = null
    private var attendanceGeneration = 0L
    private fun update(change: (PrototypeUiState) -> PrototypeUiState) {
        if (!mutableState.value.identityExpired) mutableState.value = change(mutableState.value)
    }

    /** A changed identity invalidates this whole instance, including delayed camera/location callbacks. */
    fun refreshIdentity(): Boolean {
        if (state.value.identityExpired) return false
        if (identity.currentUserId() == actor) return true
        leaveForeground()
        pendingScan = null
        entryGeneration++
        seenCheckIns.clear()
        mutableState.value = PrototypeUiState(ownerUserId = actor, identityExpired = true,
            message = "账号已变化，请重新进入签到。")
        return false
    }

    fun enterActivity(session: PrototypeSession) {
        if (!refreshIdentity()) return
        leaveForeground()
        pendingScan = null
        entryGeneration++
        val records = backend.history(actor)
        val receipt = records.find { it.session == session }?.receipt?.takeIf { it.userId == actor }
        val hosted = backend.hostedSession(actor, session)
        val pending = pendingSubmissions.get(actor, session.id)
        update { it.copy(entry = CheckInEntry.Activity(session),
            session = hosted, participantSession = if (isOrganizer(session)) null else session,
            attendees = emptyList(), attendance = AttendanceLoadState(), reviewSessionId = null, reviews = emptyList(),
            cancelled = backend.isCancelled(session), checkInClosed = backend.isCheckInClosed(session),
            receipt = receipt, history = records, scanned = null, notice = null,
            pendingSubmission = pending, message = if (pending == null) "" else "上次签到结果待确认，请先核对。",
            scanStage = if (pending == null) ParticipantScanStage.READY else ParticipantScanStage.ATTENTION,
            registration = registrations.status(session.id, actor),
            participants = registrations.participants(session.id)) }
    }

    /** Capture the target BEFORE requesting permission or starting the external camera Activity. */
    fun beginScan(general: Boolean = false): Long? {
        if (!refreshIdentity() || state.value.busy || pendingScan != null) return null
        if (!general && state.value.pendingSubmission != null) { resolveCheckIn(); return null }
        update { it.copy(scanned = null, locationRetryAvailable = false) }
        if (general) {
            entryGeneration++
            update { it.copy(entry = CheckInEntry.General, participantSession = null, receipt = null,
                scanned = null, notice = null, pendingSubmission = null, scanStage = ParticipantScanStage.READY, message = "",
                registration = registrations.status(null, actor), participants = registrations.participants(null)) }
        }
        val target = if (general) CheckInEntry.General else (state.value.entry as? CheckInEntry.Activity)
            ?: state.value.participantSession?.let { CheckInEntry.Activity(it) } ?: CheckInEntry.General
        return (++scanSequence).also { pendingScan = PendingCheckInScan(it, target, entryGeneration) }
    }

    fun abandonScan() { pendingScan = null }

    /** Re-read C after a promotion/cancellation; final writes still validate the live source. */
    fun refreshLiveData() {
        if (!refreshIdentity()) return
        val session = state.value.session ?: state.value.participantSession ?: return
        val cancelled = backend.isCancelled(session)
        val closed = backend.isCheckInClosed(session)
        val valid = runCatching { backend.activityInfo(session) }.isSuccess
        if (closed || !valid) stopDisplaying()
        update { it.copy(registration = registrations.status(session.id, actor),
            participants = registrations.participants(session.id), cancelled = cancelled, checkInClosed = closed,
            message = if (!valid) "活动资料已变化，请返回重新进入。" else it.message) }
    }

    fun dismissNotice(id: Long) { update { if (it.notice?.id == id) it.copy(notice = null) else it } }

    /** Independent of QR/location work; a failed refresh keeps the last successful snapshot. */
    fun refreshAttendance(session: PrototypeSession) {
        if (!refreshIdentity()) return
        if (!isOrganizer(session)) {
            stopAttendance()
            update { it.copy(attendees = emptyList(), attendance = AttendanceLoadState(), notice = null) }
            return
        }
        val previous = state.value.attendance
        if (previous.session == session && previous.loading) return
        stopAttendance()
        if (previous.session != session) {
            seenCheckIns.clear()
            update { it.copy(attendees = emptyList(), attendance = AttendanceLoadState(session), notice = null) }
        }
        val generation = attendanceGeneration
        update { it.copy(attendance = it.attendance.copy(loading = true, error = null)) }
        attendanceJob = viewModelScope.launch {
            try {
                val people = attendanceSource.load(actor, session)
                currentCoroutineContext().ensureActive()
                if (!refreshIdentity() || generation != attendanceGeneration) return@launch
                updateAttendance(people, session)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (refreshIdentity() && generation == attendanceGeneration) update {
                    it.copy(attendance = it.attendance.copy(error = "名单加载失败，请重试"))
                }
            } finally {
                if (generation == attendanceGeneration) update { it.copy(attendance = it.attendance.copy(loading = false)) }
            }
        }
    }

    fun stopAttendance() {
        attendanceGeneration++
        attendanceJob?.cancel()
        attendanceJob = null
        update { it.copy(attendance = it.attendance.copy(loading = false)) }
    }

    private fun updateAttendance(people: List<PrototypeAttendee>, session: PrototypeSession) {
        val hadSnapshot = state.value.attendance.loaded
        val arrivals = people.filter { it.checkedInAt != null && seenCheckIns.add(it.userId) }
            .takeIf { hadSnapshot && now() < session.endAt && !backend.isCancelled(session) }.orEmpty()
        val notice = arrivals.firstOrNull()?.let {
            PrototypeNotice(++noticeSequence, if (arrivals.size == 1) "${it.name} 刚刚签到" else "${it.name} 等 ${arrivals.size} 人刚刚签到")
        }
        update { it.copy(attendees = people, notice = notice ?: it.notice,
            attendance = AttendanceLoadState(session, loaded = true, updatedAt = now())) }
    }

    fun message(text: String) { if (!refreshIdentity()) return; update { it.copy(message = text,
        locationRetryAvailable = false,
        notice = if (isOrganizer(it.session)) it.notice else null,
        scanStage = if (isOrganizer(it.session)) it.scanStage else ParticipantScanStage.ATTENTION) } }

    private fun runOperation(scanOperation: Boolean = false, work: suspend () -> Unit) {
        if (!refreshIdentity() || state.value.busy) return
        val generation = ++operationGeneration
        update { it.copy(busy = true) }
        operation = viewModelScope.launch {
            if (!refreshIdentity()) return@launch
            try { work() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == operationGeneration) update { it.copy(code = null, message = e.message ?: "操作失败，请重试。",
                scanStage = if (scanOperation) ParticipantScanStage.ATTENTION else it.scanStage) } }
            finally { if (generation == operationGeneration) update { it.copy(busy = false) } }
        }
    }

    fun locateMeeting() {
        if (state.value.entry is CheckInEntry.Activity) return message("请由活动资料提供本场集合点，不能另建活动替代。")
        stopDisplaying()
        runOperation {
            message("正在定位测试集合点…")
            val fix = position.locate()
            currentCoroutineContext().ensureActive()
            if (!refreshIdentity()) return@runOperation
            update { it.copy(candidate = fix, message = "请确认将当前位置固定为本次测试集合点。") }
        }
    }
    fun dismissMeeting() { update { it.copy(candidate = null) } }
    fun confirmMeeting() = runOperation {
        stopAttendance()
        require(state.value.entry !is CheckInEntry.Activity) { "指定活动入口不能另建活动。" }
        val fix = requireNotNull(state.value.candidate) { "请先定位集合点。" }
        update { it.copy(candidate = null) }
        val session = backend.createMeeting(actor, fix)
        seenCheckIns.clear()
        update { it.copy(session = session, candidate = null, code = null, cancelled = false,
            notice = null,
            attendees = emptyList(), attendance = AttendanceLoadState(),
            history = backend.history(actor),
            message = "测试集合点已固定。活动 10 分钟后开始，可现在试用签到。") }
    }

    fun showCode() {
        if (!refreshIdentity() || state.value.busy) return
        stopDisplaying()
        val session = state.value.session ?: return message("请先确认测试集合点。")
        val generation = displayGeneration
        displaying = viewModelScope.launch {
            try {
                while (true) {
                    update { it.copy(code = null, busy = true, message = "正在确认组织者位置…") }
                    val fix = position.locate()
                    currentCoroutineContext().ensureActive()
                    if (!refreshIdentity()) return@launch
                    val code = backend.issue(actor, session, fix)
                    update { it.copy(code = code, busy = false,
                        message = "位置验证通过，请让参与者扫码。") }
                    delay(PrototypePolicy.REFRESH_MS)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { update { it.copy(code = null, message = e.message ?: "刷新失败，请重试。") } }
            finally { if (generation == displayGeneration) update { it.copy(code = null, busy = false) } }
        }
    }

    fun receiveScan(raw: String?, requestId: Long? = null) {
        if (!refreshIdentity() || state.value.busy) return
        val ticket = if (requestId != null) pendingScan?.takeIf {
            it.id == requestId && it.generation == entryGeneration
        } ?: return else null
        pendingScan = null
        if (state.value.pendingSubmission != null) { resolveCheckIn(); return }
        val target = ticket?.entry ?: state.value.entry
        update { it.copy(scanned = null, notice = null, locationRetryAvailable = false,
            scanStage = ParticipantScanStage.READY, message = "") }
        // Closing the camera is navigation, not a failed check-in. Keep the existing activity/receipt.
        if (raw == null) return
        try {
            val code = PrototypeCodeCodec.decode(raw)
            (target as? CheckInEntry.Activity)?.session?.let { expected ->
                require(code.session.id == expected.id) { "这不是本场活动的签到码，请扫描本场组织者出示的二维码。" }
                require(code.session == expected) { "二维码中的活动信息与当前活动不一致，请重新获取活动信息。" }
            }
            pendingSubmissions.get(actor, code.session.id)?.let { pending ->
                update { it.copy(participantSession = pending.session, pendingSubmission = pending,
                    receipt = null, scanStage = ParticipantScanStage.ATTENTION,
                    registration = registrations.status(pending.session.id, actor),
                    participants = registrations.participants(pending.session.id), history = backend.history(actor)) }
                resolveCheckIn()
                return
            }
            code.requireValid(now())
            if (!refreshIdentity()) return
            backend.rememberCode(actor, raw)
            update { it.copy(scanned = code, participantSession = code.session,
                receipt = it.receipt?.takeIf { receipt -> receipt.userId == actor && receipt.sessionId == code.session.id },
                registration = registrations.status(code.session.id, actor), participants = registrations.participants(code.session.id),
                history = backend.history(actor), scanStage = ParticipantScanStage.VERIFYING,
                message = "已识别本场活动，正在获取精确位置…") }
            runOperation(scanOperation = true) {
                val fix = try { position.locate() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (refreshIdentity()) update { it.copy(locationRetryAvailable = true) }
                    throw e
                }
                currentCoroutineContext().ensureActive()
                if (!refreshIdentity()) return@runOperation
                update { it.copy(message = "正在核验距离、报名资格与签到时间…") }
                val attempt = CheckInAttempt(UUID.randomUUID().toString(), actor, code.session)
                pendingSubmissions.save(attempt)
                update { it.copy(pendingSubmission = attempt, locationRetryAvailable = false) }
                try {
                    val receipt = withTimeout(10_000) { submissions.submit(attempt, raw, fix) }
                    currentCoroutineContext().ensureActive()
                    if (refreshIdentity()) completeCheckIn(attempt, receipt)
                } catch (_: TimeoutCancellationException) { resolveAttempt(attempt) }
                catch (e: CancellationException) { throw e }
                catch (e: CheckInRejected) {
                    currentCoroutineContext().ensureActive()
                    if (!refreshIdentity()) return@runOperation
                    pendingSubmissions.remove(attempt)
                    update { it.copy(pendingSubmission = null) }
                    throw e
                } catch (_: Exception) { resolveAttempt(attempt) }
            }
        } catch (e: IllegalArgumentException) { message(e.message ?: "二维码无效。") }
    }

    /** Only a failed location acquisition may reuse a code, with all checks performed again. */
    fun retryLocation() {
        if (!refreshIdentity() || state.value.busy || pendingScan != null) return
        val current = state.value
        val code = current.scanned ?: return
        if (!current.locationRetryAvailable || current.participantSession != code.session) return
        receiveScan(PrototypeCodeCodec.encode(code))
    }

    fun resolveCheckIn() {
        if (!refreshIdentity() || state.value.busy) return
        val attempt = state.value.pendingSubmission ?: return
        runOperation(scanOperation = true) { resolveAttempt(attempt) }
    }

    fun hasPendingCheckIn(session: PrototypeSession): Boolean = !state.value.identityExpired &&
        identity.currentUserId() == actor && pendingSubmissions.get(actor, session.id) != null

    private suspend fun resolveAttempt(attempt: CheckInAttempt) {
        currentCoroutineContext().ensureActive()
        if (!refreshIdentity() || state.value.pendingSubmission != attempt) return
        update { it.copy(scanStage = ParticipantScanStage.RESOLVING, scanned = null,
            locationRetryAvailable = false, message = "正在核对上次签到结果，请稍候…") }
        try {
            val result = withTimeout(10_000) { submissions.resolve(attempt) }
            currentCoroutineContext().ensureActive()
            if (!refreshIdentity() || state.value.pendingSubmission != attempt) return
            when (result) {
                is CheckInResolution.Saved -> completeCheckIn(attempt, result.receipt)
                CheckInResolution.NotSubmitted -> {
                    pendingSubmissions.remove(attempt)
                    update { it.copy(pendingSubmission = null, scanStage = ParticipantScanStage.ATTENTION,
                        message = "已确认上次未完成签到，请重新扫码。") }
                }
                CheckInResolution.Unknown -> unresolvedCheckIn()
            }
        } catch (_: TimeoutCancellationException) { unresolvedCheckIn() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            if (refreshIdentity() && state.value.pendingSubmission == attempt) unresolvedCheckIn()
        }
    }

    private fun unresolvedCheckIn() {
        update { it.copy(scanStage = ParticipantScanStage.ATTENTION, notice = null,
            message = "请稍后核对，无需重复扫码。") }
    }

    private fun completeCheckIn(attempt: CheckInAttempt, receipt: PrototypeReceipt) {
        require(receipt.userId == actor && receipt.sessionId == attempt.session.id) { "签到结果与账号或活动不匹配。" }
        if (state.value.pendingSubmission != attempt || state.value.participantSession != attempt.session) return
        // A confirmed reply remains useful even if a separate history refresh fails.
        val records = runCatching { backend.history(actor) }.getOrDefault(state.value.history)
        val previous = records.find { it.session == attempt.session }
        val updated = (records.filterNot { it.session.id == attempt.session.id } +
            (previous?.copy(receipt = receipt) ?: PrototypeHistoryEntry(attempt.session, receipt, null, false)))
            .sortedByDescending { it.session.startAt }
        pendingSubmissions.remove(attempt)
        update { it.copy(receipt = receipt, scanned = null, history = updated, pendingSubmission = null,
            locationRetryAvailable = false, scanStage = ParticipantScanStage.SUCCESS,
            notice = PrototypeNotice(++noticeSequence, "签到成功"), message = "签到成功，已确认签到记录。") }
    }

    fun openHistory() {
        leaveForeground()
        runOperation { update { it.copy(history = backend.history(actor), message = "") } }
    }

    fun submitFeedback(sessionId: String, rating: Int, comment: String, details: FeedbackDetails = FeedbackDetails()) = runOperation {
        backend.submitFeedback(actor, sessionId, rating, comment, details)
        update { it.copy(history = backend.history(actor), message = "评价已保存。") }
    }

    fun loadReviews(session: PrototypeSession) {
        if (!refreshIdentity() || state.value.busy) return
        update { it.copy(reviewSessionId = session.id, reviews = emptyList(), message = "") }
        runOperation { update { it.copy(reviews = backend.reviews(actor, session)) } }
    }

    fun cancelEvent() {
        stopDisplaying()
        runOperation {
            backend.cancel(actor, requireNotNull(state.value.session))
            update { it.copy(cancelled = true, notice = null, history = backend.history(actor), message = "活动已取消。已发出的码最多仍可使用 30 秒。") }
        }
    }
    fun stopDisplaying() {
        displayGeneration++
        displaying?.cancel()
        displaying = null
        update { it.copy(code = null, busy = false) }
    }
    fun leaveForeground() {
        stopAttendance()
        stopDisplaying()
        operationGeneration++
        operation?.cancel()
        update { it.copy(busy = false, candidate = null, scanned = null, notice = null, locationRetryAvailable = false,
            scanStage = if (it.pendingSubmission != null) ParticipantScanStage.ATTENTION
                else if (it.scanStage == ParticipantScanStage.VERIFYING) ParticipantScanStage.READY else it.scanStage,
            message = if (it.pendingSubmission != null) "上次签到结果待确认，请先核对。" else it.message) }
    }
}

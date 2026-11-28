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
    val creatingMeeting: Boolean = false,
    val receipt: PrototypeReceipt? = null,
    val busy: Boolean = false,
    val message: String = "Choose an action.",
    val cancelled: Boolean = false,
    val checkInClosed: Boolean = false,
    val attendees: List<PrototypeAttendee> = emptyList(),
    val history: List<PrototypeHistoryEntry> = emptyList(),
    val notice: PrototypeNotice? = null,
    val scanStage: ParticipantScanStage = ParticipantScanStage.READY,
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
    private val ownLatest = latestReceipt?.takeIf { authorized && it.userId == actor && registrations.isCurrentReceipt(it) }
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
        message = if (initialPending == null) "Choose an action." else "Your last check-in is unconfirmed. Check its status first."))
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

    /** A changed identity invalidates this whole instance, including delayed camera callbacks. */
    fun refreshIdentity(): Boolean {
        if (state.value.identityExpired) return false
        if (identity.currentUserId() == actor) return true
        leaveForeground()
        pendingScan = null
        entryGeneration++
        seenCheckIns.clear()
        mutableState.value = PrototypeUiState(ownerUserId = actor, identityExpired = true,
            message = "Your account has changed. Reopen check-in.")
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
            pendingSubmission = pending, message = if (pending == null) "" else "Your last check-in is unconfirmed. Check its status first.",
            scanStage = if (pending == null) ParticipantScanStage.READY else ParticipantScanStage.ATTENTION,
            registration = registrations.status(session.id, actor),
            participants = registrations.participants(session.id)) }
    }

    /** Capture the target BEFORE requesting permission or starting the external camera Activity. */
    fun beginScan(general: Boolean = false): Long? {
        if (!refreshIdentity() || state.value.busy || pendingScan != null) return null
        if (!general && state.value.pendingSubmission != null) { resolveCheckIn(); return null }
        update { it.copy(scanned = null) }
        if (general) {
            entryGeneration++
            update { it.copy(entry = CheckInEntry.General, participantSession = null, receipt = null,
                scanned = null, notice = null, pendingSubmission = null, scanStage = ParticipantScanStage.READY, message = "",
                registration = registrations.status(null, actor), participants = registrations.participants(null)) }
        }
        val target = if (general) CheckInEntry.General else (state.value.entry as? CheckInEntry.Activity)
            ?: state.value.participantSession?.let { CheckInEntry.Activity(it) } ?: CheckInEntry.General
        return (++scanSequence).also { pendingScan = PendingCheckInScan(it, target, entryGeneration,
            registrations.registrationKey((target as? CheckInEntry.Activity)?.session?.id, actor)) }
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
        val records = backend.history(actor)
        val currentReceipt = records.find { it.session == session }?.receipt
        update { it.copy(receipt = currentReceipt, history = records, registration = registrations.status(session.id, actor),
            participants = registrations.participants(session.id), cancelled = cancelled, checkInClosed = closed,
            message = if (!valid) "Event details have changed. Go back and reopen the event." else it.message) }
    }

    fun dismissNotice(id: Long) { update { if (it.notice?.id == id) it.copy(notice = null) else it } }

    /** Independent of QR work; a failed refresh keeps the last successful snapshot. */
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
                    it.copy(attendance = it.attendance.copy(error = "Could not load attendance. Try again."))
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
            PrototypeNotice(++noticeSequence, if (arrivals.size == 1) "${it.name} just checked in" else "${arrivals.size} people just checked in, including ${it.name}")
        }
        update { it.copy(attendees = people, notice = notice ?: it.notice,
            attendance = AttendanceLoadState(session, loaded = true, updatedAt = now())) }
    }

    fun message(text: String) { if (!refreshIdentity()) return; update { it.copy(message = text,
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
            catch (e: Exception) { if (generation == operationGeneration) update { it.copy(code = null, message = e.message ?: "Something went wrong. Try again.",
                scanStage = if (scanOperation) ParticipantScanStage.ATTENTION else it.scanStage) } }
            finally { if (generation == operationGeneration) update { it.copy(busy = false) } }
        }
    }

    fun prepareMeeting() {
        if (state.value.entry is CheckInEntry.Activity) return message("Open this event's check-in page.")
        stopDisplaying()
        update { it.copy(creatingMeeting = true) }
    }
    fun dismissMeeting() { update { it.copy(creatingMeeting = false) } }
    fun confirmMeeting() = runOperation {
        stopAttendance()
        require(state.value.entry !is CheckInEntry.Activity) { "Open this event's check-in page." }
        val session = backend.createMeeting(actor)
        seenCheckIns.clear()
        update { it.copy(session = session, creatingMeeting = false, code = null, cancelled = false,
            notice = null, attendees = emptyList(), attendance = AttendanceLoadState(),
            history = backend.history(actor), message = "Event ready. Confirm your arrival to display the code.") }
    }

    fun showCode() {
        if (!refreshIdentity() || state.value.busy) return
        stopDisplaying()
        val session = state.value.session ?: return message("Open the event first.")
        val generation = displayGeneration
        displaying = viewModelScope.launch {
            try {
                while (true) {
                    update { it.copy(code = null, busy = true, message = "Preparing your check-in code…") }
                    currentCoroutineContext().ensureActive()
                    if (!refreshIdentity()) return@launch
                    val code = backend.issue(actor, session)
                    update { it.copy(code = code, busy = false,
                        history = backend.history(actor), receipt = backend.history(actor).find { record -> record.session == session }?.receipt,
                        message = "Your arrival is confirmed. Participants can now scan your code.") }
                    delay(PrototypePolicy.REFRESH_MS)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { update { it.copy(code = null, message = e.message ?: "Could not refresh. Please try again.") } }
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
        update { it.copy(scanned = null, notice = null,
            scanStage = ParticipantScanStage.READY, message = "") }
        // Closing the camera is navigation, not a failed check-in. Keep the existing activity/receipt.
        if (raw == null) return
        try {
            val code = PrototypeCodeCodec.decode(raw)
            (target as? CheckInEntry.Activity)?.session?.let { expected ->
                require(code.session.id == expected.id) { "This code is for another event. Scan this event's organizer code." }
                require(code.session == expected) { "The code does not match this event. Reload the event details." }
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
                message = "Event recognized. Checking your registration…") }
            runOperation(scanOperation = true) {
                currentCoroutineContext().ensureActive()
                if (!refreshIdentity()) return@runOperation
                val currentKey = requireNotNull(registrations.registrationKey(code.session.id, actor)) { "Your place is no longer confirmed." }
                require(ticket == null || ticket.entry is CheckInEntry.General || ticket.registrationKey == currentKey) {
                    "Your registration changed while scanning. Scan again."
                }
                update { it.copy(message = "Checking registration and check-in time…") }
                val attempt = CheckInAttempt(UUID.randomUUID().toString(), actor, code.session, currentKey)
                pendingSubmissions.save(attempt)
                update { it.copy(pendingSubmission = attempt) }
                try {
                    val receipt = withTimeout(10_000) { submissions.submit(attempt, raw) }
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
        } catch (e: IllegalArgumentException) { message(e.message ?: "Invalid QR code.") }
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
        if (attempt.registrationKey != registrations.registrationKey(attempt.session.id, actor) || attempt.registrationKey == null) {
            pendingSubmissions.remove(attempt)
            update { it.copy(pendingSubmission = null, receipt = null, scanStage = ParticipantScanStage.ATTENTION,
                message = "Your registration changed. Scan again for your current place.") }
            return
        }
        update { it.copy(scanStage = ParticipantScanStage.RESOLVING, scanned = null,
            message = "Checking your previous check-in. Please wait…") }
        try {
            val result = withTimeout(10_000) { submissions.resolve(attempt) }
            currentCoroutineContext().ensureActive()
            if (!refreshIdentity() || state.value.pendingSubmission != attempt) return
            when (result) {
                is CheckInResolution.Saved -> completeCheckIn(attempt, result.receipt)
                CheckInResolution.NotSubmitted -> {
                    pendingSubmissions.remove(attempt)
                    update { it.copy(pendingSubmission = null, scanStage = ParticipantScanStage.ATTENTION,
                        message = "Your previous check-in was not completed. Scan again.") }
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
            message = "Check the result later. No need to scan again.") }
    }

    private fun completeCheckIn(attempt: CheckInAttempt, receipt: PrototypeReceipt) {
        require(receipt.userId == actor && receipt.sessionId == attempt.session.id) { "The check-in result does not match this account or event." }
        if (state.value.pendingSubmission != attempt || state.value.participantSession != attempt.session) return
        if (attempt.registrationKey == null || attempt.registrationKey != registrations.registrationKey(attempt.session.id, actor) ||
            !registrations.isCurrentReceipt(receipt) || backend.isCancelled(attempt.session)) {
            pendingSubmissions.remove(attempt)
            update { it.copy(pendingSubmission = null, receipt = null, scanStage = ParticipantScanStage.ATTENTION,
                message = "This check-in belongs to an earlier registration. Scan again.") }
            return
        }
        // A confirmed reply remains useful even if a separate history refresh fails.
        val records = runCatching { backend.history(actor) }.getOrDefault(state.value.history)
        val previous = records.find { it.session == attempt.session }
        val updated = (records.filterNot { it.session.id == attempt.session.id } +
            (previous?.copy(receipt = receipt) ?: PrototypeHistoryEntry(attempt.session, receipt, null, false)))
            .sortedByDescending { it.session.startAt }
        pendingSubmissions.remove(attempt)
        update { it.copy(receipt = receipt, scanned = null, history = updated, pendingSubmission = null,
            scanStage = ParticipantScanStage.SUCCESS,
            notice = PrototypeNotice(++noticeSequence, "Checked in successfully"), message = "Check-in successful. Your record is confirmed.") }
    }

    fun openHistory() {
        leaveForeground()
        runOperation { update { it.copy(history = backend.history(actor), message = "") } }
    }

    fun submitFeedback(sessionId: String, rating: Int, comment: String, details: FeedbackDetails = FeedbackDetails()) = runOperation {
        backend.submitFeedback(actor, sessionId, rating, comment, details)
        update { it.copy(history = backend.history(actor), message = "Review saved.") }
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
            update { it.copy(cancelled = true, notice = null, history = backend.history(actor), message = "Event cancelled. Check-in is closed.") }
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
        update { it.copy(busy = false, creatingMeeting = false, scanned = null, notice = null,
            scanStage = if (it.pendingSubmission != null) ParticipantScanStage.ATTENTION
                else if (it.scanStage == ParticipantScanStage.VERIFYING) ParticipantScanStage.READY else it.scanStage,
            message = if (it.pendingSubmission != null) "Your last check-in is unconfirmed. Check its status first." else it.message) }
    }
}

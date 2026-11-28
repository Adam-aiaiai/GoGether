package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppBackButton
import com.gogether.app.core.ui.PageActionButton
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.gogether.app.GogetherApplication
import com.gogether.app.core.ui.*
import com.gogether.app.ui.theme.GogetherTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PrototypeCheckInActivity : ComponentActivity() {
    private lateinit var model: PrototypeViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actor = (application as GogetherApplication).repository.state.value.currentUserId
        if (actor == null) { finish(); return }
        val entry = try { CheckInEntryIntent.read(intent) } catch (_: IllegalArgumentException) { finish(); return }
        val identity = CheckInIdentitySource { (application as GogetherApplication).repository.state.value.currentUserId }
        val store = PreferencesPrototypeStore(applicationContext)
        val linked = RegistrationAttendanceBridge({ (application as GogetherApplication).repository.state.value }, store,
            elapsedNow = SystemClock::elapsedRealtime)
        val registrations = CombinedCheckInRegistrations(store, linked, StoredMockCheckInRegistrations(store))
        val backend = CombinedCheckInBackend(store, linked.backend,
            LocalPrototypeBackend(store, elapsedNow = SystemClock::elapsedRealtime, registrations = registrations,
                userName = { id -> (application as GogetherApplication).repository.state.value.users
                    .find { it.id == id }?.displayName ?: "Participant" }))
        model = ViewModelProvider(this, RepositoryViewModel.factory {
            PrototypeViewModel(actor,
                backend,
                AndroidPositionSource(applicationContext), store.latestReceipt(actor), entry = entry, identity = identity,
                registrations = registrations, pendingSubmissions = PreferencesPendingCheckInStore(applicationContext))
        })[PrototypeViewModel::class.java]
        // Keep this lightweight identity watch active while the external camera covers this Activity.
        lifecycleScope.launch {
            (application as GogetherApplication).repository.state.collect {
                if (!model.refreshIdentity()) finish() else model.refreshLiveData()
            }
        }
        setContent {
            GogetherTheme {
                val state by model.state.collectAsStateWithLifecycle()
                val now by produceState(System.currentTimeMillis()) {
                    while (true) { value = System.currentTimeMillis(); delay(1_000) }
                }
                // Protect only the displayed QR. Another camera can still photograph it.
                DisposableEffect(state.code) {
                    if (state.code != null) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }
                val current = state.session ?: state.participantSession
                PrototypeRoute(model, state, now, ::finish,
                    integrated = current?.let { store.activityInfo(it.id)?.sourceEventId != null } == true)
            }
        }
    }
    override fun onStop() {
        if (::model.isInitialized) model.leaveForeground()
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) {
            if (!model.refreshIdentity()) finish() else model.resolveCheckIn()
        }
    }
}

private enum class PermissionAction { MEETING, DISPLAY, SCAN }

@Composable
internal fun PrototypeRoute(model: PrototypeViewModel, state: PrototypeUiState, now: Long, back: () -> Unit,
    integrated: Boolean = false) {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<PermissionAction?>(null) }
    var scanRequest by rememberSaveable(model.actor) { mutableStateOf<Long?>(null) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        scanRequest?.let { model.receiveScan(result.contents, it) }
        scanRequest = null
    }
    fun perform(action: PermissionAction) {
        if (!model.refreshIdentity()) return
        when (action) {
            PermissionAction.MEETING -> model.locateMeeting()
            PermissionAction.DISPLAY -> model.showCode()
            PermissionAction.SCAN -> scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan the organizer's check-in code").setBeepEnabled(false).setOrientationLocked(false))
        }
    }
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action = pending
        pending = null
        if (action != null && granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            (action != PermissionAction.SCAN || granted(Manifest.permission.CAMERA))) perform(action)
        else {
            model.abandonScan(); scanRequest = null
            model.message("Precise location is required; scanning also needs camera access. Go back or allow access in app settings and try again.")
        }
    }
    fun request(action: PermissionAction, general: Boolean = false) {
        if (pending != null || state.busy) return
        if (!model.refreshIdentity()) return
        if (action == PermissionAction.SCAN) scanRequest = model.beginScan(general) ?: return
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            (action != PermissionAction.SCAN || granted(Manifest.permission.CAMERA))) perform(action)
        else {
            pending = action
            val needed = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            if (action == PermissionAction.SCAN) needed += Manifest.permission.CAMERA
            permissions.launch(needed.toTypedArray())
        }
    }
    CompositionLocalProvider(LocalCheckInActivities provides model.activitySource) {
    PrototypeScreen(model.actor, state, now, back,
        locate = { request(PermissionAction.MEETING) }, show = { request(PermissionAction.DISPLAY) },
        scan = { request(PermissionAction.SCAN) }, confirmMeeting = model::confirmMeeting,
        dismissMeeting = model::dismissMeeting, cancel = model::cancelEvent,
        settings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"))) },
        openHistory = model::openHistory, submitFeedback = model::submitFeedback, dismissNotice = model::dismissNotice,
        scanOther = { request(PermissionAction.SCAN, general = true) }, selectActivity = model::enterActivity,
        refreshAttendance = model::refreshAttendance, stopAttendance = model::stopAttendance, loadReviews = model::loadReviews,
        retryLocation = model::retryLocation, resolveCheckIn = model::resolveCheckIn,
        pendingActivities = state.history.filter { model.hasPendingCheckIn(it.session) }.map { it.session.id }.toSet(),
        integrated = integrated)
    }
}

@Composable
fun PrototypeScreen(actor: String, state: PrototypeUiState, now: Long, back: () -> Unit,
    locate: () -> Unit, show: () -> Unit, scan: () -> Unit, confirmMeeting: () -> Unit,
    dismissMeeting: () -> Unit, cancel: () -> Unit, settings: () -> Unit,
    openHistory: () -> Unit = {}, submitFeedback: (String, Int, String, FeedbackDetails) -> Unit = { _, _, _, _ -> },
    dismissNotice: (Long) -> Unit = {}, scanOther: () -> Unit = scan,
    selectActivity: (PrototypeSession) -> Unit = {},
    refreshAttendance: (PrototypeSession) -> Unit = {}, stopAttendance: () -> Unit = {},
    loadReviews: (PrototypeSession) -> Unit = {}, retryLocation: () -> Unit = {}, resolveCheckIn: () -> Unit = {},
    pendingActivities: Set<String> = state.pendingSubmission?.let { setOf(it.session.id) }.orEmpty(),
    integrated: Boolean = false) {
    if (state.identityExpired || (state.ownerUserId != null && state.ownerUserId != actor)) {
        Page {
            Text("Your account has changed. Reopen check-in.", Modifier.testTag("checkin_identity_expired"))
            AppBackButton(back)
        }
        return
    }
    val activities = LocalCheckInActivities.current
    fun owns(session: PrototypeSession?) = activities.infoOrNull(session)?.organizerId == actor
    val roleSession = state.session ?: state.participantSession ?: (state.entry as? CheckInEntry.Activity)?.session
    val organizerMode = if (roleSession != null) owns(roleSession) else actor == "alice"
    var confirmNew by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var historyOpen by rememberSaveable(actor) { mutableStateOf(false) }
    val historyState = rememberSaveableStateHolder()
    var historySessionId by rememberSaveable(actor) { mutableStateOf<String?>(null) }
    var attendanceRecordId by rememberSaveable(actor) { mutableStateOf<String?>(null) }
    var reviewSessionId by rememberSaveable(actor) { mutableStateOf<String?>(null) }
    val reviewSession = state.history.find { it.session.id == reviewSessionId }?.session
    val attendanceRecord = if (historyOpen)
        state.history.find { it.session.id == attendanceRecordId && owns(it.session) }?.session else null
    val visibleAttendance = attendanceRecord ?: state.session?.takeIf { owns(it) && !historyOpen }
    if (visibleAttendance != null) AttendanceRefreshEffect(visibleAttendance,
        live = attendanceRecord == null && now < visibleAttendance.endAt && !state.cancelled,
        refresh = refreshAttendance, stop = stopAttendance)
    fun viewHistory() { historySessionId = null; openHistory(); historyOpen = true }
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding).testTag("prototype_screen")) {
            if (reviewSession != null) {
                LaunchedEffect(reviewSession) { loadReviews(reviewSession) }
                ActivityReviewsScreen(actor, reviewSession, state.reviews.takeIf { state.reviewSessionId == reviewSession.id }.orEmpty(),
                    state.busy, state.message, { reviewSessionId = null }, { loadReviews(reviewSession) })
            } else if (attendanceRecord != null) {
                AttendanceRecordScreen(state, attendanceRecord, now, back = { attendanceRecordId = null }, refresh = refreshAttendance)
            } else if (historyOpen) {
                historyState.SaveableStateProvider(actor) {
                PrototypeHistoryScreen(actor, state.history, now, state.busy, state.message,
                    back = { historyOpen = false }, submit = submitFeedback, initialSessionId = historySessionId,
                    openCheckIn = { session -> selectActivity(session); historyOpen = false },
                    openAttendance = { session -> historySessionId = session.id; attendanceRecordId = session.id },
                    openReviews = { session -> reviewSessionId = session.id }, pendingCheckIns = pendingActivities)
                }
            } else if (organizerMode && state.session != null) {
                OrganizerCheckInPanel(state, now, back, show,
                    cancel = { confirmCancel = true }, newMeeting = { confirmNew = true }, settings = settings, history = ::viewHistory,
                    refreshAttendance = refreshAttendance, integrated = integrated)
            } else if (!organizerMode) {
                ParticipantCheckInPanel(actor, state, now, back, scan, ::viewHistory, settings,
                    record = { id -> historySessionId = id; openHistory(); historyOpen = true }, scanOther = scanOther,
                    retryLocation = retryLocation, resolveCheckIn = resolveCheckIn)
            } else Page {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AppBackButton(back)
                    TextButton(shape = CheckInStyle.shape, onClick = ::viewHistory, modifier = Modifier.testTag("open_history")) { Text("My records") }
                }
                SectionTitle(if (organizerMode) "Event check-in" else "Scan to check in", "Account: $actor")
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (state.entry is CheckInEntry.Activity) "Organizer check-in details are unavailable. Go back and check the event."
                    else "Confirm the meeting point on site. It stays fixed when your phone moves. The event starts in 10 minutes.")
                PageActionButton(onClick = locate, enabled = !state.busy && state.entry !is CheckInEntry.Activity,
                    modifier = Modifier.fillMaxWidth().testTag("prototype_meeting")) { Text("Locate meeting point") }
                if (organizerMode && state.session == null)
                    CheckInInfoCard("Status", state.message, Modifier.testTag("prototype_status"))
                state.receipt?.let {
                    CheckInInfoCard("Last check-in on this device", "Event ${it.sessionId.take(8)}\n${it.userId}\n${checkInDisplayTime(it.checkedInAt)}",
                        Modifier.testTag("prototype_receipt"))
                }
                CheckInInfoCard("Why we need your location", "Organizers and participants must be within 200 m of the fixed meeting point. Location is used only for actions on this page. Allow precise location; scanning also requires camera access.")
                TextButton(shape = CheckInStyle.shape, onClick = settings) { Text("Open permission settings") }
            }
            if (!historyOpen) state.notice?.let {
                CheckInNotice(it, dismissNotice, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
    state.candidate?.let { fix ->
        CheckInActionDialog(title = "Confirm meeting point?",
            description = "Create a new event at your current location. The meeting point stays fixed; participants must scan the new event code.\n" +
                "Estimated location accuracy: ${fix.accuracyMeters.toInt()} m.",
            action = "Confirm meeting point", onDismiss = dismissMeeting, onConfirm = confirmMeeting,
            confirmEnabled = !state.busy)
    }
    if (confirmNew) CheckInConfirmDialog("Create another event?", "This creates a new event and meeting point. Existing check-in records are kept.",
        "Retry location", { confirmNew = false }, { confirmNew = false; locate() })
    if (confirmCancel) CheckInConfirmDialog("Cancel this event?", "Stop issuing codes. Existing codes expire within 30 seconds.",
        "Confirm cancellation", { confirmCancel = false }, { confirmCancel = false; cancel() })
}

@Composable
internal fun MeetingSummary(session: PrototypeSession) {
    CheckInInfoCard("Meeting point ${session.id.take(8)}",
        "Location confirmed by the organizer on site (stays fixed)" +
            "\nStarts: ${checkInDisplayTime(session.startAt)}\nCheck-in: ${checkInDisplayTime(session.startAt - PrototypePolicy.HALF_HOUR_MS)} to " +
            checkInDisplayTime(session.startAt + PrototypePolicy.HALF_HOUR_MS))
}

private fun checkInDisplayTime(time: Long) = displayTime(time).replace(" · ", " ")

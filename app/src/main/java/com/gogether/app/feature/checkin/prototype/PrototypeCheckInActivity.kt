package com.gogether.app.feature.checkin.prototype

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
            LocalPrototypeBackend(store, elapsedNow = SystemClock::elapsedRealtime, registrations = registrations))
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
                .setPrompt("扫描组织者的动态签到码").setBeepEnabled(false).setOrientationLocked(false))
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
            model.message("需要精确位置权限；扫码还需要相机权限。你可以返回，或在应用设置中允许后重试。")
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
            Text("账号已变化，请重新进入签到。", Modifier.testTag("checkin_identity_expired"))
            TextButton(onClick = back, shape = CheckInStyle.shape) { Text("< 返回") }
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
                    TextButton(shape = CheckInStyle.shape, onClick = back) { Text("< 返回") }
                    TextButton(shape = CheckInStyle.shape, onClick = ::viewHistory, modifier = Modifier.testTag("open_history")) { Text("我的记录") }
                }
                SectionTitle(if (organizerMode) "现场签到" else "扫码签到", "当前账号：$actor")
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (state.entry is CheckInEntry.Activity) "未找到本场活动的组织者签到资料，请返回确认活动信息。"
                    else "先在现场确认集合点。确认后不会随手机移动；活动将在 10 分钟后开始。")
                Button(shape = CheckInStyle.shape, onClick = locate, enabled = !state.busy && state.entry !is CheckInEntry.Activity,
                    modifier = Modifier.fillMaxWidth().testTag("prototype_meeting")) { Text("定位并设置集合点") }
                if (organizerMode && state.session == null)
                    CheckInInfoCard("当前状态", state.message, Modifier.testTag("prototype_status"))
                state.receipt?.let {
                    CheckInInfoCard("本机最近一次签到", "活动 ${it.sessionId.take(8)}\n${it.userId}\n${checkInDisplayTime(it.checkedInAt)}",
                        Modifier.testTag("prototype_receipt"))
                }
                CheckInInfoCard("使用位置的原因", "组织者出码和参与者签到都需在固定集合点 200 米内。只在本页面操作时获取位置；请允许精确位置。扫码还需要相机权限。")
                TextButton(shape = CheckInStyle.shape, onClick = settings) { Text("打开应用权限设置") }
            }
            if (!historyOpen) state.notice?.let {
                CheckInNotice(it, dismissNotice, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
    state.candidate?.let { fix ->
        AlertDialog(shape = CheckInStyle.shape, onDismissRequest = dismissMeeting, title = { Text("固定集合点？") },
            text = { Text("使用当前位置建立一场新的活动。集合点之后保持固定，需让参与者扫描新活动码。\n" +
                "定位精度估计：${fix.accuracyMeters.toInt()} 米。") },
            confirmButton = { TextButton(shape = CheckInStyle.shape, onClick = confirmMeeting, enabled = !state.busy) { Text("确认集合点") } },
            dismissButton = { TextButton(shape = CheckInStyle.shape, onClick = dismissMeeting) { Text("取消") } })
    }
    if (confirmNew) CheckInConfirmDialog("另建活动？", "会产生新的活动编号和固定集合点；已有签到记录仍保留。",
        "重新定位", { confirmNew = false }, { confirmNew = false; locate() })
    if (confirmCancel) CheckInConfirmDialog("取消活动？", "停止出码。已发出的码最长 30 秒后过期。",
        "确认取消", { confirmCancel = false }, { confirmCancel = false; cancel() })
}

@Composable
internal fun MeetingSummary(session: PrototypeSession) {
    CheckInInfoCard("集合点 ${session.id.take(8)}",
        "组织者在现场确认的位置（建立后保持固定）" +
            "\n开始：${checkInDisplayTime(session.startAt)}\n签到：${checkInDisplayTime(session.startAt - PrototypePolicy.HALF_HOUR_MS)} 至 " +
            checkInDisplayTime(session.startAt + PrototypePolicy.HALF_HOUR_MS))
}

private fun checkInDisplayTime(time: Long) = displayTime(time).replace(" · ", " ")

package com.gogether.app.feature.checkin.prototype

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.model.EventStatus
import com.gogether.app.core.ui.*
import com.gogether.app.feature.checkin.CheckInScreen
import com.gogether.app.feature.event.MeetingMapButton
import kotlinx.coroutines.launch

/** Normal event entry uses the same live C repository as ParticipantsCard. */
@Composable
fun RepositoryCheckInScreen(repository: GogetherRepository, eventId: String, now: Long, back: () -> Unit) {
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val event = snapshot.event(eventId)
    val actor = snapshot.currentUserId
    if (event == null || actor == null) {
        Page { Text("活动或账号已变化，请返回。"); TextButton(onClick = back) { Text("< 返回") } }
        return
    }
    val context = LocalContext.current
    val store = remember { PreferencesPrototypeStore(context.applicationContext) }
    val bridge = remember(repository, store) {
        RegistrationAttendanceBridge({ repository.state.value }, store, elapsedNow = SystemClock::elapsedRealtime)
    }
    var revision by remember { mutableIntStateOf(0) }
    val session = remember(event, revision) { bridge.session(event) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var candidate by remember { mutableStateOf<PositionFix?>(null) }
    fun locate() {
        if (busy) return
        busy = true
        scope.launch {
            try { candidate = AndroidPositionSource(context).locate() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "定位失败，请重试。" }
            finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
        else message = "需要精确位置权限才能确认集合点。"
    }
    if (session == null) {
        // Existing events without a GPS point can still receive their original post-event feedback.
        if (now >= event.endAt || event.status == EventStatus.COMPLETED) {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = back, shape = CheckInStyle.shape) { Text("< 返回") }
                if (message.isNotBlank()) Text(message)
                Box(Modifier.weight(1f)) {
                    CheckInScreen(event, snapshot, now, busy, {}, { rating, comment ->
                        scope.launch {
                            busy = true
                            message = repository.submitFeedback(eventId, rating, comment).exceptionOrNull()?.message.orEmpty()
                            busy = false
                        }
                    })
                }
            }
        } else Page {
            TextButton(onClick = back, shape = CheckInStyle.shape) { Text("< 返回") }
            SectionTitle("集合与签到", event.title)
            Text("${displayTime(event.startAt)}\n${event.city} ${event.location}")
            MeetingMapButton("${event.city} ${event.location}")
            Text(if (event.status == EventStatus.CANCELLED) "活动已取消。"
                else if (event.organizerId == actor) "到达约定地点后，确认当前位置为本场固定集合点。"
                else "组织者尚未确认集合点，请稍后重新进入。", Modifier.testTag("meeting_setup_status"))
            if (event.organizerId == actor && event.status == EventStatus.OPEN) Button(
                onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
                enabled = !busy, shape = CheckInStyle.shape, modifier = Modifier.fillMaxWidth().testTag("bind_meeting")) {
                Text(if (busy) "正在定位…" else "定位并确认本场集合点")
            }
            if (message.isNotBlank()) Text(message)
        }
        candidate?.let { fix -> AlertDialog(shape = CheckInStyle.shape, onDismissRequest = { candidate = null },
            title = { Text("确认本场集合点") }, text = { Text("${event.title}\n${event.city} ${event.location}\n将当前位置固定为集合点，签到需在 200 米内。活动时间和报名名单保持原样。") },
            confirmButton = { TextButton(onClick = {
                runCatching { bridge.prepare(actor, eventId, fix) }.onSuccess { revision++ }
                    .onFailure { message = it.message ?: "保存失败，请重试。" }
                candidate = null
            }) { Text("确认") } }, dismissButton = { TextButton(onClick = { candidate = null }) { Text("取消") } }) }
        return
    }
    val model: PrototypeViewModel = viewModel(key = "ce:$actor:${session.id}", factory = RepositoryViewModel.factory {
        PrototypeViewModel(actor, bridge.backend, AndroidPositionSource(context), entry = CheckInEntry.Activity(session),
            identity = CheckInIdentitySource { repository.state.value.currentUserId }, registrations = bridge,
            pendingSubmissions = PreferencesPendingCheckInStore(context))
    })
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(snapshot) { model.refreshLiveData() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.leaveForeground()
            if (event == Lifecycle.Event.ON_RESUME) { model.refreshLiveData(); model.resolveCheckIn() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); model.leaveForeground() }
    }
    val window = (context as? Activity)?.window
    DisposableEffect(state.code, window) {
        if (state.code != null) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    PrototypeRoute(model, state, now, back, integrated = true)
}

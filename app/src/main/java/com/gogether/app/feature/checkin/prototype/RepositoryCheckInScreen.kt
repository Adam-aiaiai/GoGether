package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.PageActionButton
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
import com.gogether.app.feature.event.MeetingMapButton
import kotlinx.coroutines.launch

/** Normal event entry uses the same live C repository as ParticipantsCard. */
@Composable
fun RepositoryCheckInScreen(repository: GogetherRepository, eventId: String, now: Long, back: () -> Unit,
    attendanceOnly: Boolean = false) {
    if (repository is com.gogether.app.core.data.cloud.CloudOperations) {
        com.gogether.app.feature.checkin.cloud.CloudCheckInScreen(repository, repository, eventId, now, back, attendanceOnly)
        return
    }
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val event = snapshot.event(eventId)
    val actor = snapshot.currentUserId
    if (event == null || actor == null) {
        Page { Text("The event or account has changed. Go back."); TextButton(onClick = back) { Text("< Back") } }
        return
    }
    if (attendanceOnly && event.organizerId != actor) {
        Page {
            Text("Only this event's organizer can view attendance.")
            TextButton(onClick = back) { Text("< Back") }
        }
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
            catch (e: Exception) { message = e.message ?: "Could not get your location. Try again." }
            finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
        else message = "Precise location permission is required to confirm the meeting point."
    }
    if (bridge.hasEnded(event, now) || event.status == EventStatus.CANCELLED) {
        key(actor, event.id) { RepositoryFeedbackScreen(repository, bridge, event, actor, now, back,
            initialAttendance = attendanceOnly) }
        return
    }
    if (session == null) {
        Page {
            TextButton(onClick = back, shape = CheckInStyle.shape) { Text("< Back") }
            SectionTitle("Meetup and check-in", event.title)
            Text("${displayTime(event.meetingAt ?: event.startAt)}\n${event.city} ${event.meetingLocation ?: event.location}")
            if (event.meetingNotes.isNotBlank()) Text(event.meetingNotes)
            MeetingMapButton("${event.city} ${event.meetingLocation ?: event.location}")
            Text(if (event.status == EventStatus.CANCELLED) "This event has been cancelled."
                else if (event.organizerId == actor) "Once you arrive, confirm your location as this event's fixed meeting point."
                else "The organizer has not set the meeting point. Try again later.", Modifier.testTag("meeting_setup_status"))
            if (event.organizerId == actor && event.status == EventStatus.OPEN) PageActionButton(
                onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
                enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("bind_meeting")) {
                Text(if (busy) "Getting location…" else "Set meeting point here")
            }
            if (message.isNotBlank()) Text(message)
        }
        candidate?.let { fix -> AlertDialog(shape = CheckInStyle.shape, onDismissRequest = { candidate = null },
            title = { Text("Confirm meeting point") }, text = { Text("${event.title}\n${event.city} ${event.meetingLocation ?: event.location}\nUse your current location as the fixed meeting point. Check-in requires being within 200 m. Event times and registrations will stay the same.") },
            confirmButton = { TextButton(onClick = {
                runCatching { bridge.prepare(actor, eventId, fix) }.onSuccess { revision++ }
                    .onFailure { message = it.message ?: "Could not save. Please try again." }
                candidate = null
            }) { Text("Confirm") } }, dismissButton = { TextButton(onClick = { candidate = null }) { Text("Cancel") } }) }
        return
    }
    val model: PrototypeViewModel = viewModel(key = "ce:$actor:$session", factory = RepositoryViewModel.factory {
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

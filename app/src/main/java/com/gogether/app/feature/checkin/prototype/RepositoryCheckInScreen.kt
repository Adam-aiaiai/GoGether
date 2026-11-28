package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppBackButton
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
        Page { Text("The event or account has changed. Go back."); AppBackButton(back) }
        return
    }
    if (attendanceOnly && event.organizerId != actor) {
        Page {
            Text("Only this event's organizer can view attendance.")
            AppBackButton(back)
        }
        return
    }
    val context = LocalContext.current
    val store = remember { PreferencesPrototypeStore(context.applicationContext) }
    val bridge = remember(repository, store) {
        RegistrationAttendanceBridge({ repository.state.value }, store)
    }
    val session = remember(event) { bridge.session(event) }
    if (bridge.hasEnded(event, now) || event.status == EventStatus.CANCELLED) {
        key(actor, event.id) { RepositoryFeedbackScreen(repository, bridge, event, actor, now, back,
            initialAttendance = attendanceOnly) }
        return
    }
    val model: PrototypeViewModel = viewModel(key = "ce:$actor:$session", factory = RepositoryViewModel.factory {
        PrototypeViewModel(actor, bridge.backend, entry = CheckInEntry.Activity(session),
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

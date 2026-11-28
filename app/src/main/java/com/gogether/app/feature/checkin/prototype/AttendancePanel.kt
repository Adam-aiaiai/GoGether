package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppDivider
import com.gogether.app.core.ui.AppPageHeader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/** Observe only while this roster is visible and the Activity is in the foreground. */
@Composable
internal fun AttendanceRefreshEffect(session: PrototypeSession, live: Boolean,
    refresh: (PrototypeSession) -> Unit, stop: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val refreshLatest by rememberUpdatedState(refresh)
    val stopLatest by rememberUpdatedState(stop)
    LaunchedEffect(session, live, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                refreshLatest(session)
                if (live) while (true) {
                    delay(10_000)
                    refreshLatest(session)
                } else awaitCancellation()
            } finally { stopLatest() }
        }
    }
}

/** Loading and errors occupy the roster area, never the QR area or a growing page. */
@Composable
internal fun AttendancePanel(state: PrototypeUiState, session: PrototypeSession, modifier: Modifier,
    searchOpen: Boolean, refresh: () -> Unit, dismissSearch: () -> Unit) {
    val matches = state.attendance.session == session
    val load = if (matches) state.attendance else AttendanceLoadState(session)
    val people = if (matches) state.attendees else emptyList()
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (load.loaded) "${people.size} participants, ${people.count { it.checkedInAt != null }} checked in"
                    else "Attendance", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("attendance_summary"))
                Text(when {
                    load.loading -> if (load.loaded) "Updating attendance…" else "Loading attendance…"
                    load.error != null -> if (load.loaded) "Update failed. Showing previous results." else "Could not load attendance. Try again."
                    load.updatedAt != null -> "Updated ${attendanceTime(load.updatedAt).takeLast(8)}"
                    else -> "Waiting to load"
                }, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (load.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("attendance_load_status"))
            }
            IconButton(onClick = refresh, enabled = !load.loading, modifier = Modifier.testTag("attendance_refresh")) {
                Icon(Icons.Default.Refresh, if (load.error != null) "Retry loading attendance" else "Refresh attendance")
            }
        }
    }
    if (load.loaded) {
        PrototypeAttendance(people, modifier, searchOpen, header, dismissSearch)
    } else Column(modifier) {
        header()
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (load.loading) CircularProgressIndicator(Modifier.size(24.dp).testTag("attendance_loading"))
            else Text("Attendance will appear once loaded", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun AttendanceRecordScreen(state: PrototypeUiState, session: PrototypeSession, now: Long,
    back: () -> Unit, refresh: (PrototypeSession) -> Unit) {
    var search by rememberSaveable(session.id) { mutableStateOf(false) }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("attendance_record_screen")) {
        AppPageHeader("Attendance record", horizontalInset = 16.dp, onBack = back, actions = {
            IconButton(onClick = { search = true }, enabled = state.attendance.session == session && state.attendance.loaded,
                modifier = Modifier.testTag("attendance_search_open")) { Icon(Icons.Default.Search, "Find participants") }
        })
        Text(LocalCheckInActivities.current.infoOrNull(session)?.title ?: "Event details unavailable", style = MaterialTheme.typography.titleMedium)
        Text(attendanceTime(session.startAt), style = MaterialTheme.typography.bodySmall)
        Text(if (state.history.find { it.session == session }?.cancelled == true) "Event cancelled. Attendance records are kept."
            else if (now >= session.endAt) "Event ended" else "This event has not ended yet", style = MaterialTheme.typography.bodySmall)
        AppDivider(Modifier.padding(vertical = 8.dp), horizontalInset = 16.dp)
        key(session) {
            AttendancePanel(state, session, Modifier.weight(1f).padding(bottom = 8.dp), search,
                refresh = { refresh(session) }, dismissSearch = { search = false })
        }
    }
}

package com.gogether.app.feature.checkin.prototype

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

/** A bounded phone screen: QR stays visible while only the selected attendance grid scrolls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrganizerCheckInPanel(state: PrototypeUiState, now: Long, back: () -> Unit,
    show: () -> Unit, cancel: () -> Unit, newMeeting: () -> Unit, settings: () -> Unit, history: () -> Unit,
    refreshAttendance: (PrototypeSession) -> Unit = {}) {
    val session = requireNotNull(state.session)
    var more by rememberSaveable(session.id) { mutableStateOf(false) }
    var preview by rememberSaveable(session.id) { mutableStateOf(false) }
    var searchOpen by rememberSaveable(session.id) { mutableStateOf(false) }
    val previewPeople = remember(session.id) {
        attendanceLayoutExample.mapIndexed { index, person ->
            person.copy(checkedInAt = person.checkedInAt?.let { minOf(now, session.startAt) - index * 60_000L })
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("organizer_panel")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(shape = CheckInStyle.shape, onClick = back) { Text("< 返回") }
            Text(LocalCheckInActivities.current.infoOrNull(session)?.title ?: "现场签到", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { searchOpen = true }, enabled = preview ||
                (state.attendance.session == session && state.attendance.loaded), modifier = Modifier.testTag("attendance_search_open")) {
                Icon(Icons.Default.Search, contentDescription = "查找参与者")
            }
            TextButton(shape = CheckInStyle.shape, onClick = { more = true }, modifier = Modifier.testTag("organizer_more")) { Text("更多") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp)) {
            val people = if (preview) previewPeople else state.attendees
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OrganizerCode(state, now, show, Modifier.weight(1f).fillMaxHeight())
                    key(session.id, preview) {
                        if (preview) PrototypeAttendance(people, Modifier.weight(1f).fillMaxHeight(), searchOpen) { searchOpen = false }
                        else AttendancePanel(state, session, Modifier.weight(1f).fillMaxHeight(), searchOpen,
                            { refreshAttendance(session) }, { searchOpen = false })
                    }
                }
            } else {
                val codeWeight = if (LocalDensity.current.fontScale > 1.3f) 0.48f else 0.55f
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrganizerCode(state, now, show, Modifier.weight(codeWeight).fillMaxWidth())
                    key(session.id, preview) {
                        if (preview) PrototypeAttendance(people, Modifier.weight(1f - codeWeight).fillMaxWidth(), searchOpen) { searchOpen = false }
                        else AttendancePanel(state, session, Modifier.weight(1f - codeWeight).fillMaxWidth(), searchOpen,
                            { refreshAttendance(session) }, { searchOpen = false })
                    }
                }
            }
        }
    }
    if (more) ModalBottomSheet(shape = CheckInStyle.sheetShape, onDismissRequest = { more = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("活动信息与操作", style = MaterialTheme.typography.titleLarge)
            TextButton(shape = CheckInStyle.shape, onClick = { more = false; history() }, modifier = Modifier.testTag("open_history")) { Text("我的记录") }
            CheckInInfoCard("当前状态", state.message)
            MeetingSummary(session)
            Text("组织者和参与者均需在集合点 200 米内。只在操作时获取精确位置。")
            TextButton(shape = CheckInStyle.shape, onClick = { more = false; settings() }) { Text("打开应用权限设置") }
            TextButton(shape = CheckInStyle.shape, onClick = { preview = !preview; more = false }, modifier = Modifier.testTag("attendance_preview")) {
                Text(if (preview) "返回当前名单" else "预览多人名单")
            }
            if (!state.cancelled && now < session.startAt) OutlinedButton(shape = CheckInStyle.shape,
                onClick = { more = false; cancel() }, enabled = !state.busy) { Text("取消这场活动") }
            TextButton(shape = CheckInStyle.shape, onClick = { more = false; newMeeting() }, enabled = !state.busy) { Text("另建一场活动") }
        }
    }
}

@Composable
private fun OrganizerCode(state: PrototypeUiState, now: Long, show: () -> Unit, modifier: Modifier) {
    val session = requireNotNull(state.session)
    val available = !state.cancelled && now < session.endAt &&
        now >= session.startAt - PrototypePolicy.HALF_HOUR_MS && now <= session.startAt + PrototypePolicy.HALF_HOUR_MS
    val code = state.code?.takeIf { available && now >= it.issuedAt - 5_000 && now < it.expiresAt }
    val status = when {
        state.cancelled -> "活动已取消，不能出码"
        !available -> "当前不在签到时段"
        code != null -> "剩余 ${(code.expiresAt - now + 999) / 1000} 秒，自动刷新"
        state.busy -> "正在确认位置…"
        else -> state.message
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight, 280.dp)
            if (code != null) {
                val bitmap = remember(code) { BarcodeEncoder().encodeBitmap(
                    PrototypeCodeCodec.encode(code), BarcodeFormat.QR_CODE, 720, 720).asImageBitmap() }
                Image(bitmap, "动态签到二维码", Modifier.size(side).background(Color.White).testTag("prototype_qr"))
            } else Surface(Modifier.size(side), shape = CheckInStyle.shape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(if (state.busy) "定位中…" else "等待出码", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(status, Modifier.testTag("prototype_status"), style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Button(shape = CheckInStyle.shape, onClick = show, enabled = !state.busy && available,
            modifier = Modifier.fillMaxWidth().testTag("prototype_show")) {
            Text(if (code != null) "刷新二维码" else "定位并出码", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

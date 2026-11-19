package com.gogether.app.feature.checkin.prototype

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun eventTime(time: Long, pattern: String) = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))

/** Event context scrolls on small displays; the primary action stays within reach. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParticipantCheckInPanel(actor: String, state: PrototypeUiState, now: Long, back: () -> Unit,
    scan: () -> Unit, history: () -> Unit, settings: () -> Unit, record: (String) -> Unit, scanOther: () -> Unit,
    retryLocation: () -> Unit = {}, resolveCheckIn: () -> Unit = {}) {
    val session = state.participantSession ?: state.scanned?.session
    val activity = LocalCheckInActivities.current.infoOrNull(session)
    var sheet by rememberSaveable(actor, session?.id) { mutableStateOf<String?>(null) }
    var mapError by rememberSaveable(actor, session?.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val registration = state.registration ?: MockCheckInRegistrations.status(session?.id, actor)
    val confirmed = registration == CheckInRegistration.CONFIRMED
    val waitlisted = registration == CheckInRegistration.WAITLISTED
    val participants = state.participants ?: MockCheckInRegistrations.participants(session?.id)
    val name = participants.find { it.userId == actor }?.name ?: actor.replaceFirstChar { it.uppercase() }
    val entry = state.history.find { it.session == session }
    val receipt = (entry?.receipt ?: state.receipt)?.takeIf {
        it.userId == actor && (session == null || it.sessionId == session.id)
    }
    val resolving = state.scanStage == ParticipantScanStage.RESOLVING
    val needsResolution = state.pendingSubmission?.let { it.actor == actor && it.session == session } == true
    val busy = state.busy || state.scanStage == ParticipantScanStage.VERIFYING || resolving
    val attention = !busy && state.scanStage == ParticipantScanStage.ATTENTION
    val retryCode = state.scanned?.takeIf { state.locationRetryAvailable && it.session == session }
    val canRetryLocation = attention && retryCode != null && runCatching { retryCode.requireValid(now) }.isSuccess
    val showReceipt = receipt != null && !busy && !attention && !needsResolution
    val cancelled = entry?.cancelled == true || (state.session?.id == session?.id && state.cancelled)
    val ended = session != null && now >= session.endAt
    val early = session != null && now < session.startAt - PrototypePolicy.HALF_HOUR_MS
    val late = session != null && now > session.startAt + PrototypePolicy.HALF_HOUR_MS
    val canOpenRecord = entry != null && (showReceipt || ended) && !attention
    val status = when {
        resolving -> "正在核对签到结果"
        busy -> "正在核验签到"
        needsResolution -> "签到结果待确认"
        attention -> "签到提示"
        cancelled -> "活动已取消"
        ended -> if (entry?.feedback != null) "已评价" else "活动已结束"
        showReceipt -> if (state.scanStage == ParticipantScanStage.SUCCESS) "签到成功" else "已签到"
        !confirmed -> if (waitlisted) "候补中" else "尚未报名"
        early -> "签到尚未开始"
        late -> "签到已结束"
        session == null -> "到场后扫码签到"
        else -> "现在可以签到"
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("participant_panel")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(shape = CheckInStyle.shape, onClick = back) { Text("< 返回") }
            Text("我的活动", style = MaterialTheme.typography.titleSmall)
            TextButton(shape = CheckInStyle.shape, onClick = history, modifier = Modifier.testTag("open_history")) { Text("记录") }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .testTag("participant_status_area"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("活动参与", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(activity?.title ?: "待选择活动", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.testTag("participant_event_title"))
                Text("$name  " + if (confirmed) "已报名" else if (waitlisted) "候补中" else "尚未报名",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PersonInitial(activity?.organizerName ?: "?", 36)
                Column {
                    Text(activity?.let { "${it.organizerName} 发起" } ?: "扫码后查看组织者", style = MaterialTheme.typography.titleSmall)
                    Text("组织者", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EventInfoRow("时间", if (session == null) "扫码后获取本场时间" else
                    "${eventTime(session.startAt, "M月d日 HH:mm")}–${eventTime(session.endAt, "HH:mm")}")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { EventInfoRow("集合", if (session == null) "到场后扫描组织者的二维码" else (activity?.meetingName ?: "集合点待确认")) }
                    if (session != null) TextButton(shape = CheckInStyle.shape, onClick = { mapError = false; sheet = "location" },
                        contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.testTag("participant_location")) {
                        Text("位置 ›", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("同行", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    participants.take(3).forEach { PersonInitial(it.name, 26) }
                    Text("${participants.size} 人已报名", style = MaterialTheme.typography.bodySmall)
                }
            }
            Surface(shape = CheckInStyle.shape, color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().testTag("prototype_status")) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(status, style = MaterialTheme.typography.titleMedium,
                        color = if (attention) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("participant_status_title"))
                    when {
                        busy -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(state.message.ifBlank { "正在确认位置与签到资格…" }, style = MaterialTheme.typography.bodySmall)
                        }
                        needsResolution -> Text(state.message, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("participant_issue"))
                        attention -> Text(if (retryCode != null && !canRetryLocation) "签到码已过期，请扫描组织者的新码。"
                            else state.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("participant_issue"))
                        cancelled -> Text("本场活动停止签到与评价。", style = MaterialTheme.typography.bodySmall)
                        showReceipt -> Text("签到时间 ${eventTime(receipt!!.checkedInAt, "M月d日 HH:mm:ss")}",
                            Modifier.testTag("prototype_receipt"), style = MaterialTheme.typography.bodyMedium)
                        ended -> Text("可以在活动记录中留下评分和感受。", style = MaterialTheme.typography.bodySmall)
                        !confirmed -> Text(if (waitlisted) "转为正式参与者后即可签到。" else "正式报名后即可签到。", style = MaterialTheme.typography.bodySmall)
                        session != null -> Text("${eventTime(session.startAt - PrototypePolicy.HALF_HOUR_MS, "HH:mm")}–${eventTime(minOf(session.endAt, session.startAt + PrototypePolicy.HALF_HOUR_MS), "HH:mm")}  签到时段\n请在集合点 200 米内扫码。", style = MaterialTheme.typography.bodySmall)
                        else -> Text("扫描组织者二维码，确认活动与集合位置。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            TextButton(shape = CheckInStyle.shape, onClick = { sheet = "about" }, contentPadding = PaddingValues(0.dp),
                modifier = Modifier.testTag("participant_about")) {
                Text("活动介绍与须知 ›", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(shape = CheckInStyle.shape, enabled = !busy && (needsResolution || (!cancelled &&
            (canOpenRecord || (!ended && !early && !late && confirmed)))),
            onClick = { if (needsResolution) resolveCheckIn() else if (canOpenRecord) record(entry!!.session.id)
                else if (canRetryLocation) retryLocation() else scan() },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp).testTag("prototype_scan")) {
            Text(when {
                resolving -> "正在核对…"
                busy -> "正在核验…"
                needsResolution -> "核对签到结果"
                cancelled -> "活动已取消"
                canOpenRecord && ended -> if (entry?.feedback != null) "查看评价" else "评价活动"
                canOpenRecord -> "查看签到记录"
                ended -> "活动已结束"
                early -> "等待签到开放"
                late -> "签到已结束"
                !confirmed -> "暂无签到资格"
                canRetryLocation -> "重新定位"
                attention -> "重新扫码"
                else -> "扫码签到"
            })
        }
        TextButton(shape = CheckInStyle.shape, onClick = { sheet = "help" },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp).testTag("participant_help")) {
            Text("签到说明与权限", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (sheet != null) ModalBottomSheet(onDismissRequest = { sheet = null }, shape = CheckInStyle.sheetShape,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (sheet) {
                "location" -> {
                    Text("集合位置", style = MaterialTheme.typography.titleLarge)
                    Text((activity?.meetingName ?: "集合点待确认"))
                    Text("以组织者现场确认的位置为准，抵达后在附近碰面。")
                    session?.let {
                        Button(shape = CheckInStyle.shape, onClick = {
                            val point = it.meeting
                            val uri = Uri.parse("geo:${point.latitude},${point.longitude}?q=${point.latitude},${point.longitude}(${Uri.encode("集合点")})")
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            catch (_: ActivityNotFoundException) { mapError = true }
                        }, modifier = Modifier.testTag("participant_map")) { Text("在地图中查看") }
                    }
                    if (mapError) Text("未找到可打开位置的地图应用。请联系组织者确认集合位置。")
                }
                "about" -> {
                    Text("活动介绍", style = MaterialTheme.typography.titleLarge)
                    Text((activity?.introduction ?: "读取活动后查看介绍"))
                    Text("参与流程", style = MaterialTheme.typography.titleMedium)
                    Text("报名参加 → 到场签到 → 活动结束后评价")
                    Text("集合时请向组织者确认具体场地与安排。")
                    Text("活动结束后，可在本场记录中提交一次评分和评论。")
                }
                else -> {
                    Text("签到说明", style = MaterialTheme.typography.titleLarge)
                    Text("正式参与者可在活动开始前 30 分钟至开始后 30 分钟签到；活动取消或结束后停止签到。")
                    Text("组织者和参与者均需在固定集合点 200 米内。扫码需要相机和精确位置权限，只在签到操作时获取位置。")
                    TextButton(shape = CheckInStyle.shape, onClick = { sheet = null; settings() },
                        modifier = Modifier.testTag("participant_settings")) { Text("打开应用权限设置") }
                    TextButton(shape = CheckInStyle.shape, enabled = !busy,
                        onClick = { sheet = null; scanOther() }, modifier = Modifier.testTag("participant_scan_other")) {
                        Text("扫描其他活动")
                    }
                }
            }
        }
    }
}

@Composable
private fun EventInfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PersonInitial(name: String, size: Int) {
    Box(Modifier.size(size.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.take(1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

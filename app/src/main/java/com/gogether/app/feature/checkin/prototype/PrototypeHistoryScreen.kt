package com.gogether.app.feature.checkin.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gogether.app.core.ui.displayTime

internal fun PrototypeHistoryEntry.canReview(now: Long) = !cancelled && feedback == null && now >= session.endAt

/** Bounded list and a separate detail/form screen; no growing content on the QR screen. */
@Composable
fun PrototypeHistoryScreen(actor: String, records: List<PrototypeHistoryEntry>, now: Long, busy: Boolean,
    message: String, back: () -> Unit, submit: (String, Int, String, FeedbackDetails) -> Unit,
    initialSessionId: String? = null, openCheckIn: (PrototypeSession) -> Unit = {},
    openAttendance: (PrototypeSession) -> Unit = {}, openReviews: (PrototypeSession) -> Unit = {},
    pendingCheckIns: Set<String> = emptySet()) {
    val activities = LocalCheckInActivities.current
    var selectedId by rememberSaveable(actor, initialSessionId) { mutableStateOf(initialSessionId) }
    var pendingOnly by rememberSaveable(actor) { mutableStateOf(false) }
    val selected = records.find { it.session.id == selectedId }
    BackHandler { if (selectedId != null) selectedId = null else back() }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp).testTag("history_screen")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(shape = CheckInStyle.shape, onClick = { if (selectedId != null) selectedId = null else back() }) { Text("< 返回") }
            Text(when {
                selected == null -> "我的记录"
                selected.feedback != null -> "我的评价"
                selected.canReview(now) -> "活动评价"
                else -> "活动记录"
            }, style = MaterialTheme.typography.titleLarge)
        }
        if (selected != null) {
            key(actor, selected.session.id) {
                HistoryDetail(actor, selected, now, busy, message, submit, Modifier.weight(1f), openCheckIn, openAttendance, openReviews,
                    pendingCheckIn = selected.session.id in pendingCheckIns)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!pendingOnly, { pendingOnly = false }, shape = CheckInStyle.shape, label = { Text("全部") })
                FilterChip(pendingOnly, { pendingOnly = true }, shape = CheckInStyle.shape, label = { Text("待评价 ${records.count { it.canReview(now) }}") },
                    modifier = Modifier.testTag("history_pending"))
            }
            val visible = if (pendingOnly) records.filter { it.canReview(now) } else records
            if (visible.isEmpty()) Text(if (pendingOnly) "暂时没有待评价的活动" else "参与活动后，签到和评价会保存在这里。",
                Modifier.padding(vertical = 20.dp))
            LazyColumn(Modifier.weight(1f).testTag("history_list"), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)) {
                items(visible, key = { it.session.id }) { entry ->
                    Card(shape = CheckInStyle.shape, onClick = { selectedId = entry.session.id },
                        modifier = Modifier.fillMaxWidth().testTag("history_${entry.session.id}")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(activities.infoOrNull(entry.session)?.title ?: "活动资料不可用", style = MaterialTheme.typography.titleMedium)
                            Text(historyTime(entry.session.startAt), style = MaterialTheme.typography.bodySmall)
                            Text(when {
                                entry.session.id in pendingCheckIns -> "签到结果待确认"
                                entry.cancelled -> "活动已取消"
                                entry.feedback != null -> "已评价 ${entry.feedback.rating} / 5"
                                now >= entry.session.endAt -> "已结束，可以评价"
                                entry.receipt != null -> "已签到，结束后可评价"
                                else -> "尚未签到"
                            })
                        }
                    }
                }
            }
        }
    }
}

private fun historyTime(time: Long) = displayTime(time).replace(" · ", " ")

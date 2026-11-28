package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppPageHeader
import com.gogether.app.core.ui.AppFilterChip
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

internal fun PrototypeHistoryEntry.canReview(now: Long) = !cancelled && eligible && feedback == null && hasEnded(now)

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
    Column(Modifier.fillMaxSize().imePadding().then(if (selected?.canReview(now) == true) Modifier else Modifier.padding(horizontal = 16.dp)).testTag("history_screen")) {
        if (selected?.canReview(now) != true) AppPageHeader(when {
            selected == null -> "My records"
            selected.feedback != null -> "My review"
            selected.canReview(now) -> "Event review"
            else -> "Event records"
        }, horizontalInset = 16.dp, onBack = { if (selectedId != null) selectedId = null else back() })
        if (selected != null) {
            key(actor, selected.session.id) {
                HistoryDetail(actor, selected, now, busy, message, submit, Modifier.weight(1f), openCheckIn, openAttendance, openReviews,
                    pendingCheckIn = selected.session.id in pendingCheckIns, back = { selectedId = null })
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilterChip(!pendingOnly, { pendingOnly = false }, label = { Text("All") })
                AppFilterChip(pendingOnly, { pendingOnly = true }, label = { Text("To review ${records.count { it.canReview(now) }}") },
                    modifier = Modifier.testTag("history_pending"))
            }
            val visible = if (pendingOnly) records.filter { it.canReview(now) } else records
            if (visible.isEmpty()) Text(if (pendingOnly) "No events to review yet" else "Your check-ins and reviews will appear here after taking part.",
                Modifier.padding(vertical = 20.dp))
            LazyColumn(Modifier.weight(1f).testTag("history_list"), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)) {
                items(visible, key = { it.session.id }) { entry ->
                    Card(shape = CheckInStyle.shape, onClick = { selectedId = entry.session.id },
                        modifier = Modifier.fillMaxWidth().testTag("history_${entry.session.id}")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(activities.infoOrNull(entry.session)?.title ?: "Event details unavailable", style = MaterialTheme.typography.titleMedium)
                            Text(historyTime(entry.session.startAt), style = MaterialTheme.typography.bodySmall)
                            Text(when {
                                entry.session.id in pendingCheckIns -> "Check-in unconfirmed"
                                entry.cancelled -> "Event cancelled"
                                entry.feedback != null -> "Reviewed ${entry.feedback.rating} / 5"
                                entry.hasEnded(now) -> if (entry.eligible) "Ended. Ready to review." else "Ended. Not eligible to review."
                                entry.receipt != null -> "Checked in. Review after the event."
                                else -> "Not checked in"
                            })
                        }
                    }
                }
            }
        }
    }
}

private fun historyTime(time: Long) = displayTime(time).replace(" · ", " ")

package com.gogether.app.feature.checkin.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Display fixture only. Never save these people or timestamps as real attendance. */
internal val attendanceLayoutExample = listOf("小林", "小陈", "Alex", "小雨", "晨晨", "小周", "Sam", "小李", "小吴", "小赵")
    .mapIndexed { index, name -> PrototypeAttendee("example-$index", name, if (index < 5) 1L else null) }

@Composable
fun PrototypeAttendance(attendees: List<PrototypeAttendee>, modifier: Modifier = Modifier,
    searchOpen: Boolean = false, header: (@Composable () -> Unit)? = null, dismissSearch: () -> Unit = {}) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var selectedPerson by rememberSaveable { mutableStateOf<String?>(null) }
    val detail = attendees.firstOrNull { it.userId == selectedPerson }
    BackHandler(enabled = detail != null && !searchOpen) { selectedPerson = null }
    LaunchedEffect(attendees) {
        if (attendees.none { it.userId == selectedPerson }) selectedPerson = null
    }
    val checked = attendees.filter { it.checkedInAt != null }
    val pending = attendees.filter { it.checkedInAt == null }
    val people = if (selected == 0) checked else pending
    val gridState = rememberLazyGridState()
    LaunchedEffect(selected) { gridState.scrollToItem(0) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (header != null) header() else Text("参与者 ${attendees.size} 人，已签到 ${checked.size} 人", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("attendance_summary"))
        TabRow(selectedTabIndex = selected) {
            val largeText = LocalDensity.current.fontScale > 1.3f
            listOf("已签到" to checked.size, "未签到" to pending.size).forEachIndexed { index, (label, count) ->
                Tab(selected = selected == index, onClick = { selected = index; selectedPerson = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                        .testTag(if (index == 0) "attendance_checked_tab" else "attendance_pending_tab")) {
                    Text(if (largeText) "$label\n$count 人" else "$label（$count）",
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center, maxLines = if (largeText) 2 else 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().testTag(if (selected == 0) "attendance_checked" else "attendance_pending")) {
            if (detail != null) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(detail.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { selectedPerson = null }) { Icon(Icons.Default.Close, "收起签到信息") }
                }
                HorizontalDivider()
                Text(attendanceDetails(detail), Modifier.padding(vertical = 12.dp).testTag("attendance_detail"),
                    style = MaterialTheme.typography.bodyMedium)
            }
            else if (people.isEmpty()) Text(when {
                attendees.isEmpty() -> "暂无正式参与者"
                selected == 0 -> "还没有签到记录"
                else -> "参与者均已签到"
            }, Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyVerticalGrid(columns = GridCells.Adaptive((72 * LocalDensity.current.fontScale.coerceAtMost(2f)).dp),
                state = gridState, modifier = Modifier.fillMaxSize().testTag("attendance_grid"),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(people, key = { it.userId }) { person ->
                    val title = if (selected == 0) "已签到" else "未签到"
                    Column(Modifier.fillMaxWidth().testTag("attendee_${person.userId}")
                        .clickable(onClickLabel = "查看签到信息") { selectedPerson = person.userId }
                        .semantics(mergeDescendants = true) { contentDescription = "${person.name}，$title" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val checked = person.checkedInAt != null
                        Box(Modifier.size(48.dp).background(
                            if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            CircleShape), contentAlignment = Alignment.Center) {
                            Text(person.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge,
                                color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(person.name, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (searchOpen) ParticipantSearchSheet(attendees, dismissSearch)
}

internal fun attendanceDetails(person: PrototypeAttendee): String =
    person.checkedInAt?.let { "已签到\n签到时间：${attendanceTime(it)}" } ?: "尚未签到"

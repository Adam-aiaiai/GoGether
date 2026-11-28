package com.gogether.app.feature.checkin.cloud

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.Event
import com.gogether.app.core.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class CloudAttendancePerson(val userId: String, val name: String, val checkedInAt: Long) {
    val checkedIn get() = checkedInAt > 0
}

internal fun attendancePeople(rows: List<Map<*, *>>) = rows.map {
    CloudAttendancePerson(it.string("userId"), it.string("name").ifBlank { "Participant" }, it.number("checkedInAt"))
}

internal fun attendanceTime(time: Long, pattern: String = "HH:mm"): String =
    Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))

/** Account profiles currently expose names, not photo URLs. Never invent participant photographs. */
@Composable
internal fun AttendanceAvatar(person: CloudAttendancePerson, modifier: Modifier = Modifier) {
    val initials = remember(person.name) {
        person.name.trim().split(Regex("\\s+")).take(2).joinToString("") { it.take(1).uppercase(Locale.ENGLISH) }
    }
    Surface(modifier.semantics { contentDescription = person.name }, shape = CircleShape,
        color = AppUi.SoftAccent, contentColor = AppUi.OnSoftAccent) {
        Box(contentAlignment = Alignment.Center) {
            Text(initials, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun CloudOrganizerAttendance(
    event: Event, people: List<CloudAttendancePerson>, loaded: Boolean, loading: Boolean,
    error: String, updatedAt: Long?, onRefresh: () -> Unit, onBack: () -> Unit, onReviews: () -> Unit,
) {
    var search by rememberSaveable(event.id) { mutableStateOf("") }
    var filter by rememberSaveable(event.id) { mutableStateOf("all") }
    val checked = people.count { it.checkedIn }
    val filtered = people.filter { it.name.contains(search.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize().testTag("cloud_attendance_page")) {
        AppPageHeader("Attendance", onBack, actions = {
            IconButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.testTag("cloud_attendance_refresh")) {
                Icon(Icons.Default.Refresh, "Refresh attendance", tint = AppUi.Primary)
            }
        })
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("cloud_attendance_list"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(if (loaded) "$checked of ${people.size} checked in" else "Attendance",
                    style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("cloud_attendance_count"))
                updatedAt?.let { Text("Updated at ${attendanceTime(it)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                AppOutlinedTextField(search, { search = it }, label = { Text("Search participants") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cloud_attendance_search"))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "All ${people.size}", "checked" to "Checked in $checked",
                        "waiting" to "Not checked in ${people.size - checked}").forEach { (value, label) ->
                        AppFilterChip(filter == value, { filter = value }, { Text(label) },
                            Modifier.testTag("cloud_attendance_filter_$value"))
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("cloud_attendance_loading")) }
            if (error.isNotBlank()) item {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("cloud_attendance_error"))
                TextButton(onRefresh, enabled = !loading, modifier = Modifier.testTag("cloud_attendance_retry")) { Text("Retry") }
            }
            if (loaded) {
                val checkedPeople = filtered.filter { it.checkedIn }.sortedByDescending { it.checkedInAt }
                val waitingPeople = filtered.filterNot { it.checkedIn }
                val shown = when (filter) { "checked" -> checkedPeople; "waiting" -> waitingPeople; else -> filtered }
                if (shown.isEmpty()) item { Text(if (people.isEmpty()) "No participants yet." else "No matching participants.") }
                if (filter != "waiting" && checkedPeople.isNotEmpty()) {
                    item { Text("Checked in (${checkedPeople.size})", style = MaterialTheme.typography.titleMedium) }
                    items(checkedPeople, key = { it.userId }) { AttendanceRow(it) }
                }
                if (filter != "checked" && waitingPeople.isNotEmpty()) {
                    item { Text("Not checked in (${waitingPeople.size})", style = MaterialTheme.typography.titleMedium) }
                    items(waitingPeople, key = { it.userId }) { AttendanceRow(it) }
                }
            }
            item { TextButton(onReviews, modifier = Modifier.testTag("cloud_tab_reviews")) { Text("Event reviews") } }
        }
    }
}

@Composable
private fun AttendanceRow(person: CloudAttendancePerson) {
    Column {
        Row(Modifier.fillMaxWidth().testTag("cloud_attendee_${person.userId}").padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AttendanceAvatar(person, Modifier.size(44.dp))
            Text(person.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(if (person.checkedIn) attendanceTime(person.checkedInAt, "d MMM · HH:mm") else "Not checked in",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 104.dp))
        }
        AppDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

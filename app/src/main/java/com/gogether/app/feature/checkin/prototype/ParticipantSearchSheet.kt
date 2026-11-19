package com.gogether.app.feature.checkin.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun attendanceTime(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParticipantSearchSheet(people: List<PrototypeAttendee>, dismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var expandedPerson by rememberSaveable { mutableStateOf<String?>(null) }
    val matches = people.filter { it.name.contains(query.trim(), ignoreCase = true) }
    LaunchedEffect(matches) { if (matches.none { it.userId == expandedPerson }) expandedPerson = null }
    val keyboard = LocalSoftwareKeyboardController.current
    ModalBottomSheet(onDismissRequest = dismiss, shape = CheckInStyle.sheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("查找参与者", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(shape = CheckInStyle.shape, value = query, onValueChange = { query = it }, label = { Text("输入名字") },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("attendance_search_input"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Close, "清空查找")
                } })
            Text("找到 ${matches.size} 人", style = MaterialTheme.typography.bodySmall)
            if (matches.isEmpty()) Text("没有找到这个名字，请换个名字试试。", Modifier.padding(vertical = 16.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("attendance_search_results"),
                contentPadding = PaddingValues(bottom = 16.dp)) {
                items(matches, key = { it.userId }) { person ->
                    Column {
                        Row(Modifier.fillMaxWidth().testTag("search_person_${person.userId}")
                            .clickable(onClickLabel = if (expandedPerson == person.userId) "收起签到信息" else "查看签到信息") {
                                keyboard?.hide()
                                expandedPerson = if (expandedPerson == person.userId) null else person.userId
                            }
                            .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center) { Text(person.name.take(1).uppercase()) }
                            Column(Modifier.weight(1f)) {
                                Text(person.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (person.checkedInAt == null) "未签到" else "已签到", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (expandedPerson == person.userId) Text(attendanceDetails(person),
                            Modifier.fillMaxWidth().padding(start = 60.dp, bottom = 12.dp).testTag("attendance_detail"),
                            style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

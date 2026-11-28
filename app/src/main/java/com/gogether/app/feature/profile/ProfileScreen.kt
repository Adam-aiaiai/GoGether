package com.gogether.app.feature.profile

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogether.app.core.account.AccountMode
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.checkin.prototype.PrototypeCheckInActivity

@Composable
fun ProfileScreen(state: AppSnapshot, now: Long, busy: Boolean, mode: AccountMode, storageDescription: String,
    edit: () -> Unit, switchUser: (String) -> Unit, logout: () -> Unit,
    openEvent: (String) -> Unit, moreHistory: () -> Unit = {}) {
    val user = state.currentUser ?: return
    val context = LocalContext.current
    val history = personalEvents(state, PersonalEventTab.HISTORY, now)
    var accountMenu by remember { mutableStateOf(false) }
    var confirmLogout by remember(user.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("profile_page")) {
        ProfilePhoto(user.coverUri.ifBlank { defaultCover(context) }, "Profile cover",
            Modifier.fillMaxWidth().height(174.dp).testTag("profile_cover"))
        Column(Modifier.padding(horizontal = 20.dp)) {
            Box(Modifier.fillMaxWidth().height(58.dp)) {
                ProfileAvatar(user, Modifier.offset(y = (-30).dp).testTag("profile_avatar"))
                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                    CompactProfileAction("Edit profile", edit, Modifier.testTag("edit_profile"), !busy)
                    Box {
                        IconButton({ accountMenu = true }, Modifier.size(40.dp).testTag("profile_account_menu")) {
                            Icon(Icons.Default.MoreVert, "Account options", Modifier.size(21.dp))
                        }
                        DropdownMenu(accountMenu, { accountMenu = false }) {
                            if (mode == AccountMode.LOCAL_DEMO) {
                                DropdownMenuItem(text = { Text("Check-in and review records") }, onClick = {
                                    accountMenu = false; context.startActivity(Intent(context, PrototypeCheckInActivity::class.java))
                                }, modifier = Modifier.testTag("checkin_prototype"))
                                state.users.filter { it.id != user.id && it.status == UserStatus.DEMO }.forEach { target ->
                                    DropdownMenuItem(text = { Text("Switch to ${target.displayName}") }, enabled = !busy,
                                        onClick = { accountMenu = false; switchUser(target.id) }, modifier = Modifier.testTag("switch_${target.id}"))
                                }
                            }
                            DropdownMenuItem(text = { Text("Log out") }, enabled = !busy,
                                onClick = { accountMenu = false; confirmLogout = true }, modifier = Modifier.testTag("logout"))
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().height(76.dp)) {
                Text(user.displayName, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(user.signature.ifBlank { DefaultSignature }, Modifier.padding(top = 5.dp),
                    fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val joined = state.registrations.count { it.userId == user.id && it.status == RegistrationStatus.CONFIRMED }
                val created = state.events.count { it.organizerId == user.id }
                listOf(joined to "Joined", created to "Created").forEach { (count, label) ->
                    Surface(Modifier.weight(1f), color = ProfileGray, shape = AppUi.FieldShape) {
                        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$count", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Interests", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            ProfileInterestRows(user.interests, Modifier.fillMaxWidth().height(82.dp).padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Activity history", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                TextButton(moreHistory, modifier = Modifier.testTag("profile_history_more"), contentPadding = PaddingValues(0.dp)) {
                    Text("More ›", fontSize = 12.sp)
                }
            }
            repeat(3) { index ->
                history.getOrNull(index)?.let { event -> ProfileActivityRow(event) { openEvent(event.id) } }
                    ?: Box(Modifier.fillMaxWidth().height(64.dp)) {
                        if (index == 0) Text("Your past activities will appear here.", Modifier.padding(top = 14.dp),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirmLogout) ConfirmDialog("Log out?", "Your saved profile and activity records will remain available.",
        "Log out", { confirmLogout = false }, logout)
}

@Composable
private fun ProfileInterestRows(interests: Set<Category>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val labels = Category.entries.filter { it in interests }.map { it.interestLabel() }
        val available = with(density) { maxWidth.toPx() }
        val gap = with(density) { 8.dp.toPx() }
        fun width(label: String) = measurer.measure(AnnotatedString(label), TextStyle(fontSize = 11.sp,
            fontWeight = FontWeight.Medium)).size.width + with(density) { 18.dp.toPx() }
        val rows = mutableListOf(mutableListOf<String>(), mutableListOf())
        var row = 0
        for (label in labels) {
            val required = rows[row].sumOf { width(it).toDouble() }.toFloat() + gap * rows[row].size + width(label)
            if (required > available && rows[row].isNotEmpty()) row++
            if (row > 1) break
            rows[row].add(label)
        }
        var hidden = labels.size - rows.sumOf { it.size }
        if (hidden > 0) {
            while (rows[1].isNotEmpty() && rows[1].sumOf { width(it).toDouble() } + gap * rows[1].size + width("+$hidden") > available) {
                rows[1].removeAt(rows[1].lastIndex); hidden++
            }
            rows[1].add("+$hidden")
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (labels.isEmpty()) Text("Open to trying something new", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEach { labelsInRow -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labelsInRow.forEach { InterestLabel(it) }
            } }
        }
    }
}

@Composable
fun ProfileHistoryScreen(state: AppSnapshot, now: Long, openEvent: (String) -> Unit) {
    val history = personalEvents(state, PersonalEventTab.HISTORY, now)
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().testTag("profile_history_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        if (history.isEmpty()) item { Text("Your past activities will appear here.", Modifier.padding(vertical = 20.dp),
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(history.size, key = { history[it].id }) { index -> ProfileActivityRow(history[index]) { openEvent(history[index].id) } }
    }
}

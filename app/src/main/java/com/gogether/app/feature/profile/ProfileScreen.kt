package com.gogether.app.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.gogether.app.feature.checkin.prototype.PrototypeCheckInActivity
import com.gogether.app.core.account.AccountMode
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.coordination.NotificationSection

@Composable
fun ProfileScreen(state: AppSnapshot, now: Long, busy: Boolean, mode: AccountMode, storageDescription: String,
                  edit: () -> Unit, switchUser: (String) -> Unit, logout: () -> Unit,
                  read: (String) -> Unit, readAll: () -> Unit, explore: () -> Unit, openEvent: (String) -> Unit, openQuestion: ((String, String) -> Unit)? = null) {
    val user = state.currentUser ?: return
    val context = LocalContext.current
    val history = personalEvents(state, PersonalEventTab.HISTORY, now)
    var confirmLogout by remember(user.id) { mutableStateOf(false) }
    Page {
        SectionTitle("Hey, ${user.displayName}", "Make room for a little more together.")
        StatusPill(if (mode == AccountMode.LOCAL_DEMO) "Demo profile" else "Account")
        user.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        InfoCard("Your interests", user.interests.joinToString { it.label }.ifEmpty { "Ready to try something new" })
        Button(onClick = edit, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("edit_profile")) { Text("Edit profile") }
        InfoCard("Your plans", "${personalEvents(state, PersonalEventTab.JOINED, now).size} joined · " +
            "${personalEvents(state, PersonalEventTab.WAITLISTED, now).size} waitlisted · " +
            "${personalEvents(state, PersonalEventTab.CREATED, now).size} created")
        InfoCard("Your data", storageDescription)
        OutlinedButton(onClick = { context.startActivity(Intent(context, PrototypeCheckInActivity::class.java)) },
            modifier = Modifier.fillMaxWidth().testTag("checkin_prototype")) { Text("Check-in and review records") }
        if (mode == AccountMode.LOCAL_DEMO) {
            Text("Switch test user", style = MaterialTheme.typography.titleLarge)
            state.users.filter { it.id != user.id && it.status == UserStatus.DEMO }.forEach { target ->
                OutlinedButton(onClick = { switchUser(target.id) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("switch_${target.id}")) { Text("Switch to ${target.displayName}") }
            }
        }
        TextButton(onClick = { confirmLogout = true }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("logout")) { Text("Log out") }
        NotificationSection(state, busy, read, readAll, openEvent, explore, openQuestion)
        Text("Activity history", style = MaterialTheme.typography.titleLarge)
        if (history.isEmpty()) Text("Your past plans will appear here.")
        history.forEach { event ->
            EventCard(event, state, { openEvent(event.id) }, now)
            Text(participationSummary(state, event, now), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmLogout) ConfirmDialog("Log out?", "Your saved profile and activity records will remain available.",
        "Log out", { confirmLogout = false }, logout)
}

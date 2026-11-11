package com.gogether.app.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*

@Composable
fun AuthScreen(state: AppSnapshot, busy: Boolean, login: (String) -> Unit, register: (String, Set<Category>) -> Unit) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf(Category.SPORTS.name)) }
    Page {
        Spacer(Modifier.height(20.dp))
        Text("Gogether", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        SectionTitle("Find your people.\nMake it happen.", "Small plans. Good company. A little more offline.")
        InfoCard("Demo Mode", "Local test profiles only. No password or real authentication. Data resets when the app process restarts.")
        TabRow(selectedTabIndex = if (registering) 1 else 0) {
            Tab(selected = !registering, onClick = { registering = false }, text = { Text("Demo login") })
            Tab(selected = registering, onClick = { registering = true }, text = { Text("Demo register") })
        }
        if (!registering) {
            Text("Choose a test profile", style = MaterialTheme.typography.titleLarge)
            Text("Alice hosts badminton. Bob has a place. Emma is first on the waitlist.", style = MaterialTheme.typography.bodyMedium)
            state.users.forEach { user ->
                OutlinedButton(onClick = { login(user.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("login_${user.id}")) {
                    Text("Continue as ${user.displayName}", Modifier.padding(6.dp))
                }
            }
        } else {
            OutlinedTextField(nickname, { nickname = it }, label = { Text("Demo nickname") },
                supportingText = { Text("2–30 characters. Use fictional details.") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("nickname"))
            Text("Your interests", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { category ->
                    FilterChip(selected = category.name in selected, onClick = {
                        selected = if (category.name in selected) selected - category.name else selected + category.name
                    }, label = { Text(category.label) })
                }
            }
            Button(onClick = { register(nickname, selected.map(Category::valueOf).toSet()) },
                enabled = !busy && nickname.trim().length in 2..30, modifier = Modifier.fillMaxWidth()) {
                Text("Create Demo profile")
            }
        }
    }
}

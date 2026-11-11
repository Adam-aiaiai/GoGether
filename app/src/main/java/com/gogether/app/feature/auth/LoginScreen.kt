package com.gogether.app.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gogether.app.core.ui.DemoBadge
import com.gogether.app.core.ui.GogetherViewModel

@Composable
fun LoginScreen(viewModel: GogetherViewModel) {
    val users by viewModel.users.collectAsState()
    var selected by remember { mutableStateOf(users.firstOrNull()?.id.orEmpty()) }
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Spacer(Modifier.height(24.dp)); DemoBadge(); Text("Gogether", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold); Text("Find your people. Share the moment.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Text("Local demo login only. This is not secure authentication and stores no real account data.", Modifier.padding(16.dp)) } }
            item { Text("Choose a demo user", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth()) }
            items(users) { user ->
                ElevatedCard(onClick = { selected = user.id }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = if (selected == user.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == user.id, onClick = { selected = user.id })
                        Column(Modifier.padding(start = 8.dp)) { Text(user.nickname, fontWeight = FontWeight.SemiBold); Text(user.interests.joinToString(" · "), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { Button(onClick = { viewModel.login(selected) }, enabled = selected.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Continue to demo") }; TextButton(onClick = { viewModel.login(selected) }, enabled = selected.isNotBlank()) { Text("Register demo profile") } }
        }
    }
}

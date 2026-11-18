package com.gogether.app.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.gogether.app.core.account.AccountMode
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
fun AuthScreen(state: AppSnapshot, busy: Boolean, login: (String) -> Unit, register: (String, Set<Category>) -> Unit,
               mode: AccountMode, storageDescription: String,
               signIn: (String, String) -> Unit, signUp: (String, String, String, Set<Category>) -> Unit,
               retryRestore: () -> Unit) {
    if (mode == AccountMode.CONNECTED) {
        AccountSignInScreen(state, busy, signIn, signUp, retryRestore)
        return
    }
    var registering by rememberSaveable { mutableStateOf(false) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf(Category.SPORTS.name)) }
    Page {
        Spacer(Modifier.height(20.dp))
        Text("Gogether", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        SectionTitle("Find your people.\nMake it happen.", "Small plans. Good company. A little more offline.")
        InfoCard("Demo profiles", storageDescription)
        Text("Email accounts will be available when the team account service is connected.", style = MaterialTheme.typography.bodySmall)
        TabRow(selectedTabIndex = if (registering) 1 else 0) {
            Tab(selected = !registering, onClick = { registering = false }, text = { Text("Demo login") })
            Tab(selected = registering, onClick = { registering = true }, text = { Text("Demo register") })
        }
        if (!registering) {
            Text("Choose a test profile", style = MaterialTheme.typography.titleLarge)
            Text("Alice hosts badminton. Bob has a place. Emma is first on the waitlist.", style = MaterialTheme.typography.bodyMedium)
            state.users.filter { it.status == UserStatus.DEMO }.forEach { user ->
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


@Composable
private fun AccountSignInScreen(state: AppSnapshot, busy: Boolean, signIn: (String, String) -> Unit,
                                signUp: (String, String, String, Set<Category>) -> Unit, retryRestore: () -> Unit) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    // Passwords are deliberately not written to saved instance state or local storage.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var interests by rememberSaveable { mutableStateOf(emptyList<String>()) }
    Page {
        SectionTitle("Welcome to Gogether", "Sign in to your team account.")
        state.sessionError?.let {
            InfoCard("Account connection", it)
            TextButton(onClick = retryRestore, enabled = !busy) { Text("Retry connection") }
        }
        TabRow(if (registering) 1 else 0) {
            Tab(!registering, { registering = false; password = ""; confirmation = "" }, text = { Text("Sign in") })
            Tab(registering, { registering = true; password = ""; confirmation = "" }, text = { Text("Create account") })
        }
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, enabled = !busy, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().testTag("account_email"))
        if (registering) {
            OutlinedTextField(name, { name = it }, label = { Text("Nickname") }, enabled = !busy, singleLine = true,
                supportingText = { Text("2–30 characters") }, modifier = Modifier.fillMaxWidth().testTag("account_name"))
        }
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, enabled = !busy, singleLine = true,
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide" else "Show") } },
            modifier = Modifier.fillMaxWidth().testTag("account_password"))
        if (registering) {
            OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Confirm password") }, enabled = !busy,
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = confirmation.isNotEmpty() && confirmation != password,
                supportingText = { Text(if (confirmation.isNotEmpty() && confirmation != password) "Passwords do not match." else "At least 8 characters") },
                modifier = Modifier.fillMaxWidth().testTag("account_confirm"))
            Text("Interests (optional)")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { category ->
                    FilterChip(category.name in interests, {
                        interests = if (category.name in interests) interests - category.name else interests + category.name
                    }, label = { Text(category.label) }, enabled = !busy)
                }
            }
        }
        Button(onClick = {
            if (registering) signUp(email, password, name, interests.map(Category::valueOf).toSet()) else signIn(email, password)
        }, enabled = !busy && email.isNotBlank() && password.isNotEmpty() &&
            (!registering || (name.trim().length in 2..30 && password.length >= 8 && confirmation == password)),
            modifier = Modifier.fillMaxWidth().testTag("account_submit")) {
            Text(if (busy) "Please wait…" else if (registering) "Create account" else "Sign in")
        }
    }
}

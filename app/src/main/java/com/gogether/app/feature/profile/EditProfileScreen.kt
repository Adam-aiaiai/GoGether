package com.gogether.app.feature.profile

import com.gogether.app.core.ui.AppOutlinedTextField
import com.gogether.app.core.ui.AppFilterChip
import com.gogether.app.core.ui.PageActionButton
import com.gogether.app.core.ui.OutlinedPageActionButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gogether.app.core.account.AccountRules
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*

@Composable
fun EditProfileScreen(user: User, busy: Boolean, save: (String, Set<Category>) -> Unit, back: () -> Unit) {
    var name by rememberSaveable(user.id) { mutableStateOf(user.displayName) }
    var interests by rememberSaveable(user.id) { mutableStateOf(user.interests.map { it.name }) }
    val error = runCatching { AccountRules.name(name) }.exceptionOrNull()?.message
    val changed = name.trim() != user.displayName || interests.toSet() != user.interests.map { it.name }.toSet()
    Page {
        if (!LocalPageHeaderProvided.current) SectionTitle("Edit your profile", "Let others know what you enjoy.")
        else Text("Let others know what you enjoy.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppOutlinedTextField(name, { name = it }, enabled = !busy, singleLine = true,
            label = { Text("Nickname") }, isError = error != null,
            supportingText = { Text(error ?: "2–30 characters") },
            modifier = Modifier.fillMaxWidth().testTag("profile_name"))
        Text("Your interests", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { category ->
                AppFilterChip(category.name in interests, {
                    interests = if (category.name in interests) interests - category.name else interests + category.name
                }, enabled = !busy, label = { Text(category.label) }, modifier = Modifier.testTag("interest_${category.name}"))
            }
        }
        Text("Interests are optional. You can change them at any time.", style = MaterialTheme.typography.bodySmall)
        PageActionButton(onClick = { save(name, interests.map(Category::valueOf).toSet()) },
            enabled = !busy && changed && error == null, modifier = Modifier.fillMaxWidth().testTag("save_profile")) {
            Text(if (busy) "Saving…" else "Save changes")
        }
        OutlinedPageActionButton(onClick = back, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

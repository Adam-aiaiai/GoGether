package com.gogether.app.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogether.app.core.account.AccountRules
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun EditProfileScreen(user: User, busy: Boolean, save: (ProfileUpdate) -> Unit, back: () -> Unit) {
    var name by rememberSaveable(user.id) { mutableStateOf(user.displayName) }
    var interests by rememberSaveable(user.id) { mutableStateOf(user.interests.map { it.name }) }
    var avatar by rememberSaveable(user.id) { mutableStateOf(user.avatarUri) }
    var cover by rememberSaveable(user.id) { mutableStateOf(user.coverUri) }
    var gender by rememberSaveable(user.id) { mutableStateOf(user.gender.name) }
    var showGender by rememberSaveable(user.id) { mutableStateOf(user.showGender) }
    var signature by rememberSaveable(user.id) { mutableStateOf(user.signature) }
    var genderMenu by remember { mutableStateOf(false) }
    var choosingCover by rememberSaveable { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val isCover = choosingCover
            importing = true
            scope.launch {
                try {
                    val result = importProfilePhoto(context, uri)
                    if (isCover) cover = result else avatar = result
                    imageError = null
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { imageError = error.message ?: "Could not open this photo." }
                finally { importing = false }
            }
        }
    }
    val draft = ProfileUpdate(name, interests.map(Category::valueOf).toSet(), avatar, cover,
        Gender.valueOf(gender), showGender, signature)
    val original = ProfileUpdate(user.displayName, user.interests, user.avatarUri, user.coverUri,
        user.gender, user.showGender, user.signature)
    val error = runCatching { AccountRules.name(name) }.exceptionOrNull()?.message
    val enabled = !busy && !importing
    Column(Modifier.fillMaxSize().imePadding().testTag("edit_profile_page")) {
        Row(Modifier.fillMaxWidth().height(56.dp).background(Color.White).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AppBackButton(back, Modifier.testTag("edit_profile_back"))
            Spacer(Modifier.weight(1f))
            CompactProfileAction(if (busy) "Saving…" else "Save", { save(draft) },
                Modifier.padding(end = 8.dp).testTag("save_profile"), enabled && draft != original && error == null)
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                ProfilePhoto(cover.ifBlank { defaultCover(context) }, "Profile cover",
                    Modifier.fillMaxSize().testTag("edit_profile_cover"))
                Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), color = Color.White.copy(alpha = .94f),
                    shape = AppUi.TagShape) {
                    TextButton({ choosingCover = true; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = enabled, modifier = Modifier.testTag("change_cover")) {
                        Text("Change cover", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(64.dp)) {
                ProfileAvatar(user.copy(displayName = name, avatarUri = avatar, gender = Gender.valueOf(gender), showGender = showGender),
                    Modifier.align(Alignment.TopCenter).offset(y = (-38).dp).testTag("edit_profile_avatar"))
                TextButton({ choosingCover = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = enabled, modifier = Modifier.align(Alignment.BottomCenter).offset(y = 10.dp).testTag("change_avatar"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(13.dp)); Spacer(Modifier.width(5.dp))
                    Text(if (importing) "Opening photo…" else "Change photo", fontSize = 11.sp)
                }
            }
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                imageError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                AppOutlinedTextField(name, { name = it }, enabled = enabled, singleLine = true,
                    label = { Text("Nickname", fontSize = 12.sp) }, isError = error != null,
                    textStyle = TextStyle(fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth().testTag("profile_name"))
                if (error != null) Text(error, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(.43f)) {
                        AppOutlinedPickerField("Gender", Gender.valueOf(gender).label, "gender", Modifier.fillMaxWidth().testTag("profile_gender"),
                            enabled = enabled, textStyle = TextStyle(fontSize = 12.sp), onClick = { genderMenu = true })
                        DropdownMenu(genderMenu, { genderMenu = false }) {
                            Gender.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label, fontSize = 13.sp) },
                                onClick = { gender = option.name; if (option == Gender.UNDISCLOSED) showGender = false; genderMenu = false },
                                modifier = Modifier.testTag("gender_${option.name}")) }
                        }
                    }
                    Row(Modifier.weight(.57f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Show on profile", fontSize = 11.sp, maxLines = 1)
                        Switch(showGender, { showGender = it }, enabled = enabled && gender != Gender.UNDISCLOSED.name,
                            modifier = Modifier.testTag("profile_gender_visible"))
                    }
                }
                Text("Signature", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                BasicTextField(signature, { signature = it.take(100) }, enabled = enabled,
                    textStyle = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(AppUi.Primary), maxLines = 2,
                    modifier = Modifier.fillMaxWidth().clip(AppUi.FieldShape).background(ProfileGray).testTag("profile_signature"),
                    decorationBox = { input -> Column(Modifier.padding(12.dp)) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 34.dp)) {
                            if (signature.isEmpty()) Text(DefaultSignature, fontSize = 12.sp, lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            input()
                        }
                        Text("${signature.length}/100", Modifier.align(Alignment.End), fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } })
                Text("Your interests", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Category.entries.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEach { category -> InterestLabel(category.interestLabel(),
                            Modifier.weight(1f).testTag("interest_${category.name}"), category.name in interests,
                            onClick = if (enabled) ({ interests = if (category.name in interests) interests - category.name else interests + category.name }) else null) }
                    }
                }
            }
        }
    }
}

package com.gogether.app.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import kotlinx.coroutines.CancellationException

internal val ProfileGray = Color(0xFFF5F5F7)
internal fun Category.interestLabel() = when (this) {
    Category.LANGUAGE -> "Language Exchange"
    Category.READING -> "Book Clubs"
    else -> label
}

@Composable
internal fun CompactProfileAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val action = rememberBouncingAction(source, enabled, onClick)
    Button(action.click, modifier.heightIn(min = 40.dp).pressBounce(action.scale, CircleShape, enabled),
        enabled = enabled, shape = CircleShape, interactionSource = source,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
internal fun ProfilePhoto(source: String, description: String, modifier: Modifier, fallback: String = "") {
    val context = LocalContext.current
    var preview by remember(source) { mutableStateOf(false) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, source) {
        value = try { loadProfilePhoto(context, source) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { null }
    }
    Box(modifier.background(ProfileGray).clickable(enabled = bitmap != null) { preview = true },
        contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Text(fallback, fontSize = 24.sp, color = AppUi.OnSoftAccent)
    }
    if (preview) {
        val loader: suspend (String) -> android.graphics.Bitmap? = remember(context) { { path -> loadProfilePhoto(context, path) } }
        // The shared gallery centers the full image and positions × outside its top-right corner.
        AppPhotoGallery(listOf(source), 0, loader, { preview = false })
    }
}

@Composable
internal fun ProfileAvatar(user: User, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 88.dp) {
    Box(modifier.requiredSize(size)) {
        ProfilePhoto(user.avatarUri, "${user.displayName}'s avatar",
            Modifier.fillMaxSize().border(4.dp, Color.White, CircleShape).padding(4.dp).clip(CircleShape),
            fallback = user.displayName.take(1).uppercase())
        if (user.showGender && user.gender != Gender.UNDISCLOSED) {
            val color = when(user.gender) { Gender.WOMAN -> Color(0xFFE96987); Gender.MAN -> Color(0xFF428CC8); else -> Color(0xFF588A77) }
            Box(Modifier.align(Alignment.BottomEnd).size(25.dp).background(color, CircleShape)
                .border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Text(user.gender.symbol, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun InterestLabel(text: String, modifier: Modifier = Modifier, selected: Boolean = true, onClick: (() -> Unit)? = null) {
    Surface(modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(10.dp), color = if (selected) AppUi.SelectedTag else ProfileGray) {
        Box(Modifier.heightIn(min = 32.dp).padding(horizontal = 9.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,
                color = if (selected) AppUi.SelectedTagText else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun ProfileActivityRow(event: Event, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = open)
        .testTag("history_${event.id}").padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(AppUi.SoftAccent), contentAlignment = Alignment.Center) {
            if (event.imageUri.isNotBlank()) ProfilePhoto(event.imageUri, event.title, Modifier.fillMaxSize())
            else Text(event.category.symbol, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(event.title, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(displayTime(event.startAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

package com.gogether.app.feature.registration

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.gogether.app.core.model.AppSnapshot
import com.gogether.app.core.model.Event

private const val StaggerMillis = 40
private const val MoveMillis = 360
private const val CollapsedFaces = 3
private val AvatarColors = listOf(
    Color(0xFFE6D9FF), Color(0xFFFFDCBE), Color(0xFFD4EBDD),
    Color(0xFFD9E7FF), Color(0xFFFFDCE6), Color(0xFFF3E8BC),
)

/** Uses confirmed registrations only, so a waitlist entry never looks like a secured place. */
@Composable
fun ParticipantsCard(event: Event, state: AppSnapshot, modifier: Modifier = Modifier) {
    val participants = state.confirmed(event.id).sortedBy { it.queueOrder }
    var expanded by rememberSaveable(event.id) { mutableStateOf(false) }
    val transition = updateTransition(expanded, label = "participants")
    val scroll = rememberScrollState()
    val lastIndex = participants.lastIndex.coerceAtLeast(0)
    val lastDelay = lastIndex * StaggerMillis
    val avatarSize = 52.dp
    val cellWidth = 72.dp
    val overlapStep = avatarSize * (2f / 3f)
    val avatarInset = (cellWidth - avatarSize) / 2
    val nameHeight = with(LocalDensity.current) { 32.sp.toDp() }
    val rowHeight by transition.animateDp(
        transitionSpec = {
            tween(durationMillis = MoveMillis, delayMillis = if (targetState) 0 else lastDelay)
        }, label = "participant row height",
    ) { if (it) avatarSize + 8.dp + nameHeight else avatarSize }
    val chevronRotation by transition.animateFloat(label = "participant toggle rotation") {
        if (it) 180f else 0f
    }
    // Keep the full row measured until the final avatar returns to the stack.
    val showingRow = expanded || transition.currentState || transition.isRunning
    val compactWidth = overlapStep * minOf(lastIndex, CollapsedFaces) + avatarSize + avatarInset * 2
    val rowWidth = if (showingRow) cellWidth * participants.size else compactWidth
    val action = if (expanded) "Hide people" else "Show people"
    val toggle = { expanded = !expanded }

    LaunchedEffect(expanded) {
        if (!expanded) scroll.animateScrollTo(0)
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("participants_card"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Who's coming", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (participants.isNotEmpty()) {
                    TextButton(
                        onClick = toggle,
                        modifier = Modifier.testTag("participants_toggle").semantics {
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                        },
                        contentPadding = PaddingValues(start = 8.dp),
                    ) {
                        Text(action, style = MaterialTheme.typography.labelMedium)
                        Icon(
                            Icons.Default.KeyboardArrowDown, contentDescription = null,
                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevronRotation },
                        )
                    }
                }
            }
            Text(
                "${participants.size} / ${event.capacity} confirmed (includes organizer)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (participants.isEmpty()) {
                Text("No confirmed participants yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Box(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(scroll, enabled = expanded)
                        .clickable(role = Role.Button, onClickLabel = action, onClick = toggle)
                        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                        .testTag("participant_avatars"),
                ) {
                    Box(Modifier.width(rowWidth).height(rowHeight)) {
                        participants.forEachIndexed { index, participant ->
                            key(participant.id) {
                                val delay = if (expanded) index * StaggerMillis else (lastIndex - index) * StaggerMillis
                                val x by transition.animateDp(
                                    transitionSpec = { tween(MoveMillis, delay, FastOutSlowInEasing) },
                                    label = "avatar position ${participant.id}",
                                ) { open ->
                                    if (open) cellWidth * index
                                    else overlapStep * minOf(index, CollapsedFaces)
                                }
                                val opacity by transition.animateFloat(
                                    transitionSpec = { tween(180, delay) },
                                    label = "avatar opacity ${participant.id}",
                                ) { open -> if (open || index < CollapsedFaces) 1f else 0f }
                                val nameOpacity by transition.animateFloat(
                                    transitionSpec = { tween(180, delay + if (targetState) 160 else 0) },
                                    label = "participant name ${participant.id}",
                                ) { if (it) 1f else 0f }
                                val name = state.userName(participant.userId)
                                val colorIndex = Math.floorMod(participant.userId.hashCode(), AvatarColors.size)
                                val visible = expanded || index < CollapsedFaces
                                Column(
                                    modifier = Modifier.offset { IntOffset(x.roundToPx(), 0) }.width(cellWidth)
                                        .zIndex(index.toFloat())
                                        .graphicsLayer { alpha = opacity }
                                        .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    InitialAvatar(name, AvatarColors[colorIndex], Modifier
                                        .size(avatarSize).testTag("participant_avatar_${participant.userId}"))
                                    if (showingRow) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            name,
                                            modifier = Modifier.fillMaxWidth().height(nameHeight)
                                                .graphicsLayer { alpha = nameOpacity }
                                                .then(if (expanded) Modifier else Modifier.clearAndSetSemantics {})
                                                .testTag("participant_name_${participant.userId}"),
                                            style = MaterialTheme.typography.labelMedium,
                                            textAlign = TextAlign.Center, maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        val remaining = participants.size - CollapsedFaces
                        if (remaining > 0) {
                            val badgeOpacity by transition.animateFloat(
                                transitionSpec = {
                                    tween(120, delayMillis = if (targetState) 0 else lastDelay + 180)
                                }, label = "remaining participants",
                            ) { if (it) 0f else 1f }
                            Surface(
                                modifier = Modifier.offset(x = overlapStep * CollapsedFaces + avatarInset)
                                    .size(avatarSize).zIndex(participants.size.toFloat())
                                    .graphicsLayer { alpha = badgeOpacity }
                                    .clearAndSetSemantics {
                                        if (!expanded) contentDescription = "$remaining more participants"
                                    }.testTag("participant_overflow"),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("+$remaining", fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "${state.waitlist(event.id).size} on the waitlist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InitialAvatar(name: String, color: Color, modifier: Modifier = Modifier) {
    val initials = remember(name) {
        name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
    }
    Surface(
        modifier = modifier.semantics { contentDescription = name },
        shape = CircleShape, color = color, contentColor = Color(0xFF30213F),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(initials, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

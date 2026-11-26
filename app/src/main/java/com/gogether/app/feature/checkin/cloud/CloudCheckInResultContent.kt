package com.gogether.app.feature.checkin.cloud

import com.gogether.app.core.ui.AppPageHeader
import com.gogether.app.core.ui.AppUi
import com.gogether.app.core.ui.PageActionButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogether.app.core.model.Event
import com.gogether.app.core.ui.EventImage

/** Done and Back dismiss this result to E's scan entry, never to Explore. */
@Composable
internal fun CloudCheckInResultContent(
    event: Event,
    participantName: String,
    organizerName: String,
    checkedInAt: Long,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    val dark = isSystemInDarkTheme()
    val background = if (dark) MaterialTheme.colorScheme.surface else Color.White
    val primaryText = if (dark) MaterialTheme.colorScheme.onSurface else Color(0xFF26272B)
    val secondaryText = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF70737A)
    val success = if (dark) Color(0xFF76D99A) else Color(0xFF14853D)
    val panelTop = if (dark) MaterialTheme.colorScheme.surface else Color(0xFFFCFAFC)
    val panelBottom = if (dark) MaterialTheme.colorScheme.surfaceContainer else AppUi.Lilac

    Surface(color = background, contentColor = primaryText, modifier = Modifier.fillMaxSize().testTag("cloud_checkin_result")) {
        Column {
            AppPageHeader("Check-in result", onBack = onDone,
                backModifier = Modifier.testTag("cloud_checkin_result_back"), backDescription = "Back to check-in")
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.fillMaxWidth().drawBehind {
                        val inset = 18.dp.toPx()
                        val curve = Path().apply {
                            lineTo(size.width, 0f)
                            lineTo(size.width, size.height - inset)
                            quadraticTo(size.width / 2, size.height + inset, 0f, size.height - inset)
                            close()
                        }
                        drawPath(curve, Brush.verticalGradient(listOf(panelTop, panelBottom)))
                    }.padding(horizontal = 20.dp).padding(top = 32.dp, bottom = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(30.dp), tint = success)
                        Text("Check-in successful", Modifier.weight(1f, fill = false).testTag("cloud_participant_status"),
                            fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = success)
                    }
                    // Keep the approved result/card/button positions after removing the redundant sentence.
                    val captionHeight = with(LocalDensity.current) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
                    Spacer(Modifier.height(captionHeight + 36.dp))

                    Surface(shape = AppUi.CardShape, color = background, contentColor = primaryText) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Category stays visible if the event has no cover or its cover is unavailable.
                                Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(AppUi.SelectedTag),
                                    contentAlignment = Alignment.Center) {
                                    Text(event.category.symbol, fontSize = 30.sp)
                                    EventImage(event.imageUri, event.title, Modifier.fillMaxSize())
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("cloud_checkin_event"))
                                    Text("Hosted by $organizerName", style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                                }
                            }
                            ResultFact("Activity", checkInDate(event.startAt, "EEE, d MMM") + "  " +
                                checkInDate(event.startAt, "HH:mm") + "–" + checkInDate(event.endAt, "HH:mm"), secondaryText)
                            HorizontalDivider(color = secondaryText.copy(alpha = .12f))
                            ResultFact("Checked in", checkInDate(checkedInAt, "d MMM yyyy, HH:mm"), secondaryText,
                                Modifier.testTag("cloud_checkin_receipt"))
                            ResultFact("Meet at", checkInDate(event.meetingAt ?: event.startAt, "HH:mm") + "  " +
                                (event.meetingLocation ?: event.location) + "\n" + event.city, secondaryText,
                                Modifier.testTag("cloud_result_meeting"))
                            MeetingActions(event, secondaryText)
                        }
                    }
                }
                PageActionButton(onClick = onDone, modifier = Modifier
                    .padding(top = 28.dp, bottom = 24.dp)
                    .fillMaxWidth().testTag("cloud_checkin_done")) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun ResultFact(label: String, value: String, labelColor: Color, modifier: Modifier = Modifier) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.width(76.dp), style = MaterialTheme.typography.bodyMedium, color = labelColor)
        Text(value, modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

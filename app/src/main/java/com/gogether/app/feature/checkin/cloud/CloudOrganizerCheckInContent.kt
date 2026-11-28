package com.gogether.app.feature.checkin.cloud

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogether.app.core.model.Event
import com.gogether.app.core.ui.*
import com.gogether.app.feature.checkin.CheckInWindow
import com.gogether.app.feature.checkin.CheckInWindowState
import com.journeyapps.barcodescanner.BarcodeEncoder

/** UI only: QR issuance, location, eligibility and attendance still belong to the caller. */
@Composable
internal fun CloudOrganizerCheckInContent(
    event: Event, now: Long, checkInReady: Boolean, code: String?, codeExpiresAt: Long,
    showingCode: Boolean, loading: Boolean, hasInformation: Boolean, busy: Boolean, message: String,
    people: List<CloudAttendancePerson>, rosterLoaded: Boolean, rosterLoading: Boolean, rosterError: String,
    onBack: () -> Unit, onRefresh: () -> Unit, onReviews: () -> Unit, onAttendance: () -> Unit,
    onShowCode: () -> Unit, onHideCode: () -> Unit, onConfirmArrival: () -> Unit,
    extraMenuItems: @Composable (closeMenu: () -> Unit) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    var sheet by rememberSaveable(event.id) { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { if (message.isNotBlank()) snackbar.showSnackbar(message) }
    val participants = people.filterNot { it.organizer }
    val host = people.firstOrNull { it.organizer }
    val window = CheckInWindow.state(event, now)
    val windowOpen = window == CheckInWindowState.OPEN
    val validCode = code?.takeIf { showingCode && now < codeExpiresAt && windowOpen && checkInReady }
    val status = when {
        loading && !hasInformation -> "Loading check-in…"
        !hasInformation -> "Check-in unavailable"
        window == CheckInWindowState.EVENT_CANCELLED -> "Event cancelled"
        window == CheckInWindowState.EVENT_ENDED -> "Event ended"
        window == CheckInWindowState.NOT_STARTED -> "Check-in opens at ${attendanceTime(CheckInWindow.opensAt(event))}"
        window == CheckInWindowState.EXPIRED -> "Check-in closed"
        !checkInReady -> "Confirm your arrival"
        else -> "Check-in open"
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().testTag("cloud_organizer_desk")) {
            AppPageHeader("Check-in desk", onBack, actions = {
                Box {
                    IconButton({ menu = true }, modifier = Modifier.testTag("cloud_organizer_more")) {
                        Icon(Icons.Default.MoreVert, "More check-in options")
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Event reviews") }, { menu = false; onReviews() },
                            modifier = Modifier.testTag("cloud_tab_reviews"))
                        DropdownMenuItem({ Text("Refresh") }, { menu = false; onRefresh() }, enabled = !busy)
                        extraMenuItems { menu = false }
                    }
                }
            })
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 20.dp)
                .testTag("cloud_organizer_scroll")) {
                Spacer(Modifier.height(16.dp))
                Text(event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${displayTime(event.startAt)}–${attendanceTime(event.endAt)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).background(if (windowOpen && checkInReady && hasInformation) Color(0xFF26883F)
                        else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                    Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("cloud_organizer_status"))
                }
                // This slot is measured only from viewport and text scale, NEVER from visibility or code state.
                // Keeping it mounted also preserves the outer scroll offset when hiding/showing the QR.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val qrSize = (maxWidth * .64f).coerceIn(176.dp, 256.dp)
                    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                    Box(Modifier.fillMaxWidth().height(qrSize + 128.dp * fontScale).testTag("cloud_qr_slot"),
                        contentAlignment = Alignment.Center) {
                        if (validCode != null) {
                            val qr = remember(validCode) { BarcodeEncoder().createBitmap(CheckInQr.matrix(validCode)).asImageBitmap() }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(qr, "Server-issued check-in QR code", Modifier.size(qrSize).background(Color.White).testTag("cloud_qr"))
                                Text("Scan this code to check in", style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                                Text("Updates automatically · ${(codeExpiresAt - now).coerceAtLeast(0) / 1000}s",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onHideCode, modifier = Modifier.testTag("cloud_hide_code")) { Text("Hide code") }
                            }
                        } else {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val heading = when {
                                    loading && !hasInformation -> "Loading check-in…"
                                    !hasInformation -> "Could not load check-in"
                                    !windowOpen -> status
                                    !checkInReady -> "Confirm your arrival"
                                    showingCode -> "Getting a fresh code…"
                                    else -> "Code hidden"
                                }
                                if ((showingCode && windowOpen && checkInReady && hasInformation) || (loading && !hasInformation))
                                    CircularProgressIndicator(Modifier.size(28.dp))
                                else Icon(if (checkInReady && windowOpen) Icons.Default.Lock else Icons.Default.Info,
                                    null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(heading, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                                Text(when {
                                    !hasInformation -> "Reconnect and try again."
                                    !windowOpen -> "View the attendance list or check the rules below."
                                    !checkInReady -> "Confirm your arrival to record it and display the code."
                                    showingCode -> "Keep this page open while the code refreshes."
                                    else -> "Check-in stays open."
                                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center)
                                when {
                                    !hasInformation -> PageActionButton(onRefresh, enabled = !busy && !loading,
                                        modifier = Modifier.testTag("cloud_organizer_retry")) { Text("Retry") }
                                    windowOpen && !checkInReady -> PageActionButton(onConfirmArrival, enabled = !busy,
                                        modifier = Modifier.testTag("cloud_confirm_arrival")) { Text("Confirm arrival and show code") }
                                    windowOpen && showingCode -> TextButton(onHideCode, modifier = Modifier.testTag("cloud_hide_code")) { Text("Hide code") }
                                    windowOpen -> PageActionButton(onShowCode, enabled = !busy,
                                        modifier = Modifier.testTag("cloud_issue_code")) { Text("Show check-in code") }
                                }
                            }
                        }
                    }
                }
                AppDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Organizer · " + (host?.name ?: "You") + " · " + if (checkInReady) "Arrival confirmed" else "Arrival not confirmed",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("cloud_host_attendance"))
                AttendancePreview(participants, rosterLoaded, rosterLoading, rosterError, onAttendance)
                AppDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OrganizerDetailRow("Meeting point", "${event.city} · ${event.meetingLocation ?: event.location}",
                    Modifier.testTag("cloud_meeting_details"), Icons.Default.LocationOn) { sheet = "meeting" }
                OrganizerDetailRow("Check-in rules", null, Modifier.testTag("cloud_checkin_rules"), Icons.Default.Info) { sheet = "rules" }
                Spacer(Modifier.height(20.dp))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    when (sheet) {
        "meeting" -> OrganizerMeetingSheet(event, onClose = { sheet = null })
        "rules" -> OrganizerRulesSheet(event, onClose = { sheet = null })
    }
}

@Composable
private fun AttendancePreview(people: List<CloudAttendancePerson>, loaded: Boolean, loading: Boolean,
    error: String, onAttendance: () -> Unit) {
    val checked = people.filter { it.checkedIn }.sortedByDescending { it.checkedInAt }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("cloud_attendance_preview")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Attendance", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(if (loaded) "${checked.size} of ${people.size} checked in" else if (loading) "Loading…" else "Unavailable",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("cloud_attendance_preview_count"))
        }
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = (maxWidth - 24.dp - if (checked.size > 4) 20.dp else 0.dp) / 4
            val avatar = (cell - 8.dp).coerceIn(36.dp, 56.dp)
            val nameHeight = with(LocalDensity.current) { 24.sp.toDp() }
            val rowHeight = avatar + nameHeight + 8.dp
            if (checked.isEmpty()) Box(Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.CenterStart) {
                Text(if (loaded) "No one has checked in yet." else "Attendance will appear here.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else LazyRow(Modifier.fillMaxWidth().height(rowHeight).testTag("cloud_attendance_avatars"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(checked, key = { it.userId }) { person ->
                    Column(Modifier.width(cell).testTag("cloud_preview_${person.userId}"), horizontalAlignment = Alignment.CenterHorizontally) {
                        AttendanceAvatar(person, Modifier.size(avatar))
                        Spacer(Modifier.height(8.dp))
                        Text(person.name, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (error.isNotBlank()) "Could not refresh attendance" else if (loaded) "${people.size - checked.size} not checked in" else "",
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = if (error.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onAttendance, modifier = Modifier.testTag("cloud_tab_attendance")) { Text("View all ›") }
        }
    }
}

@Composable
private fun OrganizerDetailRow(title: String, subtitle: String?, modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppUi.Primary)
    }
}

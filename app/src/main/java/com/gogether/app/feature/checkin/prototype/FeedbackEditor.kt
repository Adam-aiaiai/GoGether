package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppDivider
import com.gogether.app.core.ui.AppUi
import com.gogether.app.core.ui.pageHeaderBackground
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.gogether.app.core.ui.AppCommentField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin
import kotlin.math.cos

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogether.app.core.ui.EventImage
import com.gogether.app.core.ui.PageActionButton
import com.gogether.app.core.ui.SquareActionDialog
import com.gogether.app.feature.checkin.FeedbackInput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class ReviewEventSummary(val title: String, val organizer: String, val startAt: Long,
    val imageUri: String? = null, val symbol: String = "")

/** Presentation only: both storage adapters retain their existing validation and persistence. */
@Composable
internal fun FeedbackEditor(scopeKey: String, summary: ReviewEventSummary, draft: FeedbackDraft,
    busy: Boolean, draftError: String, message: String, update: (FeedbackDraft) -> Unit,
    retryDraft: () -> Unit, submit: () -> Unit, back: () -> Unit, reviews: () -> Unit,
    modifier: Modifier = Modifier, commentTag: String = "feedback_comment", submitTag: String = "feedback_submit",
    extraActions: @Composable ColumnScope.() -> Unit = {}, photos: @Composable () -> Unit) {
    var details by rememberSaveable(scopeKey) { mutableStateOf(false) }
    var privacy by rememberSaveable(scopeKey) { mutableStateOf(false) }
    var menu by remember(scopeKey) { mutableStateOf(false) }
    val mainScroll = rememberLazyListState()
    val detailScroll = rememberLazyListState()
    val focus = LocalFocusManager.current
    fun leaveDetails() { details = false }
    fun openDetails() {
        focus.clearFocus()
        detailScroll.requestScrollToItem(0)
        details = true
    }
    fun changeDetail(change: (FeedbackDetails) -> FeedbackDetails) = update(draft.copy(details = change(draft.details)))
    BackHandler(enabled = details || privacy) { if (privacy) privacy = false else leaveDetails() }

    Column(modifier.fillMaxSize().imePadding().background(Color.White).testTag("feedback_editor")) {
        Row(Modifier.fillMaxWidth().height(AppUi.PageHeaderHeight).pageHeaderBackground()
            .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (details) leaveDetails() else back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(if (details) "Review details" else "Write a review", Modifier.weight(1f),
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("feedback_more")) {
                    Icon(Icons.Default.MoreVert, "More review options")
                }
                DropdownMenu(menu, { menu = false }) {
                    if (details) DropdownMenuItem(text = { Text("Clear optional ratings") }, enabled = !busy,
                        modifier = Modifier.testTag("feedback_clear_dimensions"), onClick = {
                            changeDetail { it.copy(organization = 0, venue = 0, atmosphere = 0) }; menu = false
                        })
                    DropdownMenuItem(text = { Text("View event reviews") }, modifier = Modifier.testTag("feedback_open_reviews"),
                        onClick = { menu = false; reviews() }, enabled = !busy)
                    extraActions()
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().testTag("history_detail"),
                state = if (details) detailScroll else mainScroll,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "summary") { ReviewSummary(summary) }
                if (details) {
                    item(key = "dimensions") {
                        Column(Modifier.testTag("feedback_ratings_section")) {
                            SectionLabel("Specific ratings", "Optional")
                            FeedbackDimension("Organization", "organization", draft.details.organization, busy) { score -> changeDetail { it.copy(organization = score) } }
                            AppDivider(color = ReviewDivider)
                            FeedbackDimension("Venue", "venue", draft.details.venue, busy) { score -> changeDetail { it.copy(venue = score) } }
                            AppDivider(color = ReviewDivider)
                            FeedbackDimension("Atmosphere", "atmosphere", draft.details.atmosphere, busy) { score -> changeDetail { it.copy(atmosphere = score) } }
                            AppDivider(color = ReviewDivider)
                        }
                    }
                    item(key = "positive") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FeedbackTags(false, draft.details.tags, busy) { id ->
                                changeDetail { it.copy(tags = if (id in it.tags) it.tags - id else it.tags + id) }
                            }
                            AppCommentField(draft.details.positiveComment,
                                { value -> changeDetail { it.copy(positiveComment = value.take(500)) } }, enabled = !busy,
                                placeholder = { Text("Anything else you enjoyed? (Optional)") },
                                minLines = 2, maxLines = 4, maxLength = 500,
                                modifier = Modifier.fillMaxWidth().testTag("feedback_positive_comment"))
                            AppDivider(color = ReviewDivider)
                        }
                    }
                    item(key = "improvement") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FeedbackTags(true, draft.details.tags, busy) { id ->
                                changeDetail { it.copy(tags = if (id in it.tags) it.tags - id else it.tags + id) }
                            }
                            AppCommentField(draft.details.improvementComment,
                                { value -> changeDetail { it.copy(improvementComment = value.take(500)) } }, enabled = !busy,
                                placeholder = { Text("How could it be better? (Optional)") },
                                minLines = 2, maxLines = 4, maxLength = 500,
                                modifier = Modifier.fillMaxWidth().testTag("feedback_improvement_comment"))
                        }
                    }
                } else {
                    item(key = "overall") {
                        SectionLabel("How was the activity?")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RatingStars("Overall rating", "rating", draft.rating, busy, Modifier.width(200.dp)) { update(draft.copy(rating = it)) }
                            Text(reviewRatingLabel(draft.rating), Modifier.padding(start = 8.dp).testTag("feedback_rating_label"),
                                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item { OptionalReviewRow("More details", "Optional", "feedback_open_details", !busy, ::openDetails) }
                    item(key = "comment") {
                        SectionLabel("Your experience")
                        AppCommentField(draft.comment, { update(draft.copy(comment = it)) }, enabled = !busy,
                            placeholder = { Text("What did you enjoy? What could be better?") },
                            minLines = 2, maxLines = 5, maxLength = 500,
                            modifier = Modifier.fillMaxWidth().testTag(commentTag))
                    }
                    item(key = "photos") { photos() }
                    item(key = "privacy") {
                        AppDivider(color = ReviewDivider)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Post anonymously", Modifier.weight(1f), fontSize = 16.sp)
                            Switch(draft.details.anonymous, { value -> changeDetail { it.copy(anonymous = value) } }, enabled = !busy,
                                modifier = Modifier.testTag("feedback_anonymous"))
                        }
                        OptionalReviewRow("Visible to", if (draft.details.visibility == FeedbackVisibility.PUBLIC) "Public" else "Organizer only",
                            "feedback_visibility", !busy, { privacy = true }, shield = true)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            AppDivider(color = ReviewDivider)
            if (message.isNotBlank()) Text(message, Modifier.padding(top = 6.dp).testTag("feedback_message"),
                color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            if (draftError.isNotBlank()) TextButton(onClick = retryDraft, enabled = !busy) { Text("Draft not saved · Retry") }
            if (details) {
                Text("Your choices are saved to this draft.", Modifier.padding(vertical = 12.dp),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PageActionButton(onClick = ::leaveDetails,
                    modifier = Modifier.testTag("feedback_details_done")) {
                    Text("Done")
                }
            } else Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (draftError.isBlank()) "Draft saved" else "Draft not saved", Modifier.weight(1f).testTag("feedback_draft_status"),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    PageActionButton(onClick = submit,
                        enabled = !busy && draftError.isBlank() && FeedbackInput.valid(draft.rating, draft.comment),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag(submitTag)) {
                        Text(if (busy) "Saving…" else "Submit review", fontSize = 14.sp)
                    }
                }
            }
        }
    }
    if (privacy) SquareActionDialog(title = "Who can see this?", onDismiss = { privacy = false },
        modifier = Modifier.testTag("feedback_visibility_dialog"),
        actions = {
            PageActionButton(onClick = { privacy = false }, modifier = Modifier.testTag("feedback_visibility_done")) {
                Text("Done")
            }
        }) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FeedbackVisibility.entries.forEach { option ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(draft.details.visibility == option, enabled = !busy,
                        role = Role.RadioButton, onClick = { changeDetail { it.copy(visibility = option) } })
                        .testTag("feedback_visibility_${option.name}"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(draft.details.visibility == option, null, enabled = !busy,
                            modifier = Modifier.padding(end = 12.dp),
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant))
                        Text(if (option == FeedbackVisibility.PUBLIC) "Public review" else "Organizer only",
                            fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
                Text("Anonymous reviews hide your name and avatar. Personal details in text and photos remain visible.",
                    Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
}

private val ReviewDivider = Color(0xFFE6E6E9)

@Composable
private fun ReviewSummary(summary: ReviewEventSummary) {
    Column {
        Row(Modifier.padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2F3F5)), contentAlignment = Alignment.Center) {
                if (summary.symbol.isNotBlank()) Text(summary.symbol, fontSize = 28.sp)
                EventImage(summary.imageUri.orEmpty(), summary.title, Modifier.fillMaxSize(), shape = RoundedCornerShape(8.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(summary.title, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${summary.organizer} / ${SimpleDateFormat("d MMM, HH:mm", Locale.ENGLISH).format(Date(summary.startAt))}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SectionLabel(label: String, trailing: String = "") {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (trailing.isNotEmpty()) Text(trailing, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OptionalReviewRow(label: String, value: String, tag: String, enabled: Boolean, click: () -> Unit, shield: Boolean = false) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF5F5F7))
        .clickable(enabled = enabled, onClick = click).testTag(tag).heightIn(min = 40.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (shield) Canvas(Modifier.size(20.dp)) {
            val outline = Path().apply {
                moveTo(size.width*.5f, size.height*.08f); lineTo(size.width*.85f,size.height*.23f)
                lineTo(size.width*.82f,size.height*.60f); quadraticTo(size.width*.75f,size.height*.8f,size.width*.5f,size.height*.92f)
                quadraticTo(size.width*.25f,size.height*.8f,size.width*.18f,size.height*.60f)
                lineTo(size.width*.15f,size.height*.23f); close()
            }
            drawPath(outline, Color(0xFF33353A), style=Stroke(1.5.dp.toPx()))
        }
        Text(label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = if (shield) FontWeight.Normal else FontWeight.Medium)
        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
    }
}

internal fun reviewRatingLabel(rating: Int) = when (rating) { 1 -> "Poor"; 2 -> "Not great"; 3 -> "Okay"; 4 -> "Good"; 5 -> "Excellent"; else -> "Tap to rate" }

@Composable
internal fun RatingStars(label: String, key: String, rating: Int, busy: Boolean, modifier: Modifier = Modifier, choose: (Int) -> Unit) {
    Row(modifier.selectableGroup()) {
        (1..5).forEach { score ->
            Box(Modifier.weight(1f).height(44.dp).selectable(rating == score, enabled = !busy,
                role = Role.RadioButton, onClick = { choose(score) }).semantics {
                    contentDescription = if (key == "rating") "$score out of 5, ${reviewRatingLabel(score)}" else "$label: $score out of 5"
                }.testTag("feedback_${key}_$score"), contentAlignment = Alignment.Center) {
                val tint = if (score <= rating) MaterialTheme.colorScheme.primary else Color(0xFF96989E)
                Canvas(Modifier.size(if (key == "rating") 28.dp else 22.dp)) {
                    val star = Path().apply {
                        repeat(10) { point ->
                            val angle = Math.PI * point / 5 - Math.PI / 2
                            val radius = size.minDimension * if (point % 2 == 0) .46f else .21f
                            val x = size.width / 2 + cos(angle).toFloat() * radius
                            val y = size.height / 2 + sin(angle).toFloat() * radius
                            if (point == 0) moveTo(x,y) else lineTo(x,y)
                        }
                        close()
                    }
                    if (score <= rating) drawPath(star, tint) else drawPath(star, tint, style=Stroke(1.4.dp.toPx()))
                }
            }
        }
    }
}

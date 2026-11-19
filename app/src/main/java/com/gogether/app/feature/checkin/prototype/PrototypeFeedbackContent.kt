package com.gogether.app.feature.checkin.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gogether.app.feature.checkin.FeedbackInput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun reviewTime(time: Long) = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(time))
private fun ratingLabel(rating: Int) = when (rating) {
    1 -> "很不满意"
    2 -> "不太满意"
    3 -> "一般"
    4 -> "满意"
    5 -> "非常满意"
    else -> "点亮星星，为这次活动打分"
}

/** A compact activity summary and scrollable form, with the submit action above the keyboard. */
@Composable
internal fun HistoryDetail(actor: String, entry: PrototypeHistoryEntry, now: Long, busy: Boolean, message: String,
    submit: (String, Int, String, FeedbackDetails) -> Unit, modifier: Modifier, openCheckIn: (PrototypeSession) -> Unit,
    openAttendance: (PrototypeSession) -> Unit, openReviews: (PrototypeSession) -> Unit, pendingCheckIn: Boolean = false) {
    val context = LocalContext.current.applicationContext
    val activity = LocalCheckInActivities.current.infoOrNull(entry.session)
    val drafts = remember(context) { FeedbackDraftStore(context) }
    val initial = remember(actor, entry.session.id) { runCatching { drafts.load(actor, entry.session.id) } }
    var draft by remember(actor, entry.session.id) { mutableStateOf(initial.getOrDefault(FeedbackDraft())) }
    var draftError by remember(actor, entry.session.id) { mutableStateOf(if (initial.isFailure) "草稿读取失败，请退出后重试" else "") }
    fun updateDraft(change: (FeedbackDraft) -> FeedbackDraft) {
        draft = change(draft)
        draftError = runCatching { drafts.save(actor, entry.session.id, draft) }
            .exceptionOrNull()?.message.orEmpty()
    }
    val rating = draft.rating
    val comment = draft.comment
    val (organization, venue, atmosphere, tags, anonymous, visibility, photos) = draft.details
    val photoActions = rememberFeedbackPhotoActions(actor, entry.session.id, photos, busy) { ids ->
        updateDraft { it.copy(details = it.details.copy(photos = ids.toList())) }
    }
    val blocked = busy || photoActions.importing
    fun toggleTag(id: String) = updateDraft { current ->
        val selected = current.details.tags
        current.copy(details = current.details.copy(tags = if (id in selected) selected - id else selected + id))
    }
    LaunchedEffect(actor, entry.session.id, entry.feedback) {
        withContext(Dispatchers.IO) {
            FeedbackPhotoStore(context).cleanUnreferenced(actor, entry.session.id)
        }
    }
    val canReview = entry.canReview(now)
    Column(modifier.fillMaxWidth()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("history_detail"),
            verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CheckInStyle.shape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DateRange, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(activity?.title ?: "活动资料不可用", style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${activity?.organizerName ?: "未知组织者"} 组织", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("${reviewTime(entry.session.startAt)} — ${SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(entry.session.endAt))}",
                    style = MaterialTheme.typography.bodyMedium)
                Text(activity?.meetingName ?: "集合点待确认", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                if (pendingCheckIn) TextButton(onClick = { openCheckIn(entry.session) }, enabled = !blocked,
                    shape = CheckInStyle.shape, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.testTag("history_resolve_checkin")) { Text("签到结果待确认，去核对 ›") }
                else Text(entry.receipt?.let { "已签到  ${reviewTime(it.checkedInAt)}" } ?: "尚未签到",
                    Modifier.testTag("history_checkin"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (activity?.organizerId == actor) item {
                OutlinedButton(onClick = { openAttendance(entry.session) }, shape = CheckInStyle.shape,
                    modifier = Modifier.testTag("history_open_attendance")) { Text("查看出席记录") }
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            when {
                entry.feedback != null -> {
                    item {
                        Text("我的评价 ${entry.feedback.rating} / 5", Modifier.testTag("feedback_saved"),
                            style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..5).forEach { score ->
                                Icon(Icons.Default.Star,
                                    null, Modifier.size(28.dp), tint = if (score <= entry.feedback.rating)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            }
                        }
                        Text(ratingLabel(entry.feedback.rating), color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    item {
                        SavedFeedbackDetails(entry.feedback.details)
                        Spacer(Modifier.height(12.dp))
                        FeedbackPrivacySummary(entry.feedback.details)
                    }
                    if (entry.feedback.details.photos.isNotEmpty()) item {
                        FeedbackPhotoStrip(entry.feedback.details.photos, actor, entry.session.id)
                    }
                    item {
                        Text("活动感受", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(entry.feedback.comment.ifBlank { "未填写评论" }, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(16.dp))
                        Text("提交于 ${reviewTime(entry.feedback.createdAt)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                entry.cancelled -> item { Text("活动已取消，不能评价。") }
                now < entry.session.endAt -> item {
                    Text("活动结束后可评价，无需重新扫码。")
                    Text("结束时间：${reviewTime(entry.session.endAt)}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { openCheckIn(entry.session) }, enabled = !blocked,
                        shape = CheckInStyle.shape, modifier = Modifier.testTag("history_open_checkin")) { Text("进入本场签到") }
                }
                else -> {
                    item {
                        Text("这次活动体验如何？", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..5).forEach { score ->
                                Box(Modifier.weight(1f).heightIn(min = 56.dp).clip(CheckInStyle.shape)
                                    .selectable(selected = rating == score, enabled = !blocked, role = Role.RadioButton,
                                        onClick = { updateDraft { it.copy(rating = score) } })
                                    .semantics { contentDescription = "$score 分，${ratingLabel(score)}" }
                                    .testTag("feedback_rating_$score"), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Star, null,
                                        Modifier.size(36.dp), tint = if (score <= rating) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                        Text(if (rating == 0) ratingLabel(0) else "$rating 分  ${ratingLabel(rating)}",
                            Modifier.padding(top = 4.dp).testTag("feedback_rating_label"), style = MaterialTheme.typography.bodyMedium,
                            color = if (rating == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                    }
                    item {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("分项体验（选填）", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { updateDraft { it.copy(details = it.details.copy(organization = 0, venue = 0, atmosphere = 0)) } },
                                enabled = !blocked && (organization + venue + atmosphere > 0),
                                modifier = Modifier.testTag("feedback_clear_dimensions")) { Text("清空") }
                        }
                        Text("只评你体验过的部分", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { FeedbackDimension("组织安排", "organization", organization, blocked) { score -> updateDraft { it.copy(details = it.details.copy(organization = score)) } } }
                    item { FeedbackDimension("场地体验", "venue", venue, blocked) { score -> updateDraft { it.copy(details = it.details.copy(venue = score)) } } }
                    item { FeedbackDimension("活动氛围", "atmosphere", atmosphere, blocked) { score -> updateDraft { it.copy(details = it.details.copy(atmosphere = score)) } } }
                    item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                    item { FeedbackTags(false, tags, blocked, ::toggleTag) }
                    item { FeedbackTags(true, tags, blocked, ::toggleTag) }
                    item {
                        Text("聊聊你的活动感受", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        TextField(comment, { text -> updateDraft { it.copy(comment = text) } }, shape = CheckInStyle.shape, enabled = !blocked,
                            label = { Text("评论（选填）") },
                            placeholder = { Text("有哪些喜欢的地方，或希望下次改进的安排？") },
                            minLines = 3, maxLines = 6, isError = comment.length > 500,
                            supportingText = { Text(if (comment.length > 500) "已超过 500 字，请精简后提交" else "${comment.length} / 500") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            modifier = Modifier.fillMaxWidth().testTag("feedback_comment"))
                    }
                    item {
                        FeedbackPhotoStrip(photos, actor, entry.session.id, blocked, photoActions.add, photoActions.remove)
                        if (photoActions.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (photoActions.error.isNotBlank()) Text(photoActions.error, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("feedback_photo_error"))
                        Text("图片目前保存在此设备。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { FeedbackPrivacyFields(anonymous, visibility, blocked, { value -> updateDraft { it.copy(details = it.details.copy(anonymous = value)) } }, { value -> updateDraft { it.copy(details = it.details.copy(visibility = value)) } }) }
                }
            }
            item {
                TextButton(onClick = { openReviews(entry.session) }, enabled = !blocked,
                    modifier = Modifier.testTag("feedback_open_reviews")) { Text("查看活动评价 >") }
            }
        }
        if (canReview) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (message.isNotBlank()) {
                    Text(message, Modifier.testTag("feedback_message"), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(draftError.ifBlank { "草稿自动保存，每场限一次" },
                    modifier = Modifier.testTag("feedback_draft_status").clickable(enabled = draftError.isNotBlank() && !blocked) { updateDraft { it } },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (draftError.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                Button(onClick = { submit(entry.session.id, rating, comment,
                    FeedbackDetails(organization, venue, atmosphere, tags.toList(), anonymous, visibility, photos.toList())) }, shape = CheckInStyle.shape,
                    enabled = !blocked && FeedbackInput.valid(rating, comment),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("feedback_submit")) {
                    Text(if (photoActions.importing) "正在处理图片…" else if (busy) "正在保存…" else "提交评价")
                }
            }
        }
    }
}

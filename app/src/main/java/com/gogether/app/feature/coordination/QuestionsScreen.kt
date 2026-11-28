package com.gogether.app.feature.coordination

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.profile.ParticipantAvatar
import kotlinx.coroutines.launch

@Composable
fun QuestionsScreen(event: Event, state: AppSnapshot, now: Long, busy: Boolean,
                    focusedQuestionId: String?,
                    ask: (String, () -> Unit) -> Unit,
                    answer: (String, String, () -> Unit) -> Unit) {
    var draft by rememberSaveable(event.id, state.currentUserId) { mutableStateOf("") }
    var editingId by rememberSaveable(event.id, state.currentUserId) { mutableStateOf<String?>(null) }
    var answerDraft by rememberSaveable(event.id, state.currentUserId) { mutableStateOf("") }
    val pager = key(event.id, state.currentUserId) { rememberPagerState(pageCount = { 2 }) }
    val scope = rememberCoroutineScope()
    val organizer = state.currentUserId == event.organizerId
    val open = event.statusAt(now) == EventStatus.OPEN
    val all = state.questions.filter { it.eventId == event.id }
        .sortedWith(compareByDescending<EventQuestion> { it.id == focusedQuestionId }.thenByDescending { it.createdAt })
    HorizontalPager(pager, Modifier.fillMaxSize().testTag("questions_pager"), verticalAlignment = Alignment.Top) { page ->
        Page {
            if (!LocalPageHeaderProvided.current) AppPageHeader("Questions & answers", horizontalInset = 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.title, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Questions and replies are visible to everyone. You can ask before joining.",
                    fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (organizer && open) Text("Reply here. Use announcements for general updates.",
                    fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!open) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Questions closed", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("This event has ended or been cancelled. Existing questions and replies remain available.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!organizer) {
                AppCommentField(draft, { draft = it }, enabled = !busy,
                    label = { Text("Your question") }, minLines = 2,
                    isError = draft.length > 100,
                    modifier = Modifier.fillMaxWidth().testTag("question_input"))
                PageActionButton(onClick = { ask(draft) { draft = ""; scope.launch { pager.scrollToPage(0) } } },
                    enabled = !busy && draft.trim().length in 1..100,
                    modifier = Modifier.testTag("post_question")) {
                    Text(if (busy) "Sending…" else "Ask organizer")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                AppTextTab("All (${all.size})", pager.currentPage == 0,
                    { scope.launch { pager.animateScrollToPage(0) } }, Modifier.testTag("questions_all"))
                AppTextTab("Unanswered (${all.count { it.answer == null }})", pager.currentPage == 1,
                    { scope.launch { pager.animateScrollToPage(1) } }, Modifier.testTag("questions_unanswered"))
            }
            val shown = all.filter { page == 0 || it.answer == null }
            if (shown.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (page == 1) "No unanswered questions" else "No questions yet",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(if (organizer) "Questions from people interested in this event will appear here." else "Ask the organizer about this event.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
                shown.forEach { question ->
                    key(question.id) {
                        Column(Modifier.fillMaxWidth().testTag("question_thread_${question.id}")) {
                            QuestionThread(question, event, state, organizer && open && editingId != question.id, busy,
                                onReply = { editingId = question.id; answerDraft = question.answer.orEmpty() })
                            if (organizer && open && editingId == question.id) {
                                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AppCommentField(answerDraft, { answerDraft = it }, enabled = !busy,
                                        label = { Text("Your answer") }, minLines = 2,
                                        isError = answerDraft.length > 100,
                                        modifier = Modifier.fillMaxWidth().testTag("answer_input"))
                                    PageActionButton(onClick = { answer(question.id, answerDraft) { editingId = null; answerDraft = "" } },
                                        enabled = !busy && answerDraft.trim().length in 1..100 && answerDraft.trim() != question.answer,
                                        modifier = Modifier.testTag("send_answer_${question.id}")) {
                                        Text(if (busy) "Saving…" else "Save answer")
                                    }
                                    OutlinedPageActionButton(onClick = { editingId = null; answerDraft = "" }, enabled = !busy,
                                        modifier = Modifier.testTag("cancel_answer_${question.id}")) { Text("Cancel reply") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionThread(question: EventQuestion, event: Event, state: AppSnapshot,
    canReply: Boolean, busy: Boolean, onReply: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val author = state.users.find { it.id == question.authorId }
        ParticipantAvatar(state.userName(question.authorId), author?.avatarUri.orEmpty(), Modifier.size(38.dp))
        Column(Modifier.weight(1f)) {
            Column(Modifier.testTag("question_${question.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MessageHeading(state.userName(question.authorId), question.createdAt)
                Text(question.content, fontSize = 13.sp, lineHeight = 19.sp)
            }
            if (question.answer != null) {
                val responderId = question.answeredBy ?: event.organizerId
                val responder = state.users.find { it.id == responderId }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp).testTag("answer_${question.id}"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParticipantAvatar(state.userName(responderId), responder?.avatarUri.orEmpty(), Modifier.size(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${state.userName(responderId)} · Organizer", fontSize = 11.sp, lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(question.answer, fontSize = 12.sp, lineHeight = 18.sp)
                        if (canReply) ReplyAction("Edit answer", question.id, busy, onReply)
                    }
                }
            } else if (canReply) ReplyAction("Reply", question.id, busy, onReply)
            else Text("Awaiting organizer reply", Modifier.padding(top = 6.dp), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReplyAction(label: String, questionId: String, busy: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
        .clickable(enabled = !busy, role = Role.Button, onClick = onClick).testTag("reply_$questionId"),
        contentAlignment = Alignment.CenterStart) {
        Text(label, fontSize = 12.sp, lineHeight = 16.sp,
            color = if (busy) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f) else AppUi.Primary)
    }
}

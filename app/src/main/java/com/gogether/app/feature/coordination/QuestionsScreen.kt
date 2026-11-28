package com.gogether.app.feature.coordination

import com.gogether.app.core.ui.AppCommentField
import com.gogether.app.core.ui.AppFilterChip
import com.gogether.app.core.ui.PageActionButton
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*

@Composable
fun QuestionsScreen(event: Event, state: AppSnapshot, now: Long, busy: Boolean,
                    focusedQuestionId: String?,
                    ask: (String, () -> Unit) -> Unit,
                    answer: (String, String, () -> Unit) -> Unit) {
    var draft by rememberSaveable(event.id, state.currentUserId) { mutableStateOf("") }
    var unanswered by rememberSaveable(event.id) { mutableStateOf(false) }
    var editingId by rememberSaveable(event.id, state.currentUserId) { mutableStateOf<String?>(null) }
    var answerDraft by rememberSaveable(event.id, state.currentUserId) { mutableStateOf("") }
    val organizer = state.currentUserId == event.organizerId
    val open = event.statusAt(now) == EventStatus.OPEN
    val all = state.questions.filter { it.eventId == event.id }
        .sortedWith(compareByDescending<EventQuestion> { it.id == focusedQuestionId }.thenByDescending { it.createdAt })
    Page {
        if (!LocalPageHeaderProvided.current) SectionTitle("Questions & answers", event.title)
        else Text(event.title, style = MaterialTheme.typography.titleMedium)
        Text("Questions and organizer replies are visible to everyone viewing this event. You can ask before joining.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!open) InfoCard("Questions closed", "This event has ended or been cancelled. Existing questions and replies remain available.")
        else if (!organizer) {
            AppCommentField(draft, { draft = it }, enabled = !busy,
                label = { Text("Your question") }, minLines = 2,
                isError = draft.length > 100,
                modifier = Modifier.fillMaxWidth().testTag("question_input"))
            PageActionButton(onClick = { ask(draft) { draft = ""; unanswered = false } },
                enabled = !busy && draft.trim().length in 1..100,
                modifier = Modifier.fillMaxWidth().testTag("post_question")) {
                Text(if (busy) "Sending…" else "Ask organizer")
            }
        } else Text("Reply to questions below. Use announcements to share general updates with participants.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFilterChip(!unanswered, { unanswered = false }, label = { Text("All (${all.size})") }, modifier = Modifier.testTag("questions_all"))
            AppFilterChip(unanswered, { unanswered = true }, label = { Text("Unanswered (${all.count { it.answer == null }})") }, modifier = Modifier.testTag("questions_unanswered"))
        }
        val shown = all.filter { !unanswered || it.answer == null }
        if (shown.isEmpty()) InfoCard(if (unanswered) "No unanswered questions" else "No questions yet",
            if (organizer) "Questions from people interested in this event will appear here." else "Ask the organizer about this event.")
        shown.forEach { question ->
            key(question.id) {
                MessageCard(state.userName(question.authorId), question.content, displayTime(question.createdAt),
                    Modifier.testTag("question_${question.id}"))
                if (question.answer == null) Text("Awaiting organizer reply")
                else MessageCard("Organizer reply", question.answer, question.answeredAt?.let(::displayTime).orEmpty(),
                    Modifier.testTag("answer_${question.id}"))
                if (organizer && open) {
                    if (editingId == question.id) {
                        AppCommentField(answerDraft, { answerDraft = it }, enabled = !busy,
                            label = { Text("Your answer") }, minLines = 2,
                            isError = answerDraft.length > 100,
                            modifier = Modifier.fillMaxWidth().testTag("answer_input"))
                        PageActionButton(onClick = { answer(question.id, answerDraft) { editingId = null; answerDraft = "" } },
                            enabled = !busy && answerDraft.trim().length in 1..100 && answerDraft.trim() != question.answer,
                            modifier = Modifier.testTag("send_answer_${question.id}")) {
                            Text(if (busy) "Saving…" else "Save answer")
                        }
                        OutlinedPageActionButton(
                            onClick = { editingId = null; answerDraft = "" }, enabled = !busy) { Text("Cancel reply") }
                    } else TextButton(onClick = { editingId = question.id; answerDraft = question.answer.orEmpty() }, enabled = !busy,
                        modifier = Modifier.testTag("reply_${question.id}")) { Text(if (question.answer == null) "Reply" else "Edit answer") }
                }
            }
        }
    }
}

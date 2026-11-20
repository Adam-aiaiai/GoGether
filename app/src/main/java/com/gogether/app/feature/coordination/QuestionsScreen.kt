package com.gogether.app.feature.coordination

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
        SectionTitle("Questions & answers", event.title)
        Text("Questions and organizer replies are visible to everyone viewing this event. You can ask before joining.")
        if (!open) InfoCard("Questions closed", "This event has ended or been cancelled. Existing questions and replies remain available.")
        else if (!organizer) {
            OutlinedTextField(draft, { draft = it }, enabled = !busy,
                label = { Text("Your question") }, minLines = 2,
                supportingText = { Text("${draft.length}/500 characters") }, isError = draft.trim().length > 500,
                modifier = Modifier.fillMaxWidth().testTag("question_input"))
            Button(onClick = { ask(draft) { draft = ""; unanswered = false } },
                enabled = !busy && draft.trim().length in 1..500,
                modifier = Modifier.fillMaxWidth().testTag("post_question")) {
                Text(if (busy) "Sending…" else "Ask organizer")
            }
        } else Text("Reply to questions below. Use announcements to share general updates with participants.")
        Row {
            FilterChip(!unanswered, { unanswered = false }, label = { Text("All (${all.size})") }, modifier = Modifier.testTag("questions_all"))
            FilterChip(unanswered, { unanswered = true }, label = { Text("Unanswered (${all.count { it.answer == null }})") }, modifier = Modifier.testTag("questions_unanswered"))
        }
        val shown = all.filter { !unanswered || it.answer == null }
        if (shown.isEmpty()) InfoCard(if (unanswered) "No unanswered questions" else "No questions yet",
            if (organizer) "Questions from people interested in this event will appear here." else "Ask the organizer about this event.")
        shown.forEach { question ->
            key(question.id) {
                InfoCard("${state.userName(question.authorId)} · ${displayTime(question.createdAt)}",
                    question.content, Modifier.testTag("question_${question.id}"))
                if (question.answer == null) Text("Awaiting organizer reply")
                else InfoCard("Organizer reply · ${question.answeredAt?.let(::displayTime).orEmpty()}",
                    question.answer, Modifier.testTag("answer_${question.id}"))
                if (organizer && open) {
                    if (editingId == question.id) {
                        OutlinedTextField(answerDraft, { answerDraft = it }, enabled = !busy,
                            label = { Text("Your answer") }, minLines = 2,
                            supportingText = { Text("${answerDraft.length}/1,000 characters") },
                            isError = answerDraft.trim().length > 1000,
                            modifier = Modifier.fillMaxWidth().testTag("answer_input"))
                        Button(onClick = { answer(question.id, answerDraft) { editingId = null; answerDraft = "" } },
                            enabled = !busy && answerDraft.trim().length in 1..1000 && answerDraft.trim() != question.answer,
                            modifier = Modifier.testTag("send_answer_${question.id}")) {
                            Text(if (busy) "Saving…" else "Save answer")
                        }
                        TextButton(onClick = { editingId = null; answerDraft = "" }, enabled = !busy) { Text("Cancel reply") }
                    } else TextButton(onClick = { editingId = question.id; answerDraft = question.answer.orEmpty() }, enabled = !busy,
                        modifier = Modifier.testTag("reply_${question.id}")) { Text(if (question.answer == null) "Reply" else "Edit answer") }
                }
            }
        }
    }
}

package com.gogether.app.feature.coordination

import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.ui.*
import com.gogether.app.core.model.MeetingUpdate

class CoordinationViewModel(repository: GogetherRepository,
    private val markRead: suspend (String) -> Result<Unit> = repository::markRead,
    private val markAllRead: suspend () -> Result<Unit> = repository::markAllRead) : RepositoryViewModel(repository) {
    fun cancel(id: String) = execute({ repository.cancelEvent(id) }) { UiMessage("Event cancelled. Participants have been notified in the app.") }
    fun announce(id: String, text: String, saved: () -> Unit = {}) = execute({ repository.postAnnouncement(id, text) }) { saved(); UiMessage("Announcement added to participant inboxes.") }
    fun meeting(id: String, update: MeetingUpdate, saved: () -> Unit = {}) = execute({ repository.updateMeeting(id, update) }) { saved(); UiMessage("Meeting information saved. Participants notified.") }
    fun ask(id: String, text: String, saved: () -> Unit) = execute({ repository.askQuestion(id, text) }) { saved(); UiMessage("Question posted. The organizer has been notified.") }
    fun answer(id: String, text: String, saved: () -> Unit) = execute({ repository.answerQuestion(id, text) }) { saved(); UiMessage("Answer saved. The person who asked has been notified.") }
    fun readAll() = execute({ markAllRead() }) { UiMessage("All your notifications are marked as read.") }
    fun read(id: String) = execute({ markRead(id) }) { UiMessage("Marked as read.") }
}

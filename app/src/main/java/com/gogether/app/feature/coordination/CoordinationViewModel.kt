package com.gogether.app.feature.coordination

import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.ui.*

class CoordinationViewModel(repository: GogetherRepository) : RepositoryViewModel(repository) {
    fun cancel(id: String) = execute({ repository.cancelEvent(id) }) { UiMessage("Event cancelled. Participants have been notified in the app.") }
    fun openCheckIn(id: String, open: Boolean) = execute({ repository.setDemoCheckInOpen(id, open) }) {
        UiMessage(if (open) "Demo check-in is open." else "Demo check-in is closed.")
    }
    fun announce(id: String, text: String) = execute({ repository.postAnnouncement(id, text) }) { UiMessage("Announcement added to participant inboxes.") }
    fun read(id: String) = execute({ repository.markRead(id) }) { UiMessage("Marked as read.") }
}

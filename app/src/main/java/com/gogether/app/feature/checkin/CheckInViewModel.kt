package com.gogether.app.feature.checkin

import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.ui.*

class CheckInViewModel(repository: GogetherRepository) : RepositoryViewModel(repository) {
    fun checkIn(id: String) = execute({ repository.checkIn(id) }) { UiMessage("Demo check-in recorded.") }
    fun feedback(id: String, rating: Int, comment: String) = execute({ repository.submitFeedback(id, rating, comment) }) {
        UiMessage("Thanks! Your feedback is saved for this demo session.")
    }
}

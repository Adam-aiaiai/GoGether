package com.gogether.app.feature.registration

import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.model.RegistrationStatus
import com.gogether.app.core.ui.*

class RegistrationViewModel(repository: GogetherRepository) : RepositoryViewModel(repository) {
    fun join(id: String) = execute({ repository.join(id) }) {
        UiMessage(if (it.status == RegistrationStatus.CONFIRMED) "You're in! Registration confirmed." else "Added to the waitlist.")
    }
    fun cancel(id: String) = execute({ repository.cancelRegistration(id) }) { UiMessage("Registration cancelled.") }
}

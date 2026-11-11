package com.gogether.app.feature.auth

import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.model.Category
import com.gogether.app.core.ui.RepositoryViewModel
import com.gogether.app.core.ui.UiMessage

class AuthViewModel(repository: GogetherRepository) : RepositoryViewModel(repository) {
    fun login(id: String) = execute({ repository.login(id) }) { UiMessage("Demo user selected") }
    fun register(name: String, interests: Set<Category>) = execute({ repository.register(name, interests) }) {
        UiMessage("Welcome, ${it.displayName}")
    }
    fun logout() = execute({ repository.logout() }) { UiMessage("Choose another Demo user") }
}

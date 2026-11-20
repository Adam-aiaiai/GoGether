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
    fun restore() = execute({ repository.restoreAccountSession() }) { UiMessage("Account session checked.") }
    fun signIn(email: String, password: String) = execute({ repository.signIn(email, password) }) { UiMessage("Signed in.") }
    fun signUp(email: String, password: String, name: String, interests: Set<Category>) =
        execute({ repository.signUp(email, password, name, interests) }) { UiMessage("Welcome, ${it.displayName}") }
    fun logout() = execute({ repository.logout() }) { UiMessage("You have logged out.") }
}

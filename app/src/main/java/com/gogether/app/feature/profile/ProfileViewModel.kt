package com.gogether.app.feature.profile

import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.model.Category
import com.gogether.app.core.ui.RepositoryViewModel
import com.gogether.app.core.ui.UiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class ProfileViewModel(repository: GogetherRepository) : RepositoryViewModel(repository) {
    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved = savedChannel.receiveAsFlow()
    fun save(name: String, interests: Set<Category>) = execute({ repository.updateProfile(name, interests) }) {
        savedChannel.trySend(Unit)
        UiMessage("Profile saved.")
    }
}

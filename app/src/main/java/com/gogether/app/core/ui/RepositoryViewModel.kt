package com.gogether.app.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gogether.app.core.data.GogetherRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiMessage(val text: String, val eventId: String? = null)

open class RepositoryViewModel(protected val repository: GogetherRepository) : ViewModel() {
    val state = repository.state
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()

    protected fun <T> execute(
        action: suspend () -> Result<T>,
        message: (T) -> UiMessage,
    ) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try {
                action().fold(
                    onSuccess = { messageChannel.send(message(it)) },
                    onFailure = { messageChannel.send(UiMessage(it.message ?: "Please try again.")) },
                )
            } finally { mutableBusy.value = false }
        }
    }

    companion object {
        fun <T : ViewModel> factory(create: () -> T): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
            }
    }
}

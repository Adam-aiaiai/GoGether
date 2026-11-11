package com.gogether.app.core.ui

import androidx.lifecycle.ViewModel
import com.gogether.app.core.data.AppContainer
import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.model.*
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GogetherViewModel(private val repository: GogetherRepository = AppContainer.repository) : ViewModel() {
    val users = repository.users
    val events = repository.events
    val registrations = repository.registrations
    val checkIns = repository.checkIns
    val currentUser = repository.currentUser
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun login(id: String) = repository.login(id)
    fun logout() = repository.logout()
    fun clearMessage() { _message.value = null }

    fun createEvent(title: String, category: String, description: String, startAt: Instant, location: String, capacity: Int, deadline: Instant, notes: String): Event? =
        repository.createEvent(EventDraft(title, category, description, startAt, location, capacity, deadline, notes))
            .onFailure { _message.value = it.message }.getOrNull()

    fun join(eventId: String) {
        _message.value = when (val outcome = repository.joinEvent(eventId)) {
            is RegistrationOutcome.Joined -> "Registration confirmed"
            is RegistrationOutcome.Waitlisted -> "Event is full — added to waitlist"
            is RegistrationOutcome.Rejected -> outcome.reason
        }
    }

    fun cancelRegistration(eventId: String) {
        repository.cancelRegistration(eventId).onSuccess { _message.value = "Registration cancelled" }.onFailure { _message.value = it.message }
    }

    fun cancelEvent(eventId: String) {
        repository.cancelEvent(eventId).onSuccess { _message.value = "Event cancelled" }.onFailure { _message.value = it.message }
    }

    fun checkIn(eventId: String) {
        repository.checkIn(eventId).onSuccess { _message.value = "Demo check-in recorded" }.onFailure { _message.value = it.message }
    }

    fun counts(eventId: String): EventCounts {
        val rows = registrations.value.filter { it.eventId == eventId }
        return EventCounts(rows.count { it.status == RegistrationStatus.CONFIRMED }, rows.count { it.status == RegistrationStatus.WAITLISTED })
    }

    fun registrationFor(eventId: String): Registration? = currentUser.value?.let { user ->
        registrations.value.lastOrNull { it.eventId == eventId && it.userId == user.id && it.status in setOf(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED) }
    }
}

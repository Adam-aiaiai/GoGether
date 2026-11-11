package com.gogether.app.core.data

import com.gogether.app.core.model.*
import kotlinx.coroutines.flow.StateFlow

interface GogetherRepository {
    val users: StateFlow<List<User>>
    val events: StateFlow<List<Event>>
    val registrations: StateFlow<List<Registration>>
    val checkIns: StateFlow<List<CheckIn>>
    val currentUser: StateFlow<User?>

    fun login(userId: String)
    fun logout()
    fun createEvent(draft: EventDraft): Result<Event>
    fun joinEvent(eventId: String): RegistrationOutcome
    fun cancelRegistration(eventId: String): Result<Unit>
    fun cancelEvent(eventId: String): Result<Unit>
    fun checkIn(eventId: String): Result<CheckIn>
}

package com.gogether.app.feature.event

import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CreateEventFormState(
    val title: String = "",
    val category: String = Category.SPORTS.name,
    val description: String = "",
    val startDate: String = "",
    val startTime: String = "",
    val endDate: String = "",
    val endTime: String = "",
    val location: String = "",
    val city: String = "Suzhou",
    val capacity: String = "4",
    val deadlineDate: String = "",
    val deadlineTime: String = "",
    val notes: String = "",
    val experience: String = ExperienceRequirement.NONE.name,
    val beginnerGuidance: Boolean = false,
    val requiredItems: String = "",
    val spareEquipment: String = "",
    val imageUri: String = "",
)

class EventViewModel(repository: GogetherRepository) : RepositoryViewModel(repository) {
    private val mutableCreateDraft = MutableStateFlow<CreateEventFormState?>(null)
    val createDraft = mutableCreateDraft.asStateFlow()

    fun retainCreateDraft(value: CreateEventFormState) { mutableCreateDraft.value = value }

    fun create(draft: EventDraft, clearRetainedDraft: Boolean = false) =
        execute({ repository.createEvent(draft) }) {
            if (clearRetainedDraft) mutableCreateDraft.value = null
            UiMessage("Your event is live!", it.id)
        }
    fun update(eventId: String, draft: EventDraft) = execute({ repository.updateEvent(eventId, draft) }) {
        UiMessage("Event details updated.", it.id)
    }
}

fun Event.reuseDraft(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): EventDraft {
    val originalTime = Instant.ofEpochMilli(startAt).atZone(zone).toLocalTime()
    val newStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
        .atTime(originalTime).atZone(zone).toInstant().toEpochMilli()
    return EventDraft(
        title = title, category = category, description = description, startAt = newStart,
        endAt = newStart + (endAt - startAt), location = location, city = city, capacity = capacity,
        registrationDeadline = newStart - 60 * 60 * 1000L, notes = notes,
        experienceRequirement = experienceRequirement, beginnerGuidance = beginnerGuidance,
        requiredItems = requiredItems, spareEquipment = spareEquipment, imageUri = imageUri,
    )
}

/** Strict parsing shared by the form and JVM tests. No silent date correction. */
fun parseEventDateTime(date: String, time: String, zone: ZoneId = ZoneId.systemDefault()): Long {
    val local = LocalDateTime.of(
        LocalDate.parse(date.trim(), DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)),
        LocalTime.parse(time.trim(), DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)),
    )
    require(zone.rules.getValidOffsets(local).size == 1) { "Choose an unambiguous local time (daylight-saving change)." }
    return local.atZone(zone).toInstant().toEpochMilli()
}

package com.gogether.app.feature.event

import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

class EventViewModel(repository: GogetherRepository) : RepositoryViewModel(repository) {
    fun create(draft: EventDraft) = execute({ repository.createEvent(draft) }) { UiMessage("Your event is live!", it.id) }
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

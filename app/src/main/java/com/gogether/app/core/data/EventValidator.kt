package com.gogether.app.core.data

import com.gogether.app.core.model.EventDraft
import java.time.Clock

object EventValidator {
    fun validate(draft: EventDraft, clock: Clock = Clock.systemUTC()): Map<String, String> = buildMap {
        if (draft.title.isBlank()) put("title", "Title is required")
        if (draft.category.isBlank()) put("category", "Category is required")
        if (draft.description.isBlank()) put("description", "Description is required")
        if (draft.meetingLocation.isBlank()) put("location", "Meeting location is required")
        if (draft.maxParticipants < 2) put("capacity", "Capacity must be at least 2")
        if (!draft.startAt.isAfter(clock.instant())) put("startAt", "Event must be in the future")
        if (!draft.registrationDeadline.isAfter(clock.instant())) put("deadline", "Deadline must be in the future")
        if (!draft.registrationDeadline.isBefore(draft.startAt)) put("deadline", "Deadline must be before start time")
    }
}

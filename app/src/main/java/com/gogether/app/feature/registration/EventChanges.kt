package com.gogether.app.feature.registration

import com.gogether.app.core.model.Event
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Describe meaningful changes once per save; updatedAt alone must never trigger a notice. */
internal fun eventChanges(before: Event, after: Event): List<String> = buildList {
    fun time(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm z", Locale.ENGLISH))
    if (before.title != after.title) add("Title: ${before.title} → ${after.title}")
    if (before.startAt != after.startAt) add("Starts: ${time(before.startAt)} → ${time(after.startAt)}")
    if (before.endAt != after.endAt) add("Ends: ${time(before.endAt)} → ${time(after.endAt)}")
    if (before.location != after.location || before.city != after.city)
        add("Location: ${before.city} · ${before.location} → ${after.city} · ${after.location}")
    if (before.registrationDeadline != after.registrationDeadline)
        add("Registration closes: ${time(before.registrationDeadline)} → ${time(after.registrationDeadline)}")
    if (before.meetingAt != after.meetingAt)
        add("Meeting time: ${time(before.meetingAt ?: before.startAt)} → ${time(after.meetingAt ?: after.startAt)}")
    if (before.capacity != after.capacity) add("Places: ${before.capacity} → ${after.capacity}")
    if (before.category != after.category) add("Category: ${before.category.label} → ${after.category.label}")
    if (before.description != after.description) add("Description changed. Open the event for details.")
    if (before.notes != after.notes) add("Event notes changed. Open the event for details.")
    if (before.experienceRequirement != after.experienceRequirement || before.beginnerGuidance != after.beginnerGuidance ||
        before.requiredItems != after.requiredItems || before.spareEquipment != after.spareEquipment)
        add("Participation or equipment information changed. Open the event for details.")
    if (before.imageUri != after.imageUri) add("Event cover photo changed.")
}

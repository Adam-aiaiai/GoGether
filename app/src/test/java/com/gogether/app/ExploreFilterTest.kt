package com.gogether.app

import com.gogether.app.core.model.*
import com.gogether.app.feature.explore.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ExploreFilterTest {
    private val now = 1_800_000_000_000L
    private val event = Event(
        id = "event", organizerId = "u1", title = "Beginner badminton", category = Category.SPORTS,
        description = "Learn a friendly doubles game", startAt = now + DAY, endAt = now + DAY + HOUR,
        location = "Sports Centre", city = "Suzhou", capacity = 6,
        registrationDeadline = now + DAY - HOUR, notes = "Meet at court 3", createdAt = now,
        updatedAt = now, experienceRequirement = ExperienceRequirement.NONE, beginnerGuidance = true,
        requiredItems = "Sports shoes and water", spareEquipment = "Two spare rackets",
    )

    @Test fun searchesEventAndParticipationInformation() {
        listOf("badminton", "friendly doubles", "court 3", "sports shoes", "spare rackets", "no experience")
            .forEach { query -> assertTrue(query, event.matchesExploreFilter(ExploreFilter(query = query), now)) }
        assertFalse(event.matchesExploreFilter(ExploreFilter(query = "photography"), now))
    }

    @Test fun searchesAndFiltersByOrganizer() {
        assertTrue(event.matchesExploreFilter(ExploreFilter(query = "alice"), now,
            organizerName = "Alice Chen"))
        assertTrue(event.matchesExploreFilter(ExploreFilter(organizerQuery = "chen"), now,
            organizerName = "Alice Chen"))
        assertFalse(event.matchesExploreFilter(ExploreFilter(organizerQuery = "bob"), now,
            organizerName = "Alice Chen"))
    }

    @Test fun filtersByCommonFreeTimeWindows() {
        val zone = ZoneId.of("UTC")
        val saturdayMorning = ZonedDateTime.of(2027, 1, 2, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val saturdayEvening = ZonedDateTime.of(2027, 1, 2, 19, 0, 0, 0, zone).toInstant().toEpochMilli()
        val before = saturdayMorning - DAY
        val morning = event.copy(startAt = saturdayMorning, endAt = saturdayMorning + HOUR)
        val evening = event.copy(startAt = saturdayEvening, endAt = saturdayEvening + HOUR)
        assertTrue(morning.matchesExploreFilter(ExploreFilter(freeTime = FreeTimeFilter.MORNING), before, zone))
        assertTrue(morning.matchesExploreFilter(ExploreFilter(freeTime = FreeTimeFilter.WEEKEND), before, zone))
        assertFalse(morning.matchesExploreFilter(ExploreFilter(freeTime = FreeTimeFilter.EVENING), before, zone))
        assertTrue(evening.matchesExploreFilter(ExploreFilter(freeTime = FreeTimeFilter.EVENING), before, zone))
    }

    @Test fun filtersCategoryLocationTimeAndBeginnerSupport() {
        assertTrue(event.matchesExploreFilter(ExploreFilter(category = Category.SPORTS, city = "centre",
            time = EventTimeFilter.NEXT_7_DAYS, beginnerFriendlyOnly = true,
            guidanceOnly = true, spareEquipmentOnly = true), now, ZoneId.of("UTC")))
        assertFalse(event.matchesExploreFilter(ExploreFilter(category = Category.READING), now))
        assertFalse(event.matchesExploreFilter(ExploreFilter(city = "Shanghai"), now))
        assertFalse(event.matchesExploreFilter(ExploreFilter(time = EventTimeFilter.TODAY), now, ZoneId.of("UTC")))
    }

    @Test fun excludesCancelledAndEndedEvents() {
        assertFalse(event.copy(status = EventStatus.CANCELLED).matchesExploreFilter(ExploreFilter(), now))
        assertFalse(event.copy(startAt = now - 2 * HOUR, endAt = now).matchesExploreFilter(ExploreFilter(), now))
    }

    companion object {
        private const val HOUR = 60 * 60 * 1000L
        private const val DAY = 24 * HOUR
    }
}

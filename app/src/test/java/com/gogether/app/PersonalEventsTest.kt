package com.gogether.app

import com.gogether.app.core.data.DemoData
import com.gogether.app.core.model.*
import com.gogether.app.feature.profile.*
import org.junit.Assert.*
import org.junit.Test

class PersonalEventsTest {
    private val now = 1_800_000_000_000L
    private val seed = DemoData.create(now)
    @Test fun activeTabsExcludeEndedAndCancelledEvents() {
        val bob = seed.copy(currentUserId = "bob")
        assertEquals(listOf("event-1"), personalEvents(bob, PersonalEventTab.JOINED, now).map { it.id })
        assertEquals(listOf("event-history"), personalEvents(bob, PersonalEventTab.HISTORY, now).map { it.id })
        val cancelled = bob.copy(events = bob.events.map { if (it.id == "event-1") it.copy(status = EventStatus.CANCELLED) else it })
        assertTrue(personalEvents(cancelled, PersonalEventTab.JOINED, now).isEmpty())
        assertEquals(setOf("event-1", "event-history"), personalEvents(cancelled, PersonalEventTab.HISTORY, now).map { it.id }.toSet())
    }
    @Test fun cancelledWaitlistIsHistoryAndAccountSwitchNeverLeaksIt() {
        val emma = seed.copy(currentUserId = "emma")
        assertEquals(listOf("event-1"), personalEvents(emma, PersonalEventTab.WAITLISTED, now).map { it.id })
        val withdrew = emma.copy(registrations = emma.registrations.map {
            if (it.userId == "emma" && it.eventId == "event-1") it.copy(status = RegistrationStatus.CANCELLED) else it
        })
        assertTrue(personalEvents(withdrew, PersonalEventTab.WAITLISTED, now).isEmpty())
        assertTrue(personalEvents(withdrew, PersonalEventTab.HISTORY, now).any { it.id == "event-1" })
        assertTrue(personalEvents(withdrew.copy(currentUserId = "finn"), PersonalEventTab.HISTORY, now).isEmpty())
        assertTrue(personalEvents(withdrew.copy(currentUserId = null), PersonalEventTab.CREATED, now).isEmpty())
    }
    @Test fun endBoundaryUsesBsStatusRuleAndCreatedKeepsHistory() {
        val event = seed.events.first()
        val bob = seed.copy(currentUserId = "bob")
        assertTrue(personalEvents(bob, PersonalEventTab.JOINED, event.endAt - 1).any { it.id == event.id })
        assertFalse(personalEvents(bob, PersonalEventTab.JOINED, event.endAt).any { it.id == event.id })
        assertTrue(personalEvents(bob, PersonalEventTab.HISTORY, event.endAt).any { it.id == event.id })
        val alice = seed.copy(currentUserId = "alice")
        assertTrue(personalEvents(alice, PersonalEventTab.CREATED, now).any { it.id == "event-history" })
    }
    @Test fun participationSummaryIncludesOnlyTheCurrentUsersRecords() {
        val event = seed.events.last()
        val state = seed.copy(currentUserId = "bob", checkIns = listOf(CheckIn("c", event.id, "bob", now)),
            feedback = listOf(Feedback("f", event.id, "bob", 4, "Good", now)))
        assertTrue(participationSummary(state, event, now).contains("4/5"))
        assertTrue(participationSummary(state, event, now).contains("Demo check-in recorded"))
        assertFalse(participationSummary(state.copy(currentUserId = "emma"), event, now).contains("4/5"))
    }
}

package com.gogether.app

import com.gogether.app.feature.checkin.prototype.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MultiActivityIsolationTest {
    private val start = 1_800_000_000_000L
    private var now = start
    private val scenario = MultiActivityCheckInScenario(start)
    private val a = scenario.aliceFirst
    private val b = scenario.aliceSecond
    private val c = scenario.carolEvent
    private val store = MemoryPrototypeStore().also(scenario::seed)
    private val backend = LocalPrototypeBackend(store, { now }, { 0 }, scenario.registrations, scenario.activities)
    private fun fix(session: PrototypeSession) = PositionFix(session.meeting, 10.0, 0)
    private fun code(session: PrototypeSession) = PrototypeCodeCodec.encode(backend.issue(scenario.activities.activity(session).organizerId, session, fix(session)))

    @Test fun eachOrganizerCanIssueAndViewOnlyTheirOwnActivities() {
        assertEquals(a, backend.hostedSession("alice", a))
        assertEquals(b, backend.hostedSession("alice", b))
        assertEquals(c, backend.hostedSession("carol", c))
        assertNull(backend.hostedSession("alice", c))
        listOf(a, b, c).forEach { session ->
            val owner = scenario.activities.activity(session).organizerId
            assertEquals(session, PrototypeCodeCodec.decode(code(session)).session)
            assertEquals(scenario.registrations.participants(session.id), backend.attendance(owner, session))
            val other = if (owner == "alice") "carol" else "alice"
            assertThrows(IllegalArgumentException::class.java) { backend.issue(other, session, fix(session)) }
            assertThrows(IllegalArgumentException::class.java) { backend.attendance(other, session) }
        }
        assertThrows(IllegalArgumentException::class.java) { backend.attendance("", a) }
    }

    @Test fun membershipReceiptsAndDuplicateChecksArePerActivity() {
        assertEquals(CheckInRegistration.CONFIRMED, scenario.registrations.status(a.id, "bob"))
        assertEquals(CheckInRegistration.WAITLISTED, scenario.registrations.status(b.id, "bob"))
        assertEquals(CheckInRegistration.NONE, scenario.registrations.status(c.id, "alice"))
        backend.submit("bob", code(a), fix(a))
        assertThrows(IllegalArgumentException::class.java) { backend.submit("bob", code(a), fix(a)) }
        assertThrows(IllegalArgumentException::class.java) { backend.submit("bob", code(b), fix(b)) }
        backend.submit("bob", code(c), fix(c))
        assertThrows(IllegalArgumentException::class.java) { backend.submit("alice", code(c), fix(c)) }
        assertEquals(listOf("bob"), backend.attendance("alice", a).filter { it.checkedInAt != null }.map { it.userId })
        assertTrue(backend.attendance("alice", b).all { it.checkedInAt == null })
        assertEquals(listOf("bob"), backend.attendance("carol", c).filter { it.checkedInAt != null }.map { it.userId })
        assertEquals(setOf(a.id, c.id), backend.history("bob").filter { it.receipt != null }.map { it.session.id }.toSet())
    }

    @Test fun unknownIdsConflictingSnapshotsAndWrongLocationsCannotCreateRecords() {
        val unknown = a.copy(id = UUID.randomUUID().toString())
        val changed = a.copy(meeting = c.meeting)
        listOf(unknown, changed).forEach { session ->
            val raw = PrototypeCodeCodec.encode(PrototypeCode(session, now, now + 30_000))
            assertThrows(IllegalArgumentException::class.java) { backend.rememberCode("bob", raw) }
            assertThrows(IllegalArgumentException::class.java) { backend.submit("bob", raw, fix(session)) }
            assertThrows(IllegalArgumentException::class.java) { backend.attendance("alice", session) }
            assertThrows(IllegalArgumentException::class.java) { backend.reviews("bob", session) }
        }
        assertThrows(IllegalArgumentException::class.java) { backend.submit("bob", code(c), fix(a)) }
        assertTrue(store.receipts.isEmpty())
        assertTrue(scenario.registrations.participants(unknown.id).isEmpty())
    }

    @Test fun privateAndAnonymousReviewsUseThisOrganizerNotTheAccountNameAlice() {
        now = a.endAt
        // An associated history record does not grant organizational privileges or registration.
        store.rememberSession("alice", c)
        store.rememberSession("carol", b)
        backend.submitFeedback("bob", a.id, 4, "只给 Alice", FeedbackDetails(anonymous = true, visibility = FeedbackVisibility.ORGANIZER_ONLY))
        backend.submitFeedback("bob", c.id, 3, "只给 Carol", FeedbackDetails(anonymous = true, visibility = FeedbackVisibility.ORGANIZER_ONLY))
        backend.submitFeedback("david", c.id, 5, "公开慢跑评价")
        assertEquals(listOf("只给 Alice"), backend.reviews("alice", a).map { it.comment })
        assertTrue(backend.reviews("carol", a).isEmpty())
        assertEquals(setOf("只给 Carol", "公开慢跑评价"), backend.reviews("carol", c).map { it.comment }.toSet())
        assertEquals(listOf("公开慢跑评价"), backend.reviews("alice", c).map { it.comment })
        assertEquals("匿名参与者", backend.reviews("carol", c).single { it.comment == "只给 Carol" }.authorName)
        assertThrows(IllegalArgumentException::class.java) { backend.submitFeedback("bob", b.id, 5, "候补不能评价") }
        assertThrows(IllegalArgumentException::class.java) { backend.submitFeedback("alice", c.id, 5, "非报名不能评价") }
        assertThrows(IllegalArgumentException::class.java) { backend.submitFeedback("bob", c.id, 5, "重复评价") }
        assertTrue(backend.reviews("alice", b).isEmpty())
    }

    @Test fun cancellationAndEndTimeOfOneActivityDoNotAffectOthers() {
        now = start - 1
        assertThrows(IllegalArgumentException::class.java) { backend.cancel("alice", c) }
        backend.cancel("carol", c)
        assertTrue(backend.isCancelled(c))
        assertFalse(backend.isCancelled(a))
        assertFalse(backend.isCancelled(b))
        backend.submit("bob", code(a), fix(a))
        assertThrows(IllegalArgumentException::class.java) { backend.issue("carol", c, fix(c)) }
        now = b.endAt
        backend.submitFeedback("david", b.id, 4, "桌游已结束")
        assertThrows(IllegalArgumentException::class.java) { backend.submitFeedback("bob", a.id, 4, "羽毛球还没结束") }
        now = a.endAt
        assertThrows(IllegalArgumentException::class.java) { backend.submitFeedback("bob", c.id, 4, "取消不能新评价") }
        assertEquals(4, backend.history("david").single { it.session == b }.feedback!!.rating)
    }
}

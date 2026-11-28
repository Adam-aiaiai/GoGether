package com.gogether.app

import com.gogether.app.core.data.DemoData
import com.gogether.app.core.data.cloud.CloudDto
import com.gogether.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class EventRatingsTest {
    private val now=10_000_000L
    private val event=DemoData.create(now).events.first().copy(id="e",organizerId="host",status=EventStatus.OPEN,startAt=now-6000,endAt=now-1000)
    private val people=listOf("host","a","b","private","deleted","absent")
    private val regs=people.map {Registration("r-$it","e",it,RegistrationStatus.CONFIRMED,now-10000,now-10000,1)}
    private val arrivals=regs.filter {it.userId!="absent"}.map {CheckIn("c-${it.userId}","e",it.userId,now-5000,CheckInStatus.QR_RECORDED,it.attendanceKey)}
    private val state=AppSnapshot(events=listOf(event),registrations=regs,checkIns=arrivals)
    private fun review(user:String,rating:Int=5)=RatingReview(user,"e",user,rating,now-500)
    private fun summary(reviews:List<RatingReview>,deleted:Set<String> = emptySet(),s:AppSnapshot=state,e:Event=event)=eventRatingSummary(s,e,reviews,deleted,now)
    @Test fun meanCountsOnlyPublicAttendedParticipants() {
        val actual=summary(listOf(review("a",5),review("b",3),review("host",1),review("private",1).copy(public=false),review("deleted",1),review("absent",1)),setOf("deleted"))
        assertEquals(EventRatingSummary(8,2),actual);assertEquals("4.0",actual.displayAverage)
    }
    @Test fun noReviewsIsDistinctFromZeroStarsAndUnknown() {
        val actual=summary(emptyList());assertNull(actual.average);assertNull(actual.displayAverage);assertEquals(0,actual.reviewCount)
        assertTrue(AppSnapshot().eventRatings.isEmpty())
    }
    @Test fun oldAndNewRegistrationCyclesCannotShareRatingEligibility() {
        assertEquals(0,summary(listOf(review("a").copy(registrationKey="earlier-registration:1"))).reviewCount)
        val changed=state.copy(registrations=regs.map {if(it.userId=="a")it.copy(queueOrder=2) else it})
        assertEquals(0,summary(listOf(review("a")),s=changed).reviewCount)
    }
    @Test fun olderCheckInAndReviewTimestampsMustProveEligibility() {
        val legacy=state.copy(checkIns=arrivals.map {it.copy(registrationKey=null)})
        assertEquals(1,summary(listOf(review("a")),s=legacy).reviewCount)
        assertEquals(0,summary(listOf(review("a").copy(createdAt=now-7000)),s=legacy).reviewCount)
        assertEquals(0,summary(listOf(review("a")),s=legacy.copy(registrations=regs.map {if(it.userId=="a")it.copy(joinedAt=now-4000) else it})).reviewCount)
    }
    @Test fun latestReviewPerUserWinsBeforeVisibilityAndDeletionFilters() {
        val old=review("a",5).copy(id="old",createdAt=now-900)
        val current=review("a",2).copy(id="current")
        assertEquals(EventRatingSummary(2,1),summary(listOf(old,current)))
        assertEquals(0,summary(listOf(old,current.copy(public=false))).reviewCount)
        assertEquals(0,summary(listOf(old,current),setOf("current")).reviewCount)
    }
    @Test fun futurePrematureInvalidAndDraftRatingsAreExcluded() {
        val variants=listOf(review("a").copy(createdAt=now+1),review("a").copy(createdAt=now-1500),review("a",0),review("a",6),review("a").copy(submitted=false))
        variants.forEach {assertEquals(0,summary(listOf(it)).reviewCount)}
    }
    @Test fun cancelledUpcomingAndInactiveRegistrationsHaveNoScore() {
        assertEquals(0,summary(listOf(review("a")),e=event.copy(status=EventStatus.CANCELLED)).reviewCount)
        assertEquals(0,summary(listOf(review("a")),e=event.copy(endAt=now+1)).reviewCount)
        for(status in listOf(RegistrationStatus.CANCELLED,RegistrationStatus.REJECTED,RegistrationStatus.WAITLISTED)) {
            assertEquals(0,summary(listOf(review("a")),s=state.copy(registrations=regs.map {if(it.userId=="a")it.copy(status=status) else it})).reviewCount)
        }
    }
    @Test fun completedEventsAndOtherEventIsolation() {
        assertEquals(1,summary(listOf(review("a")),e=event.copy(status=EventStatus.COMPLETED,endAt=now+1)).reviewCount)
        assertEquals(0,summary(listOf(review("a").copy(eventId="other"))).reviewCount)
    }
    @Test fun averagesUseOneDecimalAndStandardRounding() {
        assertEquals("4.3",EventRatingSummary(13,3).displayAverage)
        assertEquals("4.7",EventRatingSummary(14,3).displayAverage)
        assertEquals("4.3",EventRatingSummary(17,4).displayAverage)
        assertEquals("1.1",EventRatingSummary(21,20).displayAverage)
        assertThrows(IllegalArgumentException::class.java){EventRatingSummary(0,1)}
    }
    @Test fun cloudDtoUsesServerTotalsAndDoesNotInventMissingAggregates() {
        val base=mapOf("currentUserId" to "a","users" to listOf(mapOf("id" to "a","displayName" to "A","createdAt" to 1)))
        assertTrue(CloudDto.snapshot(base,"a").eventRatings.isEmpty())
        val data=base+mapOf("eventRatings" to listOf(mapOf("eventId" to "e","ratingSum" to 9,"reviewCount" to 2)))
        assertEquals(EventRatingSummary(9,2),CloudDto.snapshot(data,"a").eventRatings["e"])
    }
}

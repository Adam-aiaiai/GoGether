package com.gogether.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.feature.social.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.File
import java.util.UUID

/** Explicit fixture setup after data restoration; not part of the automated regression suite. */
class PhaseFourDemoSetup {
    @Test fun prepareManualFixtures():Unit=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val disk=FileSnapshotStore(File(context.filesDir,"integrated-snapshot-v1.json"))
        val saved=requireNotNull(disk.load());val now=System.currentTimeMillis();val prefix="phase4-${UUID.randomUUID()}"
        val rated=saved.events.first().copy(id="$prefix-rated",organizerId="alice",title="Phase 4 - Public ratings",createdAt=now,updatedAt=now,status=EventStatus.COMPLETED,startAt=now-900000,endAt=now-60000,registrationDeadline=now-960000,capacity=6,description="Expected: 4.0 from 2 public participant reviews. Bob rated 5 and Carol rated 3. Organizer, private and deleted reviews do not count.",meetingAt=null,meetingLocation=null,meetingNotes="",imageUri="")
        val empty=rated.copy(id="$prefix-empty",title="Phase 4 - No public ratings",startAt=now-1200000,endAt=now-120000,registrationDeadline=now-1260000,description="Expected: No reviews. This event has an organizer review and a private review only.")
        val events=listOf(rated,empty)
        val regs=events.flatMap {e->listOf("alice","bob","carol","david","emma").mapIndexed {i,u->Registration("${e.id}-$u",e.id,u,RegistrationStatus.CONFIRMED,now-1800000,now-1800000,i+1L)}}
        val arrivals=regs.map {r->CheckIn("${r.id}-arrival",r.eventId,r.userId,events.first {it.id==r.eventId}.startAt+10000,if(r.userId=="alice")CheckInStatus.HOST_CONFIRMED else CheckInStatus.QR_RECORDED,r.attendanceKey)}
        val next=saved.copy(currentUserId="alice",events=saved.events+events,registrations=saved.registrations+regs,checkIns=saved.checkIns+arrivals)
        disk.save(next);val repo=FakeGogetherRepository(next,store=disk)
        val store=PreferencesPrototypeStore(context);val bridge=RegistrationAttendanceBridge({repo.state.value},store)
        val gateway=RepositorySocialGateway(context,repo)
        for((e,rows) in listOf(rated to listOf(Triple("bob",5,FeedbackDetails(anonymous=true)),Triple("carol",3,FeedbackDetails()),Triple("alice",1,FeedbackDetails()),Triple("david",1,FeedbackDetails(anonymous=true,visibility=FeedbackVisibility.ORGANIZER_ONLY)),Triple("emma",2,FeedbackDetails())),empty to listOf(Triple("alice",5,FeedbackDetails()),Triple("david",4,FeedbackDetails(visibility=FeedbackVisibility.ORGANIZER_ONLY))))) {
            for((user,score,details) in rows){repo.login(user).getOrThrow();val session=bridge.prepareFeedback(user,e.id);bridge.backend.submitFeedback(user,session.id,score,if(user=="alice")"Organizer review: excluded from the public score." else if(user=="david")"Private feedback for the organizer." else "Public participant review for the rating test.",details)}
        }
        repo.login("emma").getOrThrow();gateway.call("delete",mapOf("contentId" to store.feedback(bridge.key(rated),"emma")!!.contentId))
        repo.login("alice").getOrThrow()
        val totals=gateway.projectRatings(bridge.withAttendance(repo.state.value)).eventRatings
        assertEquals(EventRatingSummary(8,2),totals[rated.id]);assertEquals(EventRatingSummary(),totals[empty.id])
        File(context.filesDir,"phase4-manual-fixtures.txt").writeText(events.joinToString("\n"){"${it.id} | ${it.title} | ${totals[it.id]}"}+"\nStart: Alice / Profile History\nBob: delete 5-star review -> 3.0 (1 review); submit 2-star replacement -> 2.5 (2 reviews).")
    }
}

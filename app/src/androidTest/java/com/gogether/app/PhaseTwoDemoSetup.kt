package com.gogether.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.feature.social.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.util.UUID

/** Explicitly invoked after restoring the user's backup, never part of automated acceptance. */
class PhaseTwoDemoSetup {
    @Test fun prepareManualFixtures():Unit=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val disk=FileSnapshotStore(File(context.filesDir,"integrated-snapshot-v1.json"))
        val saved=requireNotNull(disk.load());val now=System.currentTimeMillis();val prefix="phase2-${UUID.randomUUID()}"
        val template=saved.events.first()
        val q=template.copy(id="$prefix-questions",organizerId="alice",title="Phase 2 - Questions",createdAt=now,updatedAt=now,status=EventStatus.OPEN,startAt=now+7200000,endAt=now+10800000,registrationDeadline=now+3600000,capacity=3,description="Ask follow-ups, report content and test registration restrictions.",meetingAt=null,meetingLocation=null,meetingNotes="",imageUri="")
        val r=q.copy(id="$prefix-reviews",title="Phase 2 - Reviews",status=EventStatus.COMPLETED,startAt=now-7200000,endAt=now-60000,registrationDeadline=now-7500000,capacity=4,description="Try replies, likes, anonymous names, deletion and replacement reviews.")
        val p=r.copy(id="$prefix-private",title="Phase 2 - Private feedback",description="Only Carol and Alice can read the private review and its replies.")
        val events=listOf(q,r,p)
        val regs=events.flatMap {e->listOf("alice","bob","carol","david").mapIndexed {index,id->Registration("${e.id}-$id",e.id,id,if(e==q&&index==3)RegistrationStatus.WAITLISTED else RegistrationStatus.CONFIRMED,now-9000000,now-9000000,index+1L)}}
        val checked=regs.filter {it.eventId!=q.id}.map {reg->CheckIn("${reg.id}-arrival",reg.eventId,reg.userId,now-7000000,if(reg.userId=="alice")CheckInStatus.HOST_CONFIRMED else CheckInStatus.QR_RECORDED,reg.attendanceKey)}
        val question=EventQuestion("$prefix-question",q.id,"bob","Can I ask a follow-up here?",now-5000,"Yes, use Reply on any message.","alice",now-4000)
        val users=if(saved.users.any {it.id==LocalSocialStore.MAINTAINER}) saved.users else saved.users+User(LocalSocialStore.MAINTAINER,"Project maintainer (Demo)",emptySet(),now)
        val next=saved.copy(users=users,currentUserId="bob",events=saved.events+events,registrations=saved.registrations+regs,checkIns=saved.checkIns+checked,questions=saved.questions+question)
        disk.save(next)
        val repo=FakeGogetherRepository(next,store=disk)
        val store=PreferencesPrototypeStore(context);val bridge=RegistrationAttendanceBridge({repo.state.value},store)
        for((user,event,detail) in listOf(Triple("bob",r,FeedbackDetails(anonymous=true)),Triple("carol",r,FeedbackDetails()),Triple("alice",r,FeedbackDetails()),Triple("carol",p,FeedbackDetails(anonymous=true,visibility=FeedbackVisibility.ORGANIZER_ONLY)))) {
            repo.login(user).getOrThrow();val session=bridge.prepareFeedback(user,event.id)
            bridge.backend.submitFeedback(user,session.id,if(user=="bob")4 else 5,if(event==p) "Private feedback for the organizer." else "A helpful event. Try replying to this review.",detail)
        }
        repo.login("bob").getOrThrow();val social=RepositorySocialGateway(context,repo)
        social.call("reply",mapOf("contentId" to "answer-${question.id}","content" to "Great, each reply keeps its target."))
        repo.login("carol").getOrThrow();social.call("report",mapOf("contentId" to question.id,"reason" to "Demo report for testing the maintainer queue."))
        repo.login("bob").getOrThrow()
        File(context.filesDir,"phase2-manual-fixtures.txt").writeText(events.joinToString("\n"){it.id+" | "+it.title})
    }
}

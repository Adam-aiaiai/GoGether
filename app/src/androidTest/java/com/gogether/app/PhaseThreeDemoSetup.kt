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

/** Explicit manual setup after restoring the pre-test data; not a regression test. */
class PhaseThreeDemoSetup {
    @Test fun prepareManualFixtures():Unit=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val disk=FileSnapshotStore(File(context.filesDir,"integrated-snapshot-v1.json"))
        val saved=requireNotNull(disk.load());val now=System.currentTimeMillis();val prefix="phase3-${UUID.randomUUID()}"
        val template=saved.events.first()
        val q=template.copy(id="$prefix-questions",organizerId="alice",title="Phase 3 - Conversations",createdAt=now,updatedAt=now,status=EventStatus.OPEN,startAt=now+86400000,endAt=now+93600000,registrationDeadline=now+82800000,capacity=5,description="Try question notifications, follow-ups and the My Events unread badge.",meetingAt=null,meetingLocation=null,meetingNotes="",imageUri="")
        val r=q.copy(id="$prefix-reviews",title="Phase 3 - Review notifications",status=EventStatus.COMPLETED,startAt=now-7200000,endAt=now-60000,registrationDeadline=now-7500000,description="Bob has an anonymous public review, Carol a named review, and David an anonymous private review.")
        val events=listOf(q,r)
        val regs=events.flatMap {e->listOf("alice","bob","carol","david").mapIndexed {index,id->Registration("${e.id}-$id",e.id,id,RegistrationStatus.CONFIRMED,now-9000000,now-9000000,index+1L)}}
        val checked=regs.filter {it.eventId==r.id}.map {reg->CheckIn("${reg.id}-arrival",reg.eventId,reg.userId,now-7000000,if(reg.userId=="alice")CheckInStatus.HOST_CONFIRMED else CheckInStatus.QR_RECORDED,reg.attendanceKey)}
        val next=saved.copy(currentUserId="bob",events=saved.events+events,registrations=saved.registrations+regs,checkIns=saved.checkIns+checked)
        disk.save(next)
        val repo=FakeGogetherRepository(next,store=disk)
        val social=RepositorySocialGateway(context,repo)
        repo.askQuestion(q.id,"Will notifications take me to the exact reply?").getOrThrow()
        val question=repo.state.value.questions.last()
        repo.login("alice").getOrThrow()
        val answer=social.call("reply",mapOf("contentId" to question.id,"content" to "Yes. Tap the notification to open this reply."))["id"] as String
        repo.login("bob").getOrThrow();social.call("reply",mapOf("contentId" to answer,"content" to "Thanks! This follow-up should notify Alice."))
        val store=PreferencesPrototypeStore(context);val bridge=RegistrationAttendanceBridge({repo.state.value},store)
        for((user,detail) in listOf("bob" to FeedbackDetails(anonymous=true),"carol" to FeedbackDetails(),"david" to FeedbackDetails(anonymous=true,visibility=FeedbackVisibility.ORGANIZER_ONLY))) {
            repo.login(user).getOrThrow();val session=bridge.prepareFeedback(user,r.id)
            bridge.backend.submitFeedback(user,session.id,4,if(user=="david") "Private feedback: only David and Alice can open this thread." else "Review notification test. Try replying to this review.",detail)
        }
        repo.login("alice").getOrThrow()
        val root=store.feedback(bridge.key(r),"bob")!!.contentId
        val reviewReply=social.call("reply",mapOf("contentId" to root,"content" to "Thank you for the review."))["id"] as String
        repo.login("bob").getOrThrow();social.call("reply",mapOf("contentId" to reviewReply,"content" to "My anonymous name stays consistent in notifications."))
        repo.login("alice").getOrThrow()
        File(context.filesDir,"phase3-manual-fixtures.txt").writeText(events.joinToString("\n"){it.id+" | "+it.title}+"\nStart: Alice\nBob: question and review replies\nDavid: private review owner")
    }
}

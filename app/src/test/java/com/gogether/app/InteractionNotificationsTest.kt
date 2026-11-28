package com.gogether.app

import com.gogether.app.core.model.*
import com.gogether.app.feature.social.*
import org.junit.Assert.*
import org.junit.Test

class InteractionNotificationsTest {
    private val question=SocialContent("q","e","a","Question",1,"QUESTION")
    private val review=SocialContent("f","e","a","Private review",2,"REVIEW",anonymous=true)
    private val hosts=mapOf("e" to "host")
    private val users=listOf(User("a","Real name",emptySet(),1,avatarUri="real-photo"))
    private fun notice(c:SocialContent,type:NotificationType=NotificationType.REVIEW_POSTED,user:String="host")=Notification("n",user,"e",type,"old quoted text",1,contentId=c.id,rootId=c.rootId)
    private fun project(n:Notification,contents:List<SocialContent>,state:SocialState=SocialState())=projectInteractionNotice(n,contents,users,hosts,state)
    @Test fun questionsAndFollowUpsNotifyOnlyOtherRelevantPerson() {
        assertEquals(listOf("host"),interactionDeliveries(question,question,question,"host"){_,_->false}.map { it.userId })
        val answer=SocialContent("a1","e","host","Answer",2,"REPLY","q","q")
        assertEquals(listOf("a"),interactionDeliveries(answer,question,question,"host"){_,_->false}.map { it.userId })
        val follow=answer.copy(id="a2",authorId="a",targetId="a1")
        assertEquals(listOf("host"),interactionDeliveries(follow,question,answer,"host"){_,_->false}.map { it.userId })
    }
    @Test fun reviewRepliesDeduplicateRootAndTargetAndExcludeSelf() {
        val target=SocialContent("r","e","b","Reply",3,"REPLY","f","f")
        val reply=target.copy(id="new",authorId="c",targetId="r")
        val deliveries=interactionDeliveries(reply,review,target,"host"){_,_->false}
        assertEquals(setOf("a","b"),deliveries.map { it.userId }.toSet());assertEquals(2,deliveries.map { it.id }.toSet().size)
        assertEquals(listOf("a"),interactionDeliveries(target,review,review,"host"){_,_->false}.map { it.userId })
        assertTrue(interactionDeliveries(review.copy(authorId="host"),review.copy(authorId="host"),review,"host"){_,_->false}.isEmpty())
    }
    @Test fun privateAndBlockedDeliveriesAreRestricted() {
        val reply=SocialContent("r","e","a","Details",3,"REPLY","f","f",anonymous=true)
        assertTrue(interactionDeliveries(reply,review.copy(private=true),review,"host"){_,_->false}.isEmpty())
        assertTrue(interactionDeliveries(review,review,review,"host"){a,b->setOf(a,b)==setOf("host","a")}.isEmpty())
        assertEquals(listOf("host"),interactionDeliveries(review,review.copy(private=true),review,"host"){_,_->false}.map { it.userId })
    }
    @Test fun anonymousNoticeNeverUsesRealNameOrAvatar() {
        val saved=SocialState(aliases=mapOf(("e" to "a") to "Anonymous participant 03"))
        val n=project(notice(review),listOf(review),saved)!!
        assertEquals("Anonymous participant 03",n.actorName);assertEquals("",n.actorAvatarUri);assertEquals("Reviewed your event.",n.content)
        assertEquals("Anonymous participant",project(notice(review),listOf(review))!!.actorName)
    }
    @Test fun deletedMissingAndPrivateTargetsAreRedacted() {
        val n=notice(review)
        assertTrue(project(n,listOf(review),SocialState(deleted=setOf("f")))!!.content.contains("deleted"))
        assertEquals("Activity conversation",project(n,emptyList())!!.actorName)
        assertEquals("",project(n.copy(userId="stranger"),listOf(review.copy(private=true)))!!.actorAvatarUri)
        assertFalse(project(n.copy(userId="stranger"),listOf(review.copy(private=true)))!!.content.contains("Private review"))
    }
    @Test fun blockHidesInteractionsButPreservesImportantActivityUpdates() {
        val saved=SocialState(blocks=listOf(SocialBlock("b","host","a","PERSONAL","A")))
        assertNull(project(notice(review),listOf(review),saved))
        val important=notice(review,NotificationType.EVENT_CANCELLED)
        assertEquals(important,project(important,listOf(review),saved))
    }
    @Test fun unreadCountIsPerAccountAndIncludesEveryNewInteractionType() {
        val ns=listOf(NotificationType.QUESTION_REPLIED,NotificationType.REVIEW_POSTED,NotificationType.REVIEW_REPLIED).mapIndexed {i,t->notice(review,t).copy(id="$i")}
        val s=AppSnapshot(currentUserId="host",notifications=ns+ns.first().copy(id="other",userId="a"))
        assertEquals(3,s.unreadNotificationCount);assertTrue(ns.all {it.type.isInteraction})
        assertEquals(0,s.copy(notifications=ns.map {it.copy(isRead=true)}).unreadNotificationCount)
    }
}

package com.gogether.app

import com.gogether.app.feature.social.*
import org.junit.Assert.*
import org.junit.Test

class SocialEngineTest {
    private val users=mapOf("host" to "Host","a" to "A","b" to "B","maintainer" to "Maintainer")
    private val question=SocialContent("q","event","a","Question",1,"QUESTION")
    private val review=SocialContent("f","event","a","Review",2,"REVIEW",anonymous=true,rating=5)
    private fun engine(state:SocialState=SocialState(),roots:List<SocialContent> = listOf(question,review),open:Boolean=true) =
        SocialEngine(state,roots,users,mapOf("event" to "host"),{open},setOf("maintainer"),{10})
    @Suppress("UNCHECKED_CAST") private fun items(e:SocialEngine,actor:String,root:String)=e.read(actor,rootId=root)["items"] as List<Map<String,Any?>>
    private fun denied(block:()->Unit){assertThrows(IllegalArgumentException::class.java,block)}
    @Test fun questionSupportsFollowUpsWithIndependentTargets() {
        val e=engine();val first=e.mutate("host","reply",mapOf("contentId" to "q","content" to "Answer"))["id"] as String
        e.mutate("a","reply",mapOf("contentId" to first,"content" to "Follow-up"))
        assertEquals(3,items(e,"a","q").size);assertEquals(first,e.state.replies.last().targetId)
        denied { e.mutate("b","reply",mapOf("contentId" to "q","content" to "Hijack")) }
        denied { engine(e.state,open=false).mutate("host","reply",mapOf("contentId" to "q","content" to "Closed")) }
    }
    @Test fun deleteKeepsRepliesButPreventsFurtherWritesAndForeignDeletion() {
        val e=engine();e.mutate("host","reply",mapOf("contentId" to "q","content" to "Answer"))
        denied {e.mutate("host","delete",mapOf("contentId" to "q"))};e.mutate("a","delete",mapOf("contentId" to "q"))
        assertEquals("Content deleted",items(e,"host","q").first()["content"]);assertEquals(2,items(e,"host","q").size)
        denied {e.mutate("a","reply",mapOf("contentId" to "q","content" to "Again"))}
    }
    @Test fun likesAreDesiredStateAndIdempotent() {
        val e=engine();repeat(3){e.mutate("b","like",mapOf("contentId" to "f","liked" to true))};assertEquals(1,e.state.likes.size)
        e.mutate("b","like",mapOf("contentId" to "f","liked" to false));assertTrue(e.state.likes.isEmpty())
    }
    @Test fun privateThreadKeepsParentAccessAndAnonymousReplyIdentity() {
        val e=engine(roots=listOf(review.copy(private=true)))
        denied {e.read("b",rootId="f")};e.mutate("a","reply",mapOf("contentId" to "f","content" to "More"))
        val list=items(e,"host","f");assertEquals(list[0]["authorName"],list[1]["authorName"])
        assertTrue(list.all {"authorId" !in it && "userId" !in it});assertTrue(list.all {it["private"]==true})
        denied {e.mutate("b","report",mapOf("contentId" to "f","reason" to "Private guess"))}
    }
    @Test fun aliasesAreStableUniqueAndScopedToEvent() {
        val e=engine();val a=e.alias("event","a");val b=e.alias("event","b");assertNotEquals(a,b)
        assertEquals(a,engine(e.state).alias("event","a"));assertEquals(a,e.alias("other","b"))
    }
    @Test fun personalBlockHidesBothWaysAndPreventsInteractionButCanBeUndone() {
        val e=engine();e.mutate("b","blockUser",mapOf("contentId" to "f","kind" to "PERSONAL"))
        assertTrue(e.state.blocked("a","b"));assertTrue(e.state.blocks.single().label.startsWith("Anonymous"))
        denied {e.mutate("b","like",mapOf("contentId" to "f","liked" to true))};denied{e.read("b",rootId="f")}
        e.mutate("b","unblock",mapOf("blockId" to e.state.blocks.single().id));assertEquals(1,items(e,"b","f").size)
    }
    @Test fun reportIsNotDeletionAndOnlyMaintainerCanResolveWithAudit() {
        val e=engine();e.mutate("b","report",mapOf("contentId" to "f","reason" to "Harassment"))
        assertTrue(e.state.deleted.isEmpty());val id=e.state.reports.single().id
        denied{e.mutate("host","resolveReport",mapOf("reportId" to id,"resolution" to "REMOVED","outcome" to "Dislike"))}
        e.mutate("maintainer","resolveReport",mapOf("reportId" to id,"resolution" to "REMOVED","outcome" to "Policy violation"))
        assertTrue("f" in e.state.deleted);assertEquals("maintainer",e.state.reports.single().moderator);assertEquals(10,e.state.reports.single().resolvedAt)
    }
    @Test fun restrictionsHaveSeparateScopesAndOnlyOwnerCanUnblock() {
        val e=engine();e.mutate("host","blockUser",mapOf("targetUserId" to "a","kind" to "REGISTRATION"))
        assertTrue(e.state.registrationBlocked("host","a"));assertFalse(e.state.blocked("host","a"))
        denied{e.mutate("a","unblock",mapOf("blockId" to e.state.blocks.single().id))}
    }
    @Test fun questionListPreviewUsesVisibleActiveOrganizerRepliesOnly() {
        val e=engine()
        e.mutate("a","reply",mapOf("contentId" to "q","content" to "More context"))
        @Suppress("UNCHECKED_CAST")
        fun answer(engine:SocialEngine):Any? = (engine.read("a",eventId="event")["items"] as List<Map<String,Any?>>).single {it["id"]=="q"}["latestAnswer"]
        assertNull(answer(e))
        val id=e.mutate("host","reply",mapOf("contentId" to "q","content" to "Answer"))["id"] as String
        assertEquals("Answer",(answer(e) as Map<*,*>)["content"])
        val active=e.state
        e.mutate("host","delete",mapOf("contentId" to id))
        assertNull(answer(e))
        val blocked=engine(active.copy(blocks=listOf(SocialBlock("b","a","host","PERSONAL","Host"))))
        assertNull(answer(blocked))
    }

}

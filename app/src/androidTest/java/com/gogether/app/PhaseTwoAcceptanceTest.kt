package com.gogether.app

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.social.*
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class PhaseTwoAcceptanceTest {
    @get:Rule val compose=createComposeRule()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val time=System.currentTimeMillis()
    private val social=LocalSocialStore(context)
    private val saved=social.load()
    private val initial=DemoData.create(time).let {s->s.copy(currentUserId="bob",users=s.users+User(LocalSocialStore.MAINTAINER,"Maintainer",emptySet(),time),events=listOf(s.event("event-1")!!.copy(createdAt=System.nanoTime(),capacity=6,startAt=time+600000,endAt=time+7800000,registrationDeadline=time+300000)),questions=listOf(EventQuestion("phase2-question","event-1","bob","Can we ask follow-ups?",time,answer="Yes",answeredBy="alice",answeredAt=time+1))) }
    private val repo=FakeGogetherRepository(initial,{time},registrationBlocked={a,b->social.load().registrationBlocked(a,b)},interactionBlocked={a,b->social.load().blocked(a,b)})
    private val gateway=RepositorySocialGateway(context,repo)
    private val store=PreferencesPrototypeStore(context)
    private val bridge=RegistrationAttendanceBridge({repo.state.value},store,{time})
    @Before fun cleanStart(){social.edit { SocialState() to Unit }}
    @After fun restore(){social.edit {saved to Unit};val p=context.getSharedPreferences("checkin_prototype_v1",Context.MODE_PRIVATE);val e=p.edit();val key=bridge.key(initial.events.single());p.all.keys.filter{it.contains(key)}.forEach(e::remove);e.commit()}
    private fun call(action:String,args:Map<String,Any?> = emptyMap())=runBlocking{gateway.call(action,args)}
    @Suppress("UNCHECKED_CAST") private fun rows(value:Map<String,Any?>,name:String)=value[name] as List<Map<String,Any?>>
    @Test fun uiFollowUpReplyAndDeleteKeepsPlaceholder() {
        compose.setContent {GogetherTheme {SocialThread(gateway,"phase2-question",{})}}
        compose.onNodeWithTag("social_reply_answer-phase2-question").performScrollTo().performClick()
        compose.onNodeWithTag("social_input").performTextInput("What should I bring?")
        compose.onNodeWithTag("social_send").performClick()
        compose.waitUntil(5000){social.load().replies.size==2}
        compose.onNodeWithText("What should I bring?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("social_delete_phase2-question").performScrollTo().performClick()
        compose.onNodeWithTag("social_confirm").performClick()
        compose.waitUntil(5000){"phase2-question" in social.load().deleted}
        compose.onNodeWithText("Content deleted").performScrollTo().assertIsDisplayed()
        assertEquals(2,social.load().replies.size)
    }
    @Test fun uiReportCreatesQueueWithoutDeletingContent() {
        runBlocking {repo.login("carol")}
        compose.setContent {GogetherTheme {SocialThread(gateway,"phase2-question",{})}}
        compose.onNodeWithTag("social_report_phase2-question").performScrollTo().performClick()
        compose.onNodeWithTag("social_input").performTextInput("Please review")
        compose.onNodeWithTag("social_send").performClick()
        compose.waitUntil(5000){social.load().reports.size==1}
        assertTrue(social.load().deleted.isEmpty());assertEquals("Please review",social.load().reports.single().reason)
    }
    @Test fun uiPersonalBlockRestrictsThreadAndPersists() {
        runBlocking {repo.login("carol")}
        compose.setContent {GogetherTheme {SocialThread(gateway,"phase2-question",{})}}
        compose.onNodeWithTag("social_block_phase2-question").performScrollTo().performClick()
        compose.onNodeWithTag("social_confirm").performClick()
        compose.waitUntil(5000){social.load().blocked("bob","carol")}
        assertTrue(LocalSocialStore(context).load().blocked("carol","bob"))
        assertTrue(runCatching {call("reply",mapOf("contentId" to "phase2-question","content" to "Blocked"))}.isFailure)
    }
    @Test fun maintainerHandlesReportAndReporterCanReadOutcome():Unit=runBlocking {
        repo.login("carol");gateway.call("report",mapOf("contentId" to "phase2-question","reason" to "Needs review"))
        val id=social.load().reports.single().id
        repo.login("alice");assertTrue(runCatching {gateway.call("resolveReport",mapOf("reportId" to id,"resolution" to "REMOVED","outcome" to "No"))}.isFailure)
        repo.login(LocalSocialStore.MAINTAINER);gateway.call("resolveReport",mapOf("reportId" to id,"resolution" to "REMOVED","outcome" to "Reviewed"))
        repo.login("carol");assertEquals("REMOVED",rows(gateway.call("read"),"reports").single()["status"])
        assertEquals(LocalSocialStore.MAINTAINER,social.load().reports.single().moderator)
    }
    @Test fun registrationBlacklistDoesNotCancelExistingPlaceAndBlocksRejoin():Unit=runBlocking {
        repo.login("alice");gateway.call("blockUser",mapOf("targetUserId" to "bob","kind" to "REGISTRATION"))
        assertEquals(RegistrationStatus.CONFIRMED,repo.state.value.registration("event-1","bob")!!.status)
        repo.login("bob");repo.cancelRegistration("event-1").getOrThrow();assertTrue(repo.join("event-1").isFailure)
        repo.login("alice");gateway.call("unblock",mapOf("blockId" to social.load().blocks.single().id))
        assertEquals(RegistrationStatus.CANCELLED,repo.state.value.registration("event-1","bob")!!.status)
        repo.login("bob");repo.join("event-1").getOrThrow()
    }
    @Test fun persistedAnonymousReviewDeletionAllowsReplacementAndPreservesAlias():Unit=runBlocking {
        val session=bridge.backend.history("bob").single().session
        val first=PrototypeFeedback(session.id,"bob",4,"First review",time,FeedbackDetails(anonymous=true))
        store.saveFeedback(first)
        val before=store.feedback(session.id,"bob")!!
        gateway.call("delete",mapOf("contentId" to before.contentId));assertNull(store.feedback(session.id,"bob"))
        assertNull(bridge.backend.history("bob").single().feedback)
        val replacement=first.copy(comment="Replacement",publicId=java.util.UUID.randomUUID().toString())
        store.saveFeedback(replacement)
        assertEquals(before.anonymousName,PreferencesPrototypeStore(context).feedback(session.id,"bob")!!.anonymousName)
        assertEquals("Content deleted",rows(gateway.call("read",mapOf("rootId" to before.contentId)),"items").single()["content"])
        repo.login("alice");val list=rows(gateway.call("read",mapOf("rootId" to replacement.contentId)),"items")
        assertTrue(list.single()["authorName"].toString().startsWith("Anonymous participant "));assertFalse(list.single().containsKey("authorId"))
    }
    @Test fun privateReviewRepliesDenyOtherAccountsAndKeepAlias():Unit=runBlocking {
        val session=bridge.backend.history("bob").single().session
        store.saveFeedback(PrototypeFeedback(session.id,"bob",4,"Private review",time,FeedbackDetails(anonymous=true,visibility=FeedbackVisibility.ORGANIZER_ONLY)))
        val id=store.feedback(session.id,"bob")!!.contentId
        gateway.call("reply",mapOf("contentId" to id,"content" to "More details"))
        repo.login("carol");assertTrue(runCatching {gateway.call("read",mapOf("rootId" to id))}.isFailure)
        repo.login("alice");val thread=rows(gateway.call("read",mapOf("rootId" to id)),"items")
        assertEquals(thread[0]["authorName"],thread[1]["authorName"])
    }
}

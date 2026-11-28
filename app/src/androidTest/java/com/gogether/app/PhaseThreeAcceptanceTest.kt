package com.gogether.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.social.*
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class PhaseThreeAcceptanceTest {
    @get:Rule val compose=createComposeRule()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val time=System.currentTimeMillis()
    private val social=LocalSocialStore(context)
    private val saved=social.load()
    private val prefs=context.getSharedPreferences("checkin_prototype_v1",Context.MODE_PRIVATE)
    private val savedPrefs=prefs.all.toMap()
    private val initial=DemoData.create(time).let {s->s.copy(currentUserId="bob",events=listOf(s.event("event-1")!!.copy(createdAt=System.nanoTime(),startAt=time+600000,endAt=time+7800000,registrationDeadline=time+300000)),questions=emptyList(),notifications=emptyList(),feedback=emptyList())}
    private val repo=FakeGogetherRepository(initial,{time},interactionBlocked={a,b->social.load().blocked(a,b)})
    private val gateway=RepositorySocialGateway(context,repo)
    private val reviews=PreferencesPrototypeStore(context)
    private val bridge=RegistrationAttendanceBridge({repo.state.value},reviews,{time})
    @Before fun clean(){social.edit { SocialState() to Unit };prefs.edit().clear().commit()}
    @After fun restore(){social.edit {saved to Unit};val edit=prefs.edit().clear();savedPrefs.forEach {(k,v)->when(v){is String->edit.putString(k,v);is Long->edit.putLong(k,v);is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is Float->edit.putFloat(k,v)}};edit.commit()}
    private fun notices()=gateway.projectNotifications(repo.state.value).notifications
    private fun makeQuestion()=runBlocking {repo.askQuestion("event-1","Can we test notifications?").getOrThrow();repo.state.value.questions.single().id}
    private fun login(id:String)=runBlocking{repo.login(id).getOrThrow()}
    private fun reply(id:String,text:String)=runBlocking{gateway.call("reply",mapOf("contentId" to id,"content" to text))["id"] as String}
    @Test fun badgeStaysOnEntryAndClickOpensExactReplyAndMarksOnlyThatNoticeRead() {
        val q=makeQuestion();login("alice");val reply=reply(q,"Answer from the organizer");login("bob")
        val n=notices().single();assertEquals(reply,n.contentId)
        compose.setContent {GogetherTheme {GogetherApp(repo)}}
        compose.onNodeWithTag("my_events_unread",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("nav_my-events").performClick()
        compose.onNodeWithText("Notifications · 1 unread").assertExists()
        assertFalse(notices().single().isRead)
        compose.onNodeWithText("Notifications · 1 unread").performClick()
        compose.onNodeWithTag("notification_open_${n.id}").performClick();compose.waitForIdle()
        compose.waitUntil(5000){notices().single().isRead}
        compose.onNodeWithTag("social_item_$reply").assertIsDisplayed()
        compose.onNodeWithTag("my_events_unread",useUnmergedTree=true).assertDoesNotExist()
    }
    @Test fun markAllReadClearsBadgeAndPersistsAcrossGatewayRecreationAndAccounts() {
        val q=makeQuestion();login("alice");reply(q,"First answer");reply(q,"Another answer");login("bob")
        compose.setContent {GogetherTheme {GogetherApp(repo)}}
        compose.onNodeWithTag("nav_my-events").performClick();compose.onNodeWithText("Notifications · 2 unread").performClick()
        compose.onNodeWithTag("read_all").performClick();compose.waitForIdle();compose.waitUntil(5000){notices().all {it.isRead}}
        compose.onNodeWithTag("my_events_unread",useUnmergedTree=true).assertDoesNotExist()
        assertEquals(0,RepositorySocialGateway(context,repo).projectNotifications(repo.state.value).unreadNotificationCount)
        login("alice");assertEquals(1,notices().count {!it.isRead});login("bob");assertEquals(0,notices().count {!it.isRead})
    }
    @Test fun anonymousPrivateReviewAndRepliesReachOrganizerWithoutIdentityLeak():Unit=runBlocking {
        val session=bridge.backend.history("bob").single().session
        reviews.saveFeedback(PrototypeFeedback(session.id,"bob",4,"Private review",time,FeedbackDetails(anonymous=true,visibility=FeedbackVisibility.ORGANIZER_ONLY)))
        val root=reviews.feedback(session.id,"bob")!!.contentId
        login("alice");val n=notices().single();assertEquals(NotificationType.REVIEW_POSTED,n.type);assertTrue(n.actorName!!.startsWith("Anonymous participant"));assertEquals("",n.actorAvatarUri)
        val hostReply=reply(root,"Thank you");login("bob");assertEquals(hostReply,notices().single().contentId)
        val answer=reply(hostReply,"More feedback");login("alice");assertEquals(2,notices().size);assertEquals(n.actorName,notices().single {it.contentId==answer}.actorName)
        login("carol");assertTrue(notices().isEmpty());assertTrue(runCatching {gateway.call("read",mapOf("rootId" to answer))}.isFailure)
        login("alice");gateway.markRead(n.id).getOrThrow();assertTrue(RepositorySocialGateway(context,repo).projectNotifications(repo.state.value).notifications.single {it.id==n.id}.isRead)
    }
    @Test fun blocksHideUnreadInteractionsAndDeletedNoticesShowSafePlaceholders():Unit=runBlocking {
        val q=makeQuestion();login("alice");val id=reply(q,"An answer");login("bob");val n=notices().single()
        gateway.call("blockUser",mapOf("targetUserId" to "alice","kind" to "PERSONAL"));assertTrue(notices().isEmpty())
        gateway.call("unblock",mapOf("blockId" to social.load().blocks.single().id));assertEquals(1,notices().count {!it.isRead})
        login("alice");gateway.call("delete",mapOf("contentId" to id));login("bob");assertTrue(notices().single().content.contains("deleted"));assertEquals("Activity conversation",notices().single().actorName)
        gateway.markRead(n.id).getOrThrow();assertTrue(notices().single().isRead)
        login("carol");assertTrue(gateway.markRead(n.id).isFailure)
    }
    @Test fun acknowledgedCloudReplyIsSuccessfulEvenWhenSnapshotRefreshFails():Unit=runBlocking {
        var calls=0
        val cloud=object:GogetherRepository by repo, com.gogether.app.core.data.cloud.CloudOperations {
            override val storage:com.google.firebase.storage.FirebaseStorage get()=error("Unused")
            override val sync=kotlinx.coroutines.flow.MutableStateFlow(com.gogether.app.core.data.cloud.CloudSyncState())
            override suspend fun call(action:String,payload:Map<String,Any?>,requestId:String?):Map<String,Any?> {
                calls++;assertEquals("socialMutate",action);return mapOf("id" to "committed-reply")
            }
            override suspend fun refresh() { throw java.io.IOException("Offline after commit") }
        }
        val result=RepositorySocialGateway(context,cloud).call("reply",mapOf("contentId" to "q","content" to "Saved"))
        assertEquals("committed-reply",result["id"]);assertEquals(1,calls)
    }
    @Test fun incomingReplyUpdatesBadgeWhileTheSameAccountStaysOnScreen() {
        val q=makeQuestion()
        compose.setContent {GogetherTheme {GogetherApp(repo)}}
        compose.onNodeWithTag("my_events_unread",useUnmergedTree=true).assertDoesNotExist()
        compose.runOnIdle {
            social.edit { saved ->
                val root=SocialContent(q,"event-1","bob","Question",time,"QUESTION")
                val engine=SocialEngine(saved,listOf(root),initial.users.associate {it.id to it.displayName},mapOf("event-1" to "alice"),{true})
                engine.mutate("alice","reply",mapOf("contentId" to q,"content" to "New reply while you are here"))
                engine.state to Unit
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("my_events_unread",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("nav_my-events").performClick()
        compose.onNodeWithText("Notifications · 1 unread").assertExists()
    }
    @Test fun deepLinkScrollsToReplyBeyondFirstScreen() {
        val q=makeQuestion();login("alice")
        val target=(1..18).map { reply(q,"Reply number $it") }.last()
        login("bob")
        compose.setContent {GogetherTheme {SocialThread(gateway,target,{})}}
        compose.waitForIdle()
        compose.onNodeWithTag("social_item_$target").assertIsDisplayed()
        compose.onNodeWithText("Reply number 18").assertIsDisplayed()
    }
    @Test fun snapshotVersionSixPreservesTargetsReadFlagsAndSafeAlias() {
        val n=Notification("id","bob","event-1",NotificationType.REVIEW_REPLIED,"Replied",time,true,contentId="reply",rootId="review",actorName="Anonymous participant 02")
        val round=SnapshotJson.decode(SnapshotJson.encode(initial.copy(notifications=listOf(n))))
        assertEquals(n,round.notifications.single())
        val old=org.json.JSONObject(SnapshotJson.encode(initial)).put("version",5).apply {remove("eventRatings")}.toString()
        assertEquals(initial,SnapshotJson.decode(old))
    }
}

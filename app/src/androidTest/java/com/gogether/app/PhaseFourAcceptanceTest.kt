package com.gogether.app

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.test.core.app.ApplicationProvider
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.EventRatingBadge
import com.gogether.app.feature.social.*
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.feature.profile.ProfileHistoryScreen
import com.gogether.app.navigation.GogetherApp
import com.gogether.app.ui.theme.GogetherTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class PhaseFourAcceptanceTest {
    @get:Rule val compose=createComposeRule()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val time=System.currentTimeMillis()
    private val social=LocalSocialStore(context)
    private val saved=social.load()
    private val prefs=context.getSharedPreferences("checkin_prototype_v1",Context.MODE_PRIVATE)
    private val savedPrefs=prefs.all.toMap()
    private val event=DemoData.create(time).events.first().copy(id="phase4-rating-event",organizerId="alice",createdAt=System.nanoTime(),startAt=time-7200000,endAt=time-1000,status=EventStatus.OPEN,title="History aggregate example")
    private val regs=listOf("alice","bob","carol","david","emma","finn").mapIndexed {i,u->Registration("phase4-$u",event.id,u,RegistrationStatus.CONFIRMED,time-10000000,time-10000000,i+1L)}
    private val initial=DemoData.create(time).copy(currentUserId="bob",events=listOf(event),registrations=regs,checkIns=regs.filter {it.userId!="finn"}.map {CheckIn("c-${it.userId}",event.id,it.userId,time-7000000,if(it.userId=="alice")CheckInStatus.HOST_CONFIRMED else CheckInStatus.QR_RECORDED,it.attendanceKey)},questions=emptyList(),notifications=emptyList(),feedback=emptyList())
    private val repo=FakeGogetherRepository(initial,{time})
    private val gateway=RepositorySocialGateway(context,repo)
    private val reviews=PreferencesPrototypeStore(context)
    private val bridge=RegistrationAttendanceBridge({repo.state.value},reviews,{time})
    @Before fun setup()=runBlocking {
        social.edit {SocialState() to Unit};prefs.edit().clear().commit()
        for((user,score,detail) in listOf(Triple("bob",5,FeedbackDetails(anonymous=true)),Triple("carol",3,FeedbackDetails()),Triple("alice",1,FeedbackDetails()),Triple("david",1,FeedbackDetails(visibility=FeedbackVisibility.ORGANIZER_ONLY)),Triple("emma",2,FeedbackDetails()))) {
            repo.login(user).getOrThrow();val session=bridge.prepareFeedback(user,event.id)
            bridge.backend.submitFeedback(user,session.id,score,"A rating test",detail)
        }
        gateway.call("delete",mapOf("contentId" to reviews.feedback(bridge.key(event),"emma")!!.contentId))
        // Simulates an old saved review whose author never checked in.
        reviews.saveFeedback(PrototypeFeedback(bridge.key(event),"finn",1,"Old unverified review",time))
        repo.login("bob").getOrThrow()
    }
    @After fun restore(){social.edit {saved to Unit};val e=prefs.edit().clear();savedPrefs.forEach {(k,v)->when(v){is String->e.putString(k,v);is Long->e.putLong(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Float->e.putFloat(k,v)}};e.commit()}
    private fun projected()=gateway.projectRatings(bridge.withAttendance(repo.state.value),time)
    private fun rating()=projected().eventRatings.getValue(event.id)
    private fun history() {compose.onNodeWithTag("nav_my-events").performClick();compose.onNodeWithTag("personal_tab_HISTORY").performClick()}
    @Test fun localTotalsAreIdenticalAcrossAccountsAndPersonalBlocks():Unit=runBlocking {
        for(user in listOf("alice","bob","carol","finn")){repo.login(user).getOrThrow();assertEquals(EventRatingSummary(8,2),rating())}
        repo.login("bob").getOrThrow();gateway.call("blockUser",mapOf("targetUserId" to "carol","kind" to "PERSONAL"))
        assertEquals(EventRatingSummary(8,2),rating())
        assertTrue(runCatching {gateway.call("read",mapOf("rootId" to reviews.feedback(bridge.key(event),"carol")!!.contentId))}.isFailure)
    }
    @Test fun myEventsHistoryShowsAggregateBesideTitleAndKeepsPersonalRatingSeparate() {
        compose.setContent {GogetherTheme {GogetherApp(repo)}};history()
        compose.onNodeWithTag("event_rating_${event.id}",useUnmergedTree=true).assertContentDescriptionEquals("4.0 out of 5, 2 reviews")
        compose.onNodeWithTag("participation_${event.id}").assertTextContains("Your rating: 5/5",substring=true)
    }
    @Test fun profilePreviewAndFullHistoryUseTheSameAggregate() {
        compose.setContent {GogetherTheme {GogetherApp(repo)}}
        compose.onNodeWithTag("nav_profile").performClick()
        compose.onNodeWithTag("event_rating_${event.id}",useUnmergedTree=true).assertContentDescriptionEquals("4.0 out of 5, 2 reviews")
        compose.onNodeWithTag("profile_history_more").performScrollTo().performClick();compose.waitForIdle()
        compose.onNodeWithTag("profile_history_list").assertExists()
        compose.onNodeWithTag("event_rating_${event.id}",useUnmergedTree=true).assertContentDescriptionEquals("4.0 out of 5, 2 reviews")
    }
    @Test fun deletingAndReplacingReviewRefreshesHistoryWithoutDoubleCounting() {
        compose.setContent {GogetherTheme {GogetherApp(repo)}};history()
        val old=reviews.feedback(bridge.key(event),"bob")!!.contentId
        runBlocking {gateway.call("delete",mapOf("contentId" to old))};compose.waitForIdle()
        compose.onNodeWithTag("event_rating_${event.id}",useUnmergedTree=true).assertContentDescriptionEquals("3.0 out of 5, 1 review")
        val session=bridge.prepareFeedback("bob",event.id);bridge.backend.submitFeedback("bob",session.id,2,"Replacement",FeedbackDetails(anonymous=true))
        compose.waitForIdle()
        compose.onNodeWithTag("event_rating_${event.id}",useUnmergedTree=true).assertContentDescriptionEquals("2.5 out of 5, 2 reviews")
        assertEquals(EventRatingSummary(5,2),RepositorySocialGateway(context,repo).projectRatings(bridge.withAttendance(repo.state.value),time).eventRatings[event.id])
        assertNotEquals(old,reviews.feedback(bridge.key(event),"bob")!!.contentId)
    }
    @Test fun emptyAndUnavailableScoresHaveDistinctLabelsAndNoFakeZeroRating() {
        compose.setContent {GogetherTheme {Column {EventRatingBadge(EventRatingSummary(),"empty");EventRatingBadge(null,"unknown")}}}
        compose.onNodeWithTag("event_rating_empty").assertContentDescriptionEquals("No reviews")
        compose.onNodeWithTag("event_rating_unknown").assertContentDescriptionEquals("Rating unavailable")
        compose.onNodeWithText("0.0").assertDoesNotExist()
    }
    @Test fun longHistoryTitleAndLargeFontKeepRatingInsideTheRow() {
        val state=projected().copy(events=listOf(event.copy(title="A very long activity title that needs to fit beside its rating")))
        compose.setContent {GogetherTheme {val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                Box(Modifier.width(280.dp)){ProfileHistoryScreen(state,time,{})}
            }
        }}
        val row=compose.onNodeWithTag("history_${event.id}").fetchSemanticsNode().boundsInRoot
        val badge=compose.onNodeWithTag("event_rating_${event.id}",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val title=compose.onNodeWithText("A very long activity title that needs to fit beside its rating",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(badge.right<=row.right);assertTrue(badge.left>=title.right);assertTrue(title.width>0)
    }
    @Test fun schemaSevenPersistsTotalsAndReadsSchemaSixWithoutInventingScores() {
        val current=projected();assertEquals(current,SnapshotJson.decode(SnapshotJson.encode(current)))
        val old=org.json.JSONObject(SnapshotJson.encode(current)).put("version",6).apply {remove("eventRatings")}
        val decoded=SnapshotJson.decode(old.toString());assertTrue(decoded.eventRatings.isEmpty())
        assertEquals(current.copy(eventRatings=emptyMap()),decoded)
        assertEquals(current.eventRatings,LocalSnapshotStore.decode(LocalSnapshotStore.encode(current)).eventRatings)
    }
}

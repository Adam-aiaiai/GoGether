package com.gogether.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.gogether.app.core.data.*
import com.gogether.app.core.data.cloud.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.cloud.CloudCheckInScreen
import com.gogether.app.ui.theme.GogetherTheme
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class CloudAttendanceUiTest {
    @get:Rule val compose=createComposeRule()
    private class Backend(private val clock:Long):CloudOperations {
        override val storage:FirebaseStorage get()=error("No image operation in this test")
        override val sync=MutableStateFlow(CloudSyncState())
        var fail=true
        var own:Map<String,Any?>?=null
        var submissions=0
        var last:Map<String,Any?>?=null
        override suspend fun refresh()=Unit
        override suspend fun call(action:String,payload:Map<String,Any?>,requestId:String?):Map<String,Any?> = when(action) {
            "eventInfo" -> mapOf("serverNow" to clock,"meeting" to null,"receipt" to null,"ownFeedback" to own)
            "attendance" -> mapOf("attendees" to listOf(mapOf("userId" to "bob","name" to "Bob cloud","checkedInAt" to clock)))
            "reviews" -> mapOf("reviews" to emptyList<Any>())
            "submitFeedback" -> {
                submissions++; last=payload
                if(fail)throw IllegalStateException("Server did not save this review. Try again.")
                own=payload+mapOf("createdAt" to clock,"id" to "review-id","own" to true)
                mapOf("feedback" to own)
            }
            else -> error("Unexpected cloud call: $action")
        }
    }
    private fun seed(actor:String):Pair<AppSnapshot,Event> {
        val now=System.currentTimeMillis();val base=DemoData.create(now)
        val event=base.event("event-history")!!.copy(id="cloud-ui-${UUID.randomUUID()}",organizerId="alice")
        val registrations=base.registrations.filter{it.eventId=="event-history"}.map{it.copy(eventId=event.id)}
        return base.copy(currentUserId=actor,events=listOf(event),registrations=registrations) to event
    }

    @Test fun endedOrganizerEntryShowsServerRosterDirectly() {
        val (state,event)=seed("alice");val repo=FakeGogetherRepository(state);val backend=Backend(System.currentTimeMillis())
        compose.setContent{GogetherTheme{CloudCheckInScreen(repo,backend,event.id,System.currentTimeMillis(),{},attendanceOnly=true)}}
        compose.onNodeWithTag("cloud_attendance_count").performScrollTo().assertTextEquals("1 of 1 checked in")
        compose.onNodeWithText("Bob cloud").performScrollTo().assertIsDisplayed()
        compose.reviewNode("cloud_submit_feedback").assertDoesNotExist()
    }

    @Test fun reviewFailureKeepsDraftThenSuccessfulRetryShowsCloudRecord() {
        val (state,event)=seed("bob");val repo=FakeGogetherRepository(state);val backend=Backend(System.currentTimeMillis())
        compose.setContent{GogetherTheme{CloudCheckInScreen(repo,backend,event.id,System.currentTimeMillis(),{})}}
        compose.onNodeWithTag("feedback_rating_5").performScrollTo().performClick()
        compose.onNodeWithTag("cloud_feedback_comment").performScrollTo().performTextReplacement("Cloud draft survives failure")
        closeSoftKeyboard()
        compose.reviewNode("feedback_positive_comment").performTextInput("Friendly teammates.")
        closeSoftKeyboard()
        compose.reviewNode("feedback_improvement_comment").performTextInput("More warm-up time.")
        closeSoftKeyboard()
        compose.reviewNode("feedback_anonymous").performScrollTo().performClick()
        compose.reviewNode("feedback_visibility_ORGANIZER_ONLY").performClick()
        compose.reviewNode("cloud_submit_feedback").performClick()
        compose.waitUntil(5000){backend.submissions==1}
        compose.onNodeWithTag("cloud_feedback_comment").assertTextContains("Cloud draft survives failure")
        assertNull(backend.own)
        compose.runOnIdle{backend.fail=false}
        compose.reviewNode("cloud_submit_feedback").performClick()
        compose.waitUntil(5000){backend.own!=null}
        compose.onNodeWithTag("cloud_feedback_saved").performScrollTo().assertIsDisplayed()
        val details=backend.last!!["details"] as Map<*,*>
        assertEquals("Friendly teammates.",details["positiveComment"])
        assertEquals("More warm-up time.",details["improvementComment"])
        compose.onNodeWithText("Friendly teammates.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("More warm-up time.").performScrollTo().assertIsDisplayed()
        assertEquals(true,details["anonymous"])
        assertEquals("ORGANIZER_ONLY",details["visibility"])
        assertEquals(2,backend.submissions)
    }
}

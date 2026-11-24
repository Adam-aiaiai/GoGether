package com.gogether.app.feature.checkin.cloud

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.data.cloud.CloudOperations
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.feature.event.MeetingMapButton
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.*
import java.util.UUID

/** Real-network attendance path. The local prototype store is never read or written here. */
@Composable
fun CloudCheckInScreen(repository:GogetherRepository, backend:CloudOperations, eventId:String, now:Long,
                       back:()->Unit, attendanceOnly:Boolean=false) {
    val state by repository.state.collectAsStateWithLifecycle()
    val actor=state.currentUserId
    val event=state.event(eventId)
    if(actor==null || event==null) { Page { Text("The event or account has changed."); TextButton(onClick=back){Text("Back")} }; return }
    key(actor,eventId) { CloudEventContent(repository,backend,state,event,actor,now,back,attendanceOnly) }
}

@Composable
private fun CloudEventContent(repository:GogetherRepository, backend:CloudOperations, state:AppSnapshot,event:Event,
                              actor:String,now:Long,back:()->Unit,attendanceOnly:Boolean) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val organizer=event.organizerId==actor
    val pendingStore=remember { CloudCheckInPendingStore(context.applicationContext) }
    val pendingLoad=remember { runCatching { pendingStore.load(actor,event.id) } }
    var pending by remember { mutableStateOf(pendingLoad.getOrNull()) }
    val pendingError=pendingLoad.exceptionOrNull()?.message
    var information by remember { mutableStateOf<Map<String,Any?>>(emptyMap()) }
    var attendees by remember { mutableStateOf<List<Map<*,*>>>(emptyList()) }
    var reviews by remember { mutableStateOf<List<Map<*,*>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(if(attendanceOnly) "attendance" else if(now>=event.endAt) "reviews" else "checkin") }
    var permissionAction by rememberSaveable { mutableStateOf<String?>(null) }
    var showingCode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }
    var codeExpiresAt by remember { mutableLongStateOf(0) }
    var serverTime by remember { mutableLongStateOf(now) }
    var receivedAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var elapsedNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val effectiveNow=serverTime+(elapsedNow-receivedAt).coerceAtLeast(0)
    val closed=event.status!=EventStatus.OPEN || effectiveNow>=event.endAt
    val confirmed=state.registration(event.id,actor)?.status==RegistrationStatus.CONFIRMED
    val receipt=information.map("receipt")
    val ownReview=information.map("ownFeedback")
    val meeting=information.map("meeting")

    suspend fun refresh() {
        val next=backend.call("eventInfo",mapOf("eventId" to event.id))
        information=next
        serverTime=next.number("serverNow").takeIf{it>0}?:System.currentTimeMillis()
        receivedAt=SystemClock.elapsedRealtime()
        if(organizer) attendees=backend.call("attendance",mapOf("eventId" to event.id)).rows("attendees")
        reviews=backend.call("reviews",mapOf("eventId" to event.id)).rows("reviews")
        if(next.map("receipt")!=null && pending!=null) { pendingStore.clear(actor,event.id); pending=null }
        loading=false
    }
    fun perform(action:suspend ()->Unit) {
        if(busy)return
        busy=true; message=""
        scope.launch { try { action() } catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){message=error.message?:"Could not reach the server. Try again."} finally {busy=false} }
    }
    suspend fun resolvePending() {
        val attempt=pending?:return
        val result=backend.call("resolveCheckIn",mapOf("eventId" to event.id,"attemptId" to attempt.id))
        when(result.string("status")) {
            "SAVED" -> { pendingStore.clear(actor,event.id);pending=null;message="Check-in confirmed by the server.";refresh();backend.refresh() }
            "NOT_SUBMITTED" -> {pendingStore.clear(actor,event.id);pending=null;message="The previous attempt was not submitted. Scan again."}
            else -> message="The previous check-in is not confirmed yet. Check again or retry the same attempt."
        }
    }
    suspend fun send(attempt:CloudPendingCheckIn) {
        try {
            backend.call("submitCheckIn",mapOf("eventId" to event.id,"code" to attempt.code,"fix" to attempt.fix),attempt.id)
            pendingStore.clear(actor,event.id);pending=null
            message="Checked in. Your attendance is saved on the server."
            refresh();backend.refresh()
        } catch(error:FirebaseFunctionsException) {
            // Transport errors cannot tell whether the transaction committed. Keep its durable ID.
            if(error.code in setOf(FirebaseFunctionsException.Code.INVALID_ARGUMENT,FirebaseFunctionsException.Code.PERMISSION_DENIED,
                FirebaseFunctionsException.Code.FAILED_PRECONDITION,FirebaseFunctionsException.Code.NOT_FOUND,
                FirebaseFunctionsException.Code.ALREADY_EXISTS)) { pendingStore.clear(actor,event.id);pending=null }
            throw error
        }
    }
    val scanner=rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { raw -> perform {
            check(repository.state.value.currentUserId==actor){"The account changed. Reopen this event."}
            check(pending==null){"Resolve the previous check-in first."}
            require(raw.startsWith("GOGETHER-CLOUD-1|")){"Scan the cloud check-in code shown by this event's organizer."}
            val fix=AndroidPositionSource(context).locate().cloudPayload()
            val attempt=CloudPendingCheckIn(UUID.randomUUID().toString(),raw,fix)
            pendingStore.save(actor,event.id,attempt);pending=attempt
            send(attempt)
        } }
    }
    fun permissionGranted(permission:String)=ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED
    fun runPermissionAction(action:String) {
        when(action) {
            "scan" -> scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan the organizer's check-in code").setBeepEnabled(false).setOrientationLocked(false))
            "bind" -> perform {
                val fix=AndroidPositionSource(context).locate().cloudPayload()
                backend.call("bindMeeting",mapOf("eventId" to event.id,"fix" to fix))
                message="Meeting point saved on the server.";refresh();backend.refresh()
            }
            "code" -> showingCode=true
        }
    }
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action=permissionAction;permissionAction=null
        if(action!=null && permissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            (action!="scan" || permissionGranted(Manifest.permission.CAMERA)))runPermissionAction(action)
        else message="Allow precise location; scanning also needs camera permission."
    }
    fun request(action:String) {
        if(permissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) && (action!="scan" || permissionGranted(Manifest.permission.CAMERA)))runPermissionAction(action)
        else {permissionAction=action;permissions.launch(if(action=="scan")arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.CAMERA)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
    }
    LaunchedEffect(actor,event.id) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try { if(pending!=null)resolvePending() }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){message="Check-in result is not confirmed. Reconnect and check the saved attempt."}
            while(isActive) {
                if(!busy)try{refresh()}catch(cancelled:CancellationException){throw cancelled}catch(error:Exception){loading=false;message=error.message?:"Could not refresh. Showing the last received data."}
                delay(5_000)
            }
        }
    }
    LaunchedEffect(showingCode,tab) {
        if(showingCode && tab=="checkin") lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while(isActive) {
                    try {
                        val result=backend.call("issueCheckInCode",mapOf("eventId" to event.id,"fix" to AndroidPositionSource(context).locate().cloudPayload()))
                        code=result.string("code");codeExpiresAt=result.number("expiresAt")
                        serverTime=result.number("serverNow");receivedAt=SystemClock.elapsedRealtime()
                    }catch(cancelled:CancellationException){throw cancelled}catch(error:Exception){code=null;message=error.message?:"Could not refresh code."}
                    delay(10_000)
                }
            } finally { code=null }
        }
    }
    LaunchedEffect(Unit){while(isActive){delay(1_000);elapsedNow=SystemClock.elapsedRealtime()}}
    val window=(context as? Activity)?.window
    DisposableEffect(code,window){if(code!=null)window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose{window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}}

    Page {
        TextButton(onClick=back){Text("< Back")}
        SectionTitle(event.title,"Meeting, attendance and reviews")
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(tab=="checkin",{tab="checkin"},label={Text("Check-in")},modifier=Modifier.testTag("cloud_tab_checkin"))
            if(organizer)FilterChip(tab=="attendance",{tab="attendance";showingCode=false},label={Text("Attendance")},modifier=Modifier.testTag("cloud_tab_attendance"))
            FilterChip(tab=="reviews",{tab="reviews";showingCode=false},label={Text("Reviews")},modifier=Modifier.testTag("cloud_tab_reviews"))
        }
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank())Text(message,Modifier.testTag("cloud_attendance_message"))
        if(pendingError!=null) InfoCard("Saved check-in could not be read", "Reopen the app or contact your organizer to verify attendance. A new scan is blocked to avoid duplicate submission.")
        if(pending!=null) {
            InfoCard("Check-in awaiting confirmation","Your request has been saved. Check its result before scanning another code.")
            TextButton(onClick={perform{resolvePending()}},enabled=!busy,modifier=Modifier.testTag("cloud_resolve_checkin")){Text("Check previous result")}
            TextButton(onClick={pending?.let{perform{send(it)}}},enabled=!busy,modifier=Modifier.testTag("cloud_retry_checkin")){Text("Retry saved attempt")}
        }
        when(tab) {
            "attendance" -> {
                if(!organizer)Text("Only the organizer can view attendance.")
                else {
                    Text("${attendees.count{it.number("checkedInAt")>0}} / ${attendees.size} checked in",Modifier.testTag("cloud_attendance_count"))
                    var search by rememberSaveable{mutableStateOf("")}
                    OutlinedTextField(search,{search=it},label={Text("Search participants")},modifier=Modifier.fillMaxWidth())
                    attendees.filter{it.string("name").contains(search,true)}.forEach { person ->
                        InfoCard(person.string("name"),person.number("checkedInAt").takeIf{it>0}?.let{"Checked in · ${displayTime(it)}"}?:"Not checked in")
                    }
                }
            }
            "reviews" -> CloudFeedbackContent(backend,event,actor,confirmed,closed,ownReview,reviews,busy,
                {action->perform{action();refresh();backend.refresh()}},{message=it})
            else -> {
                InfoCard("Meeting point","${displayTime(event.meetingAt?:event.startAt)}\n${event.city} · ${event.meetingLocation?:event.location}\n${event.meetingNotes}")
                MeetingMapButton("${event.city} ${event.meetingLocation?:event.location}")
                receipt?.let{InfoCard("Checked in",displayTime(it.number("checkedInAt")),Modifier.testTag("cloud_checkin_receipt"))}
                if(closed)Text(if(event.status==EventStatus.CANCELLED)"This event is cancelled." else "This event has ended. You can view its attendance and reviews.")
                else if(meeting==null) {
                    Text(if(organizer)"Confirm the GPS meeting point when you arrive." else "The organizer has not confirmed the meeting point yet.")
                    if(organizer)Button(onClick={request("bind")},enabled=!busy,modifier=Modifier.testTag("cloud_bind_meeting")){Text("Set meeting point here")}
                } else {
                    Text("Check-in opens 30 minutes before the start and closes 30 minutes after it. Be within 200 m of the meeting point.")
                    if(organizer){
                        OutlinedButton(onClick={showingCode=false;code=null;request("bind")},enabled=!busy) { Text("Update meeting point here") }
                        if(code!=null && effectiveNow<codeExpiresAt) {
                            val qr=remember(code){BarcodeEncoder().createBitmap(com.gogether.app.core.ui.CheckInQr.matrix(requireNotNull(code))).asImageBitmap()}
                            Image(qr,"Server-issued check-in QR code",Modifier.fillMaxWidth().height(280.dp).background(Color.White).testTag("cloud_qr"))
                            Text("Refreshes automatically · ${(codeExpiresAt-effectiveNow).coerceAtLeast(0)/1000}s remaining")
                        }
                        Button(onClick={request("code")},enabled=!busy && !showingCode,modifier=Modifier.testTag("cloud_issue_code")){Text(if(showingCode)"Refreshing code…" else "Show check-in code")}
                        if(showingCode)TextButton(onClick={showingCode=false;code=null}){Text("Hide code")}
                    }
                    if(confirmed && receipt==null)Button(onClick={request("scan")},enabled=!busy && pending==null && pendingError==null,
                        modifier=Modifier.testTag("cloud_scan_checkin")){Text(if(busy)"Submitting…" else "Scan to check in")}
                    else if(!confirmed)Text("Only confirmed participants can check in. A waitlist place is not enough.")
                }
            }
        }
        TextButton(onClick={perform{refresh();message="Updated from the server."}},enabled=!busy){Text("Refresh")}
    }
}

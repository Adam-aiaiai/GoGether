package com.gogether.app.feature.checkin.cloud

import com.gogether.app.core.ui.AppBackButton
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.google.firebase.functions.FirebaseFunctionsException
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
    if(actor==null || event==null) { Page { Text("The event or account has changed."); AppBackButton(back) }; return }
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
    var rosterLoaded by remember { mutableStateOf(false) }
    var rosterLoading by remember { mutableStateOf(false) }
    var rosterError by remember { mutableStateOf("") }
    var rosterUpdatedAt by remember { mutableStateOf<Long?>(null) }
    var organizerReviewReturn by rememberSaveable { mutableStateOf<String?>(null) }
    var reviews by remember { mutableStateOf<List<Map<*,*>>>(emptyList()) }
    var ratingSummary by remember { mutableStateOf<com.gogether.app.core.model.EventRatingSummary?>(null) }
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
    val receipt=currentCloudReceipt(information.map("receipt"),state.registration(event.id,actor),event.status)
    val ownReview=information.map("ownFeedback")
    val checkInReady=information["checkInReady"]==true
    var confirmArrival by remember { mutableStateOf(false) }
    var arrivalConfirmed by remember { mutableStateOf(false) }

    suspend fun refreshAttendance() {
        if (!organizer || rosterLoading) return
        rosterLoading = true
        try {
            attendees = backend.call("attendance", mapOf("eventId" to event.id)).rows("attendees")
            rosterLoaded = true; rosterError = ""; rosterUpdatedAt = System.currentTimeMillis()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { rosterError = error.message ?: "Could not refresh attendance. Try again." }
        finally { rosterLoading = false }
    }
    suspend fun refresh() {
        val next=backend.call("eventInfo",mapOf("eventId" to event.id))
        information=next
        serverTime=next.number("serverNow").takeIf{it>0}?:System.currentTimeMillis()
        receivedAt=SystemClock.elapsedRealtime()
        if(organizer) refreshAttendance()
        val reviewResponse=backend.call("reviews",mapOf("eventId" to event.id))
        reviews=reviewResponse.rows("reviews")
        ratingSummary=reviewResponse["ratingSummary"]?.let { com.gogether.app.core.data.cloud.CloudDto.ratingSummary(com.gogether.app.core.data.cloud.CloudDto.objectValue(it)) }
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
        val result=backend.call("resolveCheckIn",mapOf("eventId" to event.id,"attemptId" to attempt.id,"registrationKey" to attempt.registrationKey))
        when(result.string("status")) {
            "SAVED" -> { pendingStore.clear(actor,event.id);pending=null;message="Check-in confirmed by the server.";refresh();backend.refresh() }
            "NOT_SUBMITTED" -> {pendingStore.clear(actor,event.id);pending=null;message="The previous attempt was not submitted. Scan again."}
            else -> message="The previous check-in is not confirmed yet. Check again or retry the same attempt."
        }
    }
    suspend fun send(attempt:CloudPendingCheckIn) {
        try {
            val current = repository.state.value.registration(event.id,actor)
            if (attempt.registrationKey.isBlank() || current?.status != RegistrationStatus.CONFIRMED || attempt.registrationKey != current.attendanceKey) {
                pendingStore.clear(actor,event.id);pending=null
                throw IllegalArgumentException("Your registration changed. Scan again.")
            }
            backend.call("submitCheckIn",mapOf("eventId" to event.id,"code" to attempt.code,"registrationKey" to attempt.registrationKey),attempt.id)
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
    var scanRegistrationKey by remember { mutableStateOf<String?>(null) }
    val scanner=rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { raw -> perform {
            check(repository.state.value.currentUserId==actor){"The account changed. Reopen this event."}
            check(pending==null){"Resolve the previous check-in first."}
            require(raw.startsWith("GOGETHER-CLOUD-2|")){"Scan the cloud check-in code shown by this event's organizer."}
            val registration = requireNotNull(repository.state.value.registration(event.id,actor)) { "Register for this event first." }
            require(scanRegistrationKey == registration.attendanceKey) { "Your registration changed while scanning. Scan again." }
            val attempt=CloudPendingCheckIn(UUID.randomUUID().toString(),raw,registration.attendanceKey)
            pendingStore.save(actor,event.id,attempt);pending=attempt
            send(attempt)
        } }
    }
    fun permissionGranted(permission:String)=ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED
    fun runPermissionAction(action:String) {
        when(action) {
            "scan" -> scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan the organizer's check-in code").setBeepEnabled(false).setOrientationLocked(false))
            "code" -> { if (receipt == null) confirmArrival=true else showingCode=true }
        }
    }
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action=permissionAction;permissionAction=null
        if(action!=null && (action!="scan" || permissionGranted(Manifest.permission.CAMERA)))runPermissionAction(action)
        else message="Allow camera access to scan the organizer's code."
    }
    fun request(action:String) {
        if(action=="scan") scanRegistrationKey=repository.state.value.registration(event.id,actor)?.attendanceKey
        if(action!="scan" || permissionGranted(Manifest.permission.CAMERA))runPermissionAction(action)
        else {permissionAction=action;permissions.launch(arrayOf(Manifest.permission.CAMERA))}
    }
    if(confirmArrival) CheckInActionDialog("Confirm your arrival?",
        "Your arrival will be recorded when the check-in code is successfully displayed.", "Confirm and show code",
        {confirmArrival=false}, {confirmArrival=false;arrivalConfirmed=true;showingCode=true})
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
                        val result=backend.call("issueCheckInCode",mapOf("eventId" to event.id,"confirmArrival" to arrivalConfirmed))
                        code=result.string("code");codeExpiresAt=result.number("expiresAt")
                        information=information+mapOf("receipt" to result["receipt"],"checkInReady" to true)
                        backend.refresh()
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

    BackHandler(enabled = organizer && ((tab == "reviews" && organizerReviewReturn != null) || (tab == "attendance" && !attendanceOnly))) {
        tab = if (tab == "reviews") organizerReviewReturn ?: "checkin" else "checkin"
    }
    if (tab == "reviews") {
        CloudFeedbackContent(backend,event,actor,confirmed && receipt != null,closed,ownReview,reviews,busy,
            {action->perform{action();refresh();backend.refresh()}},{message=it},
            organizerName=state.users.find { it.id == event.organizerId }?.displayName ?: "Organizer",
            back={if(organizer && organizerReviewReturn != null) tab = organizerReviewReturn!! else back()},statusMessage=message,loading=loading,replacesReviewId=information.string("replacesReviewId"),ratingSummary=ratingSummary)
        return
    }

    if (organizer) {
        val people = remember(attendees) { attendancePeople(attendees) }
        if (tab == "attendance") {
            CloudOrganizerAttendance(event, people, rosterLoaded, rosterLoading, rosterError, rosterUpdatedAt,
                onRefresh = { scope.launch { refreshAttendance() } },
                onBack = { if (attendanceOnly) back() else tab = "checkin" },
                onReviews = { organizerReviewReturn = "attendance"; tab = "reviews" })
        } else {
            CloudOrganizerCheckInContent(event, effectiveNow, checkInReady, code, codeExpiresAt, showingCode,
                loading, information.isNotEmpty(), busy, message, people, rosterLoaded, rosterLoading, rosterError,
                onBack = back, onRefresh = { perform { refresh(); message = "Updated from the server." } },
                onReviews = { organizerReviewReturn = "checkin"; tab = "reviews" }, onAttendance = { tab = "attendance" },
                onShowCode = { request("code") }, onHideCode = { showingCode = false; code = null },
                onConfirmArrival = { request("code") },
                extraMenuItems = { closeMenu ->
                    // Preserve existing recovery and organizer-as-participant actions without crowding the desk.
                    if (pendingError != null) DropdownMenuItem({
                        Text("Saved check-in could not be read. Reopen the app to verify attendance.")
                    }, {}, enabled = false)
                    if (pending != null) {
                        DropdownMenuItem({ Text("Check previous result") }, { closeMenu(); perform { resolvePending() } },
                            enabled = !busy, modifier = Modifier.testTag("cloud_resolve_checkin"))
                        DropdownMenuItem({ Text("Retry saved attempt") }, { closeMenu(); pending?.let { perform { send(it) } } },
                            enabled = !busy, modifier = Modifier.testTag("cloud_retry_checkin"))
                    }
                    if (receipt != null) DropdownMenuItem({ Text("Checked in · ${displayTime(receipt.number("checkedInAt"))}") }, {}, enabled = false)
                })
        }
        return
    }

    if (!organizer && tab == "checkin") {
        CloudParticipantCheckInContent(
            event = event,
            participantName = state.users.find { it.id == actor }?.displayName ?: "Participant",
            organizerName = state.users.find { it.id == event.organizerId }?.displayName ?: "the organizer",
            registration = state.registration(event.id, actor)?.status,
            now = effectiveNow,
            checkInReady = checkInReady,
            checkedInAt = receipt?.number("checkedInAt")?.takeIf { it > 0 },
            loading = loading,
            hasInformation = information.isNotEmpty(),
            busy = busy,
            pending = pending != null,
            unreadableAttempt = pendingError != null,
            message = message,
            onBack = back,
            onRefresh = { perform { refresh(); message = "Updated from the server." } },
            onReviews = { tab = "reviews" },
            onScan = { request("scan") },
            onResolve = { perform { resolvePending() } },
            onRetryAttempt = { pending?.let { perform { send(it) } } },
        )
        return
    }

    // A participant can reach this route through an old/deep attendance link.
    Column(Modifier.fillMaxSize()) {
        AppPageHeader("Attendance", onBack = back)
        Page(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.titleLarge)
            Text("Only the organizer can view attendance.")
            TextButton({ tab = "checkin" }, modifier = Modifier.testTag("cloud_tab_checkin")) { Text("Check-in") }
            TextButton({ tab = "reviews" }, modifier = Modifier.testTag("cloud_tab_reviews")) { Text("Reviews") }
        }
    }
}

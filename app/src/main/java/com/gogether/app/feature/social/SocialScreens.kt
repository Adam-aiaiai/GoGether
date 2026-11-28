package com.gogether.app.feature.social

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gogether.app.core.model.*
import com.gogether.app.core.ui.*
import kotlinx.coroutines.launch

val LocalSocialRevision=staticCompositionLocalOf { 0 }
val LocalSocialGateway=staticCompositionLocalOf<SocialGateway?> { null }
@Suppress("UNCHECKED_CAST")
internal fun Map<String,Any?>.rows(key:String)=this[key] as? List<Map<String,Any?>> ?: emptyList()

@Composable
fun SocialContentActions(contentId:String, changed:()->Unit = {}) {
    val gateway=LocalSocialGateway.current ?: return
    var open by remember(contentId) { mutableStateOf(false) }
    TextButton({open=true},Modifier.testTag("social_open_$contentId")) { Text("Replies & actions") }
    if(open) Dialog({open=false;changed()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) { SocialThread(gateway,contentId,{open=false;changed()}) }
    }
}

@Composable
fun SocialThread(gateway:SocialGateway,contentId:String,back:()->Unit) {
    var items by remember(contentId) { mutableStateOf<List<Map<String,Any?>>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf<Pair<String,String>?>(null) }
    var draft by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Pair<String,String>?>(null) }
    val scope=rememberCoroutineScope()
    fun perform(action:String,id:String,extra:Map<String,Any?> = emptyMap()) { scope.launch {
        busy=true;error=""
        runCatching { gateway.call(action,mapOf("contentId" to id)+extra) }.onSuccess { editor=null;draft="";revision++ }.onFailure { error=it.message ?: "Try again." };busy=false
    } }
    val externalRevision=LocalSocialRevision.current
    val listState=rememberLazyListState()
    var located by remember(contentId) { mutableStateOf(false) }
    ObserveSocialChanges { revision++ }
    LaunchedEffect(contentId,revision,externalRevision) {
        busy=true
        runCatching { gateway.call("read",mapOf("rootId" to contentId)) }.onSuccess { items=it.rows("items");error="" }.onFailure { items=emptyList();error=it.message ?: "Content unavailable." };busy=false
    }
    LaunchedEffect(items,contentId) {
        val index=items.indexOfFirst { it["id"]==contentId }
        if(!located && index>=0) { listState.scrollToItem(index);located=true }
    }
    BackHandler(onBack=back)
    Column(Modifier.fillMaxSize().testTag("social_thread")) {
        AppPageHeader("Conversation",onBack=back)
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank()) Text(error,Modifier.padding(16.dp).testTag("social_error"),color=MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f).testTag("social_thread_list"),state=listState,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            items(items,key={it["id"].toString()}) { item ->
                val id=item["id"].toString();val deleted=item["deleted"]==true
                Column(Modifier.fillMaxWidth().background(if(id==contentId) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent).padding(start=if(item["kind"]=="REPLY") 18.dp else 0.dp).testTag("social_item_$id")) {
                    Text(item["authorName"].toString()+if(item["organizer"]==true) " · Organizer" else "",style=MaterialTheme.typography.titleSmall)
                    if(item["kind"]=="REPLY") Text("Reply to ${item["targetName"]}",style=MaterialTheme.typography.labelSmall)
                    if(item["private"]==true) Text("Only the reviewer and organizer can view this thread",style=MaterialTheme.typography.labelSmall)
                    Text(item["content"].toString(),Modifier.padding(vertical=8.dp))
                    if(!deleted) {
                        Row {
                            if(item["canReply"]==true) TextButton({editor="reply" to id;draft=""},enabled=!busy,modifier=Modifier.testTag("social_reply_$id")) { Text("Reply") }
                            if(item["kind"]=="REVIEW") TextButton({perform("like",id,mapOf("liked" to (item["liked"]!=true)))},enabled=!busy,modifier=Modifier.testTag("social_like_$id")) { Text("${if(item["liked"]==true) "Unlike" else "Like"} (${item["likes"]})") }
                            TextButton({if(item["own"]==true)confirm="delete" to id else {editor="report" to id;draft=""}},enabled=!busy,modifier=Modifier.testTag("social_${if(item["own"]==true) "delete" else "report"}_$id")) {Text(if(item["own"]==true) "Delete" else "Report")}
                        }
                        if(item["own"]!=true) TextButton({confirm="blockUser" to id},enabled=!busy,modifier=Modifier.testTag("social_block_$id")) {Text("Block this author")}
                    }
                    HorizontalDivider()
                }
            }
        }
        editor?.let { (action,id) ->
            Column(Modifier.padding(16.dp)) {
                AppCommentField(draft,{draft=it},label={Text(if(action=="reply") "Your reply" else "Report reason")},modifier=Modifier.fillMaxWidth().testTag("social_input"),enabled=!busy)
                Row { TextButton({perform(action,id,mapOf((if(action=="reply") "content" else "reason") to draft))},enabled=!busy && draft.trim().isNotEmpty(),modifier=Modifier.testTag("social_send")) {Text(if(action=="reply") "Send reply" else "Submit report")};TextButton({editor=null}){Text("Cancel")} }
            }
        }
    }
    confirm?.let { (action,id) -> AlertDialog(onDismissRequest={confirm=null},title={Text(if(action=="delete") "Delete your content?" else "Block this author?")},
        text={Text(if(action=="delete") "A deleted placeholder keeps existing replies readable. A deleted review no longer contributes a rating." else "Their content will be hidden and direct interactions between you will be restricted. Existing registrations stay unchanged.")},
        confirmButton={TextButton({confirm=null;perform(action,id,if(action=="blockUser") mapOf("kind" to "PERSONAL") else emptyMap())},modifier=Modifier.testTag("social_confirm")){Text("Confirm")}},dismissButton={TextButton({confirm=null}){Text("Cancel")}}) }
}

@Composable
fun SocialQuestionsScreen(event:Event,state:AppSnapshot,now:Long,busy:Boolean,ask:(String,()->Unit)->Unit) {
    val gateway=LocalSocialGateway.current ?: return
    var records by remember(event.id,state.currentUserId) { mutableStateOf<List<Map<String,Any?>>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    var draft by androidx.compose.runtime.saveable.rememberSaveable(event.id,state.currentUserId) { mutableStateOf("") }
    var openedThread by androidx.compose.runtime.saveable.rememberSaveable(event.id,state.currentUserId) { mutableStateOf<String?>(null) }
    val pager=androidx.compose.foundation.pager.rememberPagerState(pageCount={2})
    val scope=rememberCoroutineScope()
    val open=event.statusAt(now)==EventStatus.OPEN
    ObserveSocialChanges { revision++ }
    LaunchedEffect(state.questions,revision,event.id,state.currentUserId) {
        runCatching {gateway.call("read",mapOf("eventId" to event.id))}.onSuccess {records=it.rows("items").filter {r->r["kind"]=="QUESTION"};error=""}.onFailure {error=it.message.orEmpty()}
    }
    androidx.compose.foundation.pager.HorizontalPager(pager,Modifier.fillMaxSize().testTag("questions_pager"),verticalAlignment=androidx.compose.ui.Alignment.Top) { page ->
        Page {
            Text(event.title,style=MaterialTheme.typography.titleMedium)
            Text("Questions are public. The author and organizer can continue the conversation.",style=MaterialTheme.typography.bodySmall)
            if(!open) Text("Questions closed",style=MaterialTheme.typography.titleSmall)
            else if(state.currentUserId!=event.organizerId) {
                AppCommentField(draft,{draft=it},enabled=!busy,label={Text("Your question")},isError=draft.length>100,modifier=Modifier.fillMaxWidth().testTag("question_input"))
                PageActionButton({ask(draft){draft="";revision++;scope.launch {pager.scrollToPage(0)}}},enabled=!busy && draft.trim().length in 1..100,modifier=Modifier.testTag("post_question")) {Text(if(busy) "Sending…" else "Ask organizer")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                AppTextTab("All (${records.size})",pager.currentPage==0,{scope.launch {pager.animateScrollToPage(0)}},Modifier.testTag("questions_all"))
                AppTextTab("Unanswered (${records.count {it["latestAnswer"]==null && it["deleted"]!=true}})",pager.currentPage==1,{scope.launch {pager.animateScrollToPage(1)}},Modifier.testTag("questions_unanswered"))
            }
            if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
            val shown=records.filter {page==0 || (it["latestAnswer"]==null && it["deleted"]!=true)}
            if(shown.isEmpty()) Text(if(page==1) "No unanswered questions" else "No questions to show")
            shown.forEach { item ->
                val id=item["id"].toString()
                val author=state.questions.find {it.id==id}?.authorId?.let {state.users.find {u->u.id==it}}
                @Suppress("UNCHECKED_CAST") val answer=item["latestAnswer"] as? Map<String,Any?>
                Column(Modifier.fillMaxWidth().testTag("question_thread_$id")) {
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        com.gogether.app.feature.profile.ParticipantAvatar(item["authorName"].toString(),author?.avatarUri.orEmpty(),Modifier.size(38.dp))
                        Column(Modifier.weight(1f).testTag("question_$id")) {
                            Text(item["authorName"].toString(),style=MaterialTheme.typography.titleSmall)
                            Text(item["content"].toString())
                        }
                    }
                    if(answer!=null) Column(Modifier.padding(start=48.dp,top=10.dp).testTag("answer_$id")) {
                        Text("${answer["authorName"]} · Organizer",style=MaterialTheme.typography.labelSmall)
                        Text(answer["content"].toString(),style=MaterialTheme.typography.bodySmall)
                    }
                    TextButton({openedThread=id},Modifier.testTag("social_open_$id")) { Text("Replies & actions") }
                    HorizontalDivider()
                }
            }
        }
    }
    // An answer can remove its question from the Unanswered page. Keep the open
    // conversation outside that filtered row so it does not disappear mid-reply.
    openedThread?.let { id ->
        fun closeThread() { openedThread=null;revision++ }
        Dialog(::closeThread,properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.fillMaxSize()) { SocialThread(gateway,id,::closeThread) }
        }
    }
}

@Composable
fun SocialSettingsScreen(state:AppSnapshot,back:()->Unit) {
    val gateway=LocalSocialGateway.current ?: return
    var data by remember {mutableStateOf<Map<String,Any?>>(emptyMap())}
    var revision by remember {mutableIntStateOf(0)}
    var error by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var target by remember {mutableStateOf<String?>(null)}
    var kind by remember {mutableStateOf("PERSONAL")}
    var handling by remember {mutableStateOf<Pair<String,String>?>(null)}
    var outcome by remember {mutableStateOf("")}
    val scope=rememberCoroutineScope()
    fun perform(action:String,args:Map<String,Any?>) {scope.launch {busy=true;runCatching {gateway.call(action,args)}.onSuccess {revision++;error="";handling=null;outcome=""}.onFailure {error=it.message.orEmpty()};busy=false}}
    LaunchedEffect(revision) {runCatching {gateway.call("read")}.onSuccess {data=it}.onFailure {error=it.message.orEmpty()}}
    Page {
        AppPageHeader("Privacy & reports",onBack=back)
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        Text("Blacklists",style=MaterialTheme.typography.titleMedium)
        Text("Personal blocks restrict content and interactions. Registration restrictions apply to your events and preserve existing places.",style=MaterialTheme.typography.bodySmall)
        Row {FilterChip(kind=="PERSONAL",{kind="PERSONAL"},{Text("Personal")});Spacer(Modifier.width(8.dp));if(state.events.any {it.organizerId==state.currentUserId})FilterChip(kind=="REGISTRATION",{kind="REGISTRATION"},{Text("Registration")})}
        var menu by remember {mutableStateOf(false)}
        Box {TextButton({menu=true}){Text(target?.let {state.userName(it)} ?: "Choose user")};DropdownMenu(menu,{menu=false}) {state.users.filter {it.id!=state.currentUserId}.forEach {user->DropdownMenuItem({Text(user.displayName)},{target=user.id;menu=false})}}}
        TextButton({perform("blockUser",mapOf("targetUserId" to target,"kind" to kind))},enabled=target!=null && !busy,modifier=Modifier.testTag("add_block")){Text("Add restriction")}
        data.rows("blocks").forEach {b->Row(Modifier.fillMaxWidth()){Text("${b["label"]} · ${b["kind"]}",Modifier.weight(1f));TextButton({perform("unblock",mapOf("blockId" to b["id"]))},enabled=!busy){Text("Unblock")}}}
        HorizontalDivider()
        Text(if(data["maintainer"]==true) "Maintainer report queue" else "My reports",style=MaterialTheme.typography.titleMedium)
        if(data.rows("reports").isEmpty()) Text("No reports")
        data.rows("reports").forEach {r->Column(Modifier.testTag("report_${r["id"]}")) {
            if(data["maintainer"]==true)Text("${r["authorName"]}: ${r["content"]}")
            Text("Reason: ${r["reason"]}");Text("${r["status"]} ${r["outcome"]}")
            if(data["maintainer"]==true && r["status"]=="OPEN")Row {TextButton({handling=r["id"].toString() to "DISMISSED"}){Text("Dismiss")};TextButton({handling=r["id"].toString() to "REMOVED"}){Text("Remove content")}}
        }}
    }
    handling?.let {(id,resolution)->AlertDialog(onDismissRequest={handling=null},title={Text("Record the outcome")},text={AppCommentField(outcome,{outcome=it},label={Text("Reason for decision")},modifier=Modifier.testTag("report_outcome"))},confirmButton={TextButton({perform("resolveReport",mapOf("reportId" to id,"resolution" to resolution,"outcome" to outcome))},enabled=outcome.trim().isNotEmpty() && !busy){Text("Save decision")}},dismissButton={TextButton({handling=null}){Text("Cancel")}})}
}

@Composable
fun ObserveSocialChanges(changed:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val callback by rememberUpdatedState(changed)
    DisposableEffect(context) {
        val prefs=context.getSharedPreferences("social_v1",android.content.Context.MODE_PRIVATE)
        val handler=android.os.Handler(android.os.Looper.getMainLooper())
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> handler.post { callback() } }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
}

/** Removed roots remain discoverable so existing replies do not become orphaned. */
@Composable
fun DeletedReviewThreads(eventId:String) {
    val gateway=LocalSocialGateway.current ?: return
    var records by remember(eventId) { mutableStateOf<List<Map<String,Any?>>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    ObserveSocialChanges { revision++ }
    LaunchedEffect(eventId,revision) { runCatching {gateway.call("read",mapOf("eventId" to eventId))}.onSuccess {records=it.rows("items").filter {r->r["kind"]=="REVIEW" && r["deleted"]==true}} }
    records.forEach {r->Column {Text("${r["authorName"]} · Content deleted",style=MaterialTheme.typography.bodySmall);SocialContentActions(r["id"].toString()){revision++}}}
}

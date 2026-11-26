package com.gogether.app.feature.checkin.cloud

import com.gogether.app.core.ui.PageActionButton
import com.gogether.app.core.ui.AppPageHeader
import com.gogether.app.core.ui.Page
import androidx.compose.runtime.saveable.rememberSaveable
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.gogether.app.core.data.cloud.CloudOperations
import com.gogether.app.core.model.Event
import com.gogether.app.core.model.EventStatus
import com.gogether.app.core.ui.InfoCard
import com.gogether.app.core.ui.displayTime
import com.gogether.app.feature.checkin.prototype.*
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
internal fun CloudFeedbackContent(backend:CloudOperations,event:Event,actor:String,confirmed:Boolean,ended:Boolean,
    own:Map<*,*>?,reviews:List<Map<*,*>>,busy:Boolean,perform:(suspend ()->Unit)->Unit,message:(String)->Unit,
    organizerName:String,back:()->Unit,statusMessage:String = "",loading:Boolean = false) {
    val context=LocalContext.current.applicationContext
    val drafts=remember{CloudFeedbackDraftStore(context)}
    val photos=remember{FeedbackPhotoStore(context)}
    val initial=remember(actor,event.id){runCatching{drafts.load(actor,event.id)}}
    var draft by remember(actor,event.id){mutableStateOf(initial.getOrDefault(FeedbackDraft()))}
    var draftError by remember(actor,event.id){mutableStateOf(initial.exceptionOrNull()?.message.orEmpty())}
    var viewingReviews by rememberSaveable(actor,event.id){mutableStateOf(false)}
    fun update(value:FeedbackDraft){
        draft=value
        draftError=runCatching{drafts.save(actor,event.id,value)}.exceptionOrNull()?.message.orEmpty()
    }
    fun detail(change:(FeedbackDetails)->FeedbackDetails)=update(draft.copy(details=change(draft.details)))
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        if(uris.isNotEmpty())perform {
            val added=photos.importUris(actor,event.id,uris,3-draft.details.photos.size)
            detail{it.copy(photos=it.photos+added)}
        }
    }
    LaunchedEffect(own){if(own!=null)runCatching{drafts.clear(actor,event.id)}}
    val canReview=own==null && event.status!=EventStatus.CANCELLED && ended && confirmed
    if(canReview && !viewingReviews && !loading) {
        FeedbackEditor("$actor/${event.id}", ReviewEventSummary(event.title,organizerName,event.startAt,event.imageUri,event.category.symbol),
            draft,busy,draftError,statusMessage,::update,{update(draft)},submit={perform {
            val submitted=draft
            require(submitted.rating in 1..5 && submitted.comment.length<=500){"Choose a rating and use up to 500 characters."}
            submitted.details.requireValid()
            val paths=withContext(Dispatchers.IO){submitted.details.photos.map { id ->
                require(photos.isOwned(id,actor,event.id)){"A photo is missing. Remove it and choose it again."}
                val file=File(context.filesDir,"feedback_photos/$id.jpg")
                val bytes=compressedPhoto(file)
                val path="reviews/$actor/$id"
                val reference=backend.storage.reference.child(path)
                val existing=try { reference.metadata.await() } catch(error:StorageException) {
                    if(error.errorCode==StorageException.ERROR_OBJECT_NOT_FOUND)null else throw error
                }
                if(existing==null)reference.putBytes(bytes,StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
                else require(existing.sizeBytes==bytes.size.toLong()) { "The saved upload does not match this photo. Choose it again." }
                path
            }}
            backend.call("submitFeedback",mapOf("eventId" to event.id,"rating" to submitted.rating,
                "comment" to submitted.comment,"details" to submitted.details.payload(paths)))
            drafts.clear(actor,event.id)
            message("Review saved on the server.")
            }},back=back,reviews={viewingReviews=true},commentTag="cloud_feedback_comment",submitTag="cloud_submit_feedback",
            photos={
                FeedbackPhotoStrip(draft.details.photos,actor,event.id,busy,add={
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },remove={id->
                    update(draft.copy(details=draft.details.copy(photos=draft.details.photos-id)))
                    if(draftError.isEmpty())perform{withContext(Dispatchers.IO){photos.removeDraft(id,actor,event.id)}}
                },compact=true,addTag="cloud_feedback_add_photo")
            })
        return
    }
    Column(Modifier.fillMaxSize()) {
        AppPageHeader(if(own!=null) "My review" else "Event reviews",onBack={if(viewingReviews && canReview)viewingReviews=false else back()})
        Page(Modifier.weight(1f)) {
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(statusMessage.isNotBlank()) Text(statusMessage,color=MaterialTheme.colorScheme.error)
            if(own!=null) {
                Text("Your review",style=MaterialTheme.typography.titleLarge,modifier=Modifier.testTag("cloud_feedback_saved"))
                CloudReviewCard(backend,own,true)
            } else if(event.status==EventStatus.CANCELLED)Text("This event is cancelled. New reviews are closed.")
            else if(!ended)Text("Reviews open after the event ends.")
            else if(!confirmed)Text("Only confirmed participants can review this event.")
            else if(viewingReviews) PageActionButton(onClick={viewingReviews=false}){Text("Write a review")}
            HorizontalDivider()
            Text("Event reviews",style=MaterialTheme.typography.titleLarge)
            if(reviews.isEmpty())Text("No reviews are available to you yet.")
            reviews.filterNot{it["own"]==true && own!=null}.forEach { review ->
                key(review.string("id")){CloudReviewCard(backend,review,review["own"]==true)}
            }
        }
    }
}

private fun compressedPhoto(file:File):ByteArray {
    val raw=file.readBytes()
    if(raw.size<=2*1024*1024)return raw
    val bitmap=requireNotNull(decodeImage(raw)){"Could not read the photo."}
    return try {
        var quality=85
        var bytes:ByteArray
        do{bytes=ByteArrayOutputStream().use{output->check(bitmap.compress(Bitmap.CompressFormat.JPEG,quality,output));output.toByteArray()};quality-=15}
        while(bytes.size>2*1024*1024 && quality>=25)
        require(bytes.size<=2*1024*1024){"This photo is too large. Choose a smaller photo."};bytes
    } finally {bitmap.recycle()}
}

private fun decodeImage(bytes:ByteArray):Bitmap? {
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
    if(bounds.outWidth<=0 || bounds.outHeight<=0)return null
    var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1600)sample*=2
    return BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})
}

@Composable
private fun CloudLocalPhoto(file:File) {
    val bitmap by produceState<Bitmap?>(null,file.path){value=withContext(Dispatchers.IO){runCatching{decodeImage(file.readBytes())}.getOrNull()}}
    bitmap?.let{PhotoPreview(it,"Selected review photo")}?:Text("Photo unavailable")
}

@Composable
private fun CloudReviewCard(backend:CloudOperations,value:Map<*,*>,own:Boolean) {
    val detail=details(value.map("details"))
    val label=if(own)"Your review" else value.string("authorName").ifBlank{if(detail.anonymous)"Anonymous participant" else "Participant"}
    InfoCard("$label · ${value.number("rating")}/5",value.string("comment").ifBlank{"No written comment"})
    SavedFeedbackDetails(detail)
    FeedbackPrivacySummary(detail)
    Text(displayTime(value.number("createdAt")),style=MaterialTheme.typography.bodySmall)
    detail.photos.forEach { mediaId -> CloudProtectedPhoto(backend,mediaId) }
}

@Composable
private fun CloudProtectedPhoto(backend:CloudOperations,mediaId:String) {
    var failure by remember(backend,mediaId){mutableStateOf<String?>(null)}
    var retry by remember(backend,mediaId){mutableIntStateOf(0)}
    val bitmap by produceState<Bitmap?>(null,backend,mediaId,retry){
        failure=null
        try {
            val response=backend.call("mediaRead",mapOf("mediaId" to mediaId))
            value=withContext(Dispatchers.Default){
                val encoded=response.string("base64")
                require(encoded.length<=3_000_000){"Photo is too large."}
                requireNotNull(decodeImage(Base64.decode(encoded,Base64.DEFAULT))){"Photo could not be decoded."}
            }
        }catch(cancelled:CancellationException){throw cancelled}catch(error:Exception){failure=error.message?:"Photo unavailable"}
    }
    bitmap?.let{PhotoPreview(it,"Review photo")}
        ?: if(failure==null)LinearProgressIndicator(Modifier.fillMaxWidth()) else TextButton(onClick={retry++}){Text("Photo unavailable · Retry")}
}

@Composable
private fun PhotoPreview(bitmap:Bitmap,description:String) {
    var expanded by remember(bitmap){mutableStateOf(false)}
    Image(bitmap.asImageBitmap(),description,Modifier.fillMaxWidth().heightIn(max=220.dp).clickable{expanded=true},contentScale=ContentScale.Fit)
    if(expanded)Dialog(onDismissRequest={expanded=false}) {
        Surface {Column(Modifier.padding(12.dp)){Image(bitmap.asImageBitmap(),description,Modifier.fillMaxWidth().heightIn(max=550.dp),contentScale=ContentScale.Fit)
            TextButton(onClick={expanded=false}){Text("Close photo")}}}
    }
}

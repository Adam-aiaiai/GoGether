package com.gogether.app.feature.checkin.cloud

import com.gogether.app.core.ui.PageActionButton
import com.gogether.app.core.ui.AppPhotoGallery
import com.gogether.app.core.ui.AppMediaThumbnail
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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gogether.app.core.data.cloud.CloudOperations
import com.gogether.app.core.model.Event
import com.gogether.app.core.model.EventStatus
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
    organizerName:String,back:()->Unit,statusMessage:String = "",loading:Boolean = false, replacesReviewId:String = "", ratingSummary:com.gogether.app.core.model.EventRatingSummary? = null) {
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
            draft,busy,draftError,statusMessage,::update,{update(draft)},allowAnonymous=actor!=event.organizerId,submit={perform {
            val submitted=draft
            require(submitted.rating in 1..5 && submitted.comment.length<=100){"Choose a rating and use up to 100 characters."}
            submitted.details.requireValid(maxTextLength = 100)
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
                "comment" to submitted.comment,"details" to submitted.details.payload(paths),"replacesReviewId" to replacesReviewId))
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
            com.gogether.app.core.ui.EventRatingOverview(event.id,ratingSummary ?: com.gogether.app.core.ui.LocalEventRatings.current[event.id])
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(statusMessage.isNotBlank()) Text(statusMessage,color=MaterialTheme.colorScheme.error)
            if(own!=null) {
                key(actor, event.id) { CloudReviewCard(backend,own,true,Modifier.testTag("cloud_feedback_saved"), changed={perform { }}) }
            } else if(event.status==EventStatus.CANCELLED)ReviewNotice("This event is cancelled. New reviews are closed.")
            else if(!ended)ReviewNotice("Reviews open after the event ends.")
            else if(!confirmed)ReviewNotice("Only confirmed participants can review this event.")
            else if(viewingReviews) PageActionButton(onClick={viewingReviews=false}){Text("Write a review")}
            com.gogether.app.feature.social.DeletedReviewThreads(event.id)
            val otherReviews = reviews.filterNot{it["own"]==true && own!=null}
            if(own != null && otherReviews.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Event reviews",style=MaterialTheme.typography.titleMedium)
            }
            if(reviews.isEmpty())ReviewNotice(if(own == null) "No reviews are available to you yet." else "No other reviews yet.")
            Column(verticalArrangement = Arrangement.spacedBy(50.dp)) {
                otherReviews.forEach { review ->
                    key(actor, event.id, review.string("id")){
                        CloudReviewCard(backend,review,review["own"]==true,
                            Modifier.testTag("cloud_review_${review.string("id")}"), changed={perform { }})
                    }
                }
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
private fun ReviewNotice(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CloudReviewCard(backend:CloudOperations,value:Map<*,*>,own:Boolean, modifier: Modifier = Modifier, changed:()->Unit = {}) {
    val detail=details(value.map("details"))
    val label=value.string("authorName").ifBlank{if(own) "You" else "Participant"}
    SavedReviewItem(label,own,value.number("rating").toInt(),value.string("comment"),
        value.number("createdAt"),detail,modifier,avatarUri=value.string("avatarUri"),anonymousName=value.string("authorName"),organizer=value["organizer"]==true || value["organizerReview"]==true) { CloudReviewPhotos(backend, detail.photos) }
    com.gogether.app.feature.social.SocialContentActions(value.string("publicId").ifBlank {value.string("id")},changed)
}

/** Keep protected media scoped to this viewer/review composition; never use a public URL. */
@Composable
private fun CloudReviewPhotos(backend: CloudOperations, mediaIds: List<String>) {
    var preview by remember(backend, mediaIds) { mutableStateOf<Int?>(null) }
    val cache = remember(backend, mediaIds) { mutableStateMapOf<String, Bitmap>() }
    val loadPhoto = remember(backend, mediaIds) {
        val loader: suspend (String) -> Bitmap? = { id ->
            cache[id] ?: readProtectedPhoto(backend, id).also { cache[id] = it }
        }
        loader
    }
    if (mediaIds.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (index in 0..2) {
                val id = mediaIds.getOrNull(index)
                if (id != null) key(id) {
                    CloudProtectedPhoto(id, loadPhoto, Modifier.weight(1f)) { preview = index }
                } else Spacer(Modifier.weight(1f))
            }
        }
    }
    preview?.let { index ->
        key(backend, mediaIds) { AppPhotoGallery(mediaIds, index, loadPhoto) { preview = null } }
    }
}

private suspend fun readProtectedPhoto(backend: CloudOperations, mediaId: String): Bitmap {
    val response = backend.call("mediaRead", mapOf("mediaId" to mediaId))
    return withContext(Dispatchers.Default) {
        val encoded = response.string("base64")
        require(encoded.length <= 3_000_000) { "Photo is too large." }
        requireNotNull(decodeImage(Base64.decode(encoded, Base64.DEFAULT))) { "Photo could not be decoded." }
    }
}

@Composable
private fun CloudProtectedPhoto(mediaId: String, loadPhoto: suspend (String) -> Bitmap?,
    modifier: Modifier = Modifier, preview: () -> Unit) {
    var failure by remember(mediaId, loadPhoto) { mutableStateOf<String?>(null) }
    var retry by remember(mediaId, loadPhoto) { mutableIntStateOf(0) }
    val bitmap by produceState<Bitmap?>(null, mediaId, loadPhoto, retry) {
        failure = null
        try { value = loadPhoto(mediaId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message ?: "Photo unavailable" }
    }
    AppMediaThumbnail(onPreview = preview, modifier = modifier, previewTag = "cloud_review_photo_$mediaId") {
        bitmap?.let {
            Image(it.asImageBitmap(), "Review photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } ?: if (failure == null) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else TextButton(onClick = { retry++ }) { Text("Retry photo", style = MaterialTheme.typography.bodySmall) }
    }
}

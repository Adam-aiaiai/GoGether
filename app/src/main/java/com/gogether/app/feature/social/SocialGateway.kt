package com.gogether.app.feature.social

import android.content.Context
import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.data.cloud.CloudOperations
import com.gogether.app.core.model.*
import com.gogether.app.feature.checkin.prototype.*

interface SocialGateway { suspend fun call(action:String, args:Map<String,Any?> = emptyMap()):Map<String,Any?> }
class RepositorySocialGateway(context:Context, private val repository:GogetherRepository):SocialGateway {
    private val store=LocalSocialStore(context)
    private val reviews=PreferencesPrototypeStore(context)
    private fun contents(source:AppSnapshot):Pair<List<SocialContent>,List<SocialContent>> {
        val bridge=RegistrationAttendanceBridge({source},reviews)
        val questions=source.questions.map { SocialContent(it.id,it.eventId,it.authorId,it.content,it.createdAt,"QUESTION") }
        val feedback=source.events.flatMap { e ->
            val session=bridge.key(e)
            val richer=reviews.rawFeedbackForSession(session)
            richer.map { SocialContent(it.contentId,e.id,it.userId,listOf(it.comment,it.details.positiveComment.takeIf { text -> text.isNotBlank() }?.let { text -> "Positive: $text" }.orEmpty(),it.details.improvementComment.takeIf { text -> text.isNotBlank() }?.let { text -> "Could improve: $text" }.orEmpty()).filter { text -> text.isNotBlank() }.joinToString("\n"),it.createdAt,"REVIEW",anonymous=it.details.anonymous,private=it.details.visibility==FeedbackVisibility.ORGANIZER_ONLY,rating=it.rating) } +
                source.feedback.filter { it.eventId==e.id && richer.none { f->f.userId==it.userId } }.map {
                SocialContent(LocalSocialStore.legacyReviewId(it.id),e.id,it.userId,it.comment,it.createdAt,"REVIEW",rating=it.rating)
                }
        }
        val migrated=source.questions.flatMap { q ->
            val history=q.replyHistory.ifEmpty { q.answer?.let { listOf(com.gogether.app.core.model.QuestionReply("answer-${q.id}",q.answeredBy ?: source.event(q.eventId)?.organizerId.orEmpty(),it,q.answeredAt ?: q.createdAt)) } ?: emptyList() }
            history.map { r -> SocialContent(r.id,q.eventId,r.authorId,r.content,r.createdAt,"REPLY",q.id,q.id) }
        }
        return (questions+feedback) to migrated
    }
    fun projectRatings(source:AppSnapshot,now:Long=System.currentTimeMillis()):AppSnapshot {
        if(repository is CloudOperations) return source
        val saved=store.load()
        val bridge=RegistrationAttendanceBridge({source},reviews)
        val summaries=source.events.associate { event ->
            val current=reviews.currentRawFeedbackForSession(bridge.key(event))
            val candidates=current.map { RatingReview(it.contentId,event.id,it.userId,it.rating,it.createdAt,it.details.visibility==FeedbackVisibility.PUBLIC) } +
                source.feedback.filter {it.eventId==event.id && current.none {r->r.userId==it.userId}}.map {
                    RatingReview(LocalSocialStore.legacyReviewId(it.id),event.id,it.userId,it.rating,it.createdAt)
                }
            event.id to eventRatingSummary(source,event,candidates,saved.deleted,now)
        }
        return source.copy(eventRatings=summaries)
    }
    fun projectNotifications(source:AppSnapshot):AppSnapshot {
        if(repository is CloudOperations) return source
        val saved=store.load()
        val (roots,migrated)=contents(source)
        val all=roots+(saved.replies+migrated).distinctBy { it.id }
        val external=(saved.notices+reviews.reviewNotices()).map { n ->
            Notification(n.id,n.userId,n.eventId,n.type,interactionMessage(n.type),n.createdAt,
                isRead=n.id in saved.readNoticeIds,contentId=n.contentId,rootId=n.rootId)
        }
        val hosts=source.events.associate { it.id to it.organizerId }
        return source.copy(notifications=(source.notifications+external).distinctBy { it.id }
            .filter { it.userId==source.currentUserId }
            .mapNotNull { projectInteractionNotice(it,all,source.users,hosts,saved) })
    }
    suspend fun markRead(id:String):Result<Unit> {
        if(repository is CloudOperations) return repository.markRead(id)
        val actor=repository.state.value.currentUserId
        val saved=store.load()
        val external=(saved.notices+reviews.reviewNotices()).find { it.id==id }
        if(external==null) return repository.markRead(id)
        return runCatching {
            require(actor!=null && external.userId==actor) { "Notification unavailable" }
            store.edit { it.copy(readNoticeIds=it.readNoticeIds+id) to Unit }
        }
    }
    suspend fun markAllRead():Result<Unit> {
        if(repository is CloudOperations) return repository.markAllRead()
        val actor=repository.state.value.currentUserId
        val ids=(store.load().notices+reviews.reviewNotices()).filter { it.userId==actor }.map { it.id }
        val native=repository.markAllRead()
        if(native.isFailure) return native
        return runCatching {
            require(actor!=null && repository.state.value.currentUserId==actor) { "Account changed. Try again." }
            store.edit { it.copy(readNoticeIds=it.readNoticeIds+ids) to Unit }
        }
    }
    override suspend fun call(action:String,args:Map<String,Any?>):Map<String,Any?> {
        if(repository is CloudOperations) {
            val actor=repository.state.value.currentUserId
            val result=repository.call(if(action=="read") "socialRead" else "socialMutate",args+if(action=="read") emptyMap() else mapOf("operation" to action))
            if(action!="read") {
                // The write was acknowledged. A refresh failure must not invite a duplicate reply.
                try { repository.refresh() }
                catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
                catch(_:Exception) { /* CloudOperations exposes the refresh error through sync. */ }
                check(repository.state.value.currentUserId==actor) { "Account changed. Open the conversation again." }
            }
            return result
        }
        val source=repository.state.value;val actor=requireNotNull(source.currentUserId)
        return store.edit { saved ->
            val (roots,migrated)=contents(source)
            val engine=SocialEngine(saved.copy(replies=(saved.replies+migrated).distinctBy { it.id }),roots,source.users.associate { it.id to it.displayName },
                source.events.associate { it.id to it.organizerId },{ source.event(it)?.statusAt(System.currentTimeMillis())==EventStatus.OPEN },setOf(LocalSocialStore.MAINTAINER))
            val result=if(action=="read") engine.read(actor,args["eventId"] as? String,args["rootId"] as? String) else engine.mutate(actor,action,args)
            engine.state to result
        }
    }
}

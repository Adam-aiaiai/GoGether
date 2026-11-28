package com.gogether.app.feature.social

import com.gogether.app.core.model.*
import java.util.UUID

/** Private delivery record; public Notification DTOs never contain an anonymous actor ID. */
data class InteractionNotice(val id:String, val userId:String, val eventId:String, val actorId:String,
    val rootId:String, val contentId:String, val type:NotificationType, val createdAt:Long)

fun interactionDeliveries(content:SocialContent, root:SocialContent, target:SocialContent,
    organizer:String, blocked:(String,String)->Boolean):List<InteractionNotice> {
    val recipients=when(content.kind) {
        "QUESTION", "REVIEW" -> setOf(organizer)
        else -> setOfNotNull(root.authorId,target.authorId,organizer.takeIf { root.kind=="QUESTION" })
    }
    val type=when(content.kind) {
        "QUESTION" -> NotificationType.QUESTION_ASKED
        "REVIEW" -> NotificationType.REVIEW_POSTED
        else -> if(root.kind=="QUESTION") NotificationType.QUESTION_REPLIED else NotificationType.REVIEW_REPLIED
    }
    return recipients.filter { it!=content.authorId && !blocked(it,content.authorId) && !blocked(it,root.authorId) &&
        (!root.private || it==root.authorId || it==organizer) }.map {
        InteractionNotice(UUID.nameUUIDFromBytes("interaction:${content.id}:$it".toByteArray()).toString(),it,
            content.eventId,content.authorId,root.id,content.id,type,content.createdAt)
    }
}

fun interactionMessage(type:NotificationType):String = when(type) {
    NotificationType.QUESTION_ASKED -> "Asked a question about your event."
    NotificationType.QUESTION_ANSWERED, NotificationType.QUESTION_REPLIED -> "Replied in an event question."
    NotificationType.REVIEW_POSTED -> "Reviewed your event."
    NotificationType.REVIEW_REPLIED -> "Replied in an event review."
    else -> "Activity update."
}

/** The same permission-filtered list drives the inbox, unread count, tabs and bottom badge. */
fun projectInteractionNotice(notice:Notification, contents:List<SocialContent>, users:List<User>,
    hosts:Map<String,String>, social:SocialState):Notification? {
    if(!notice.type.isInteraction) return notice
    val id=notice.contentId ?: notice.questionId?.let { if(notice.type==NotificationType.QUESTION_ANSWERED) "answer-$it" else it }
    val content=contents.find {it.id==id}
    val rootId=notice.rootId ?: content?.rootId ?: notice.questionId
    val root=contents.find {it.id==rootId}
    fun unavailable(text:String)=notice.copy(content=text,contentId=id ?: rootId,rootId=rootId,
        actorName="Activity conversation",actorAvatarUri="")
    if(content==null || root==null || root.eventId !in hosts) return unavailable("This conversation is no longer available.")
    if(social.blocked(notice.userId,content.authorId) || social.blocked(notice.userId,root.authorId)) return null
    if(root.private && notice.userId!=root.authorId && notice.userId!=hosts[root.eventId]) return unavailable("This conversation is no longer available to you.")
    if(root.id in social.deleted || content.id in social.deleted) return unavailable("This content has been deleted. Existing replies may still be available.")
    val author=users.find {it.id==content.authorId}
    return notice.copy(content=interactionMessage(notice.type),contentId=id,rootId=root.id,
        actorName=if(content.anonymous) social.aliases[content.eventId to content.authorId] ?: "Anonymous participant" else author?.displayName ?: "Participant",
        actorAvatarUri=if(content.anonymous) "" else author?.avatarUri.orEmpty())
}

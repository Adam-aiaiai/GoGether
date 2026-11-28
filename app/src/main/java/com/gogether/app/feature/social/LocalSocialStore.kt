package com.gogether.app.feature.social

import android.content.Context
import com.gogether.app.core.model.NotificationType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One committed document; failed writes never publish a partially applied social action. */
class LocalSocialStore(context:Context) {
    private val prefs=context.getSharedPreferences("social_v1",Context.MODE_PRIVATE)
    companion object {
        internal val lock=Any()
        const val MAINTAINER="project-maintainer"
        internal fun encodeNotice(n:InteractionNotice)=JSONObject().put("id",n.id).put("userId",n.userId).put("eventId",n.eventId).put("actorId",n.actorId).put("rootId",n.rootId).put("contentId",n.contentId).put("type",n.type.name).put("createdAt",n.createdAt)
        internal fun decodeNotice(n:JSONObject)=InteractionNotice(n.getString("id"),n.getString("userId"),n.getString("eventId"),n.getString("actorId"),n.getString("rootId"),n.getString("contentId"),NotificationType.valueOf(n.getString("type")),n.getLong("createdAt"))
        fun reviewId(session:String,user:String,createdAt:Long)=UUID.nameUUIDFromBytes("review:$session:$user:$createdAt".toByteArray()).toString()
        fun legacyReviewId(id:String)=UUID.nameUUIDFromBytes("legacy-review:$id".toByteArray()).toString()
    }
    private fun JSONObject.rows(key:String):List<JSONObject> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
    fun load():SocialState = synchronized(lock) {
        val v=JSONObject(prefs.getString("state","{}")!!)
        SocialState(v.rows("replies").map { SocialContent(it.getString("id"),it.getString("eventId"),it.getString("authorId"),it.getString("text"),it.getLong("createdAt"),"REPLY",it.getString("rootId"),it.getString("targetId"),it.optBoolean("anonymous"),it.optBoolean("private")) },
            v.optJSONArray("deleted")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            v.rows("likes").map { it.getString("id") to it.getString("user") }.toSet(),
            v.rows("blocks").map { SocialBlock(it.getString("id"),it.getString("owner"),it.getString("target"),it.getString("kind"),it.getString("label")) },
            v.rows("reports").map { SocialReport(it.getString("id"),it.getString("reporter"),it.getString("contentId"),it.getString("reason"),it.getLong("createdAt"),it.getString("status"),it.getString("outcome"),it.getString("moderator"),it.getLong("resolvedAt")) },
            v.rows("aliases").associate { (it.getString("event") to it.getString("user")) to it.getString("name") },
            v.rows("notices").map(::decodeNotice),
            v.optJSONArray("readNoticeIds")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet())
    }
    fun <T> edit(block:(SocialState)->Pair<SocialState,T>):T = synchronized(lock) {
        val before=load();val (s,result)=block(before)
        if(s!=before) {
            val v=JSONObject().put("replies",JSONArray(s.replies.map { JSONObject().put("id",it.id).put("eventId",it.eventId).put("authorId",it.authorId).put("text",it.text).put("createdAt",it.createdAt).put("rootId",it.rootId).put("targetId",it.targetId).put("anonymous",it.anonymous).put("private",it.private) }))
                .put("deleted",JSONArray(s.deleted.toList())).put("likes",JSONArray(s.likes.map { JSONObject().put("id",it.first).put("user",it.second) }))
                .put("blocks",JSONArray(s.blocks.map { JSONObject().put("id",it.id).put("owner",it.owner).put("target",it.target).put("kind",it.kind).put("label",it.label) }))
                .put("reports",JSONArray(s.reports.map { JSONObject().put("id",it.id).put("reporter",it.reporter).put("contentId",it.contentId).put("reason",it.reason).put("createdAt",it.createdAt).put("status",it.status).put("outcome",it.outcome).put("moderator",it.moderator).put("resolvedAt",it.resolvedAt) }))
                .put("aliases",JSONArray(s.aliases.map { (key,name)->JSONObject().put("event",key.first).put("user",key.second).put("name",name) }))
            v.put("notices",JSONArray(s.notices.map(::encodeNotice))).put("readNoticeIds",JSONArray(s.readNoticeIds.toList()))
            check(prefs.edit().putString("state",v.toString()).commit()) { "Could not save. Try again." }
        };result
    }
    fun anonymousName(event:String,user:String):String = edit { s ->
        val engine=SocialEngine(s,emptyList(),emptyMap(),emptyMap(),{false});val name=engine.alias(event,user);engine.state to name
    }
}

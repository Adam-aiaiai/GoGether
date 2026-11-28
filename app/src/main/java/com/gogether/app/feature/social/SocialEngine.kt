package com.gogether.app.feature.social

import java.util.UUID

/** Internal records contain identity; only [visible] DTOs leave this permission boundary. */
data class SocialContent(val id: String, val eventId: String, val authorId: String, val text: String,
    val createdAt: Long, val kind: String, val rootId: String = id, val targetId: String = id,
    val anonymous: Boolean = false, val private: Boolean = false, val rating: Int = 0)
data class SocialBlock(val id: String, val owner: String, val target: String, val kind: String, val label: String)
data class SocialReport(val id: String, val reporter: String, val contentId: String, val reason: String,
    val createdAt: Long, val status: String = "OPEN", val outcome: String = "", val moderator: String = "", val resolvedAt: Long = 0)
data class SocialState(val replies: List<SocialContent> = emptyList(), val deleted: Set<String> = emptySet(),
    val likes: Set<Pair<String,String>> = emptySet(), val blocks: List<SocialBlock> = emptyList(),
    val reports: List<SocialReport> = emptyList(), val aliases: Map<Pair<String,String>,String> = emptyMap(),
    val notices: List<InteractionNotice> = emptyList(), val readNoticeIds: Set<String> = emptySet()) {
    fun blocked(a: String,b: String) = blocks.any { it.kind == "PERSONAL" &&
        ((it.owner == a && it.target == b) || (it.owner == b && it.target == a)) }
    fun registrationBlocked(host: String, user: String) = blocks.any { it.kind == "REGISTRATION" && it.owner == host && it.target == user }
}

class SocialEngine(initial: SocialState, private val roots: List<SocialContent>,
    private val names: Map<String,String>, private val hosts: Map<String,String>,
    private val questionOpen: (String)->Boolean, private val maintainers: Set<String> = emptySet(),
    private val now: ()->Long = System::currentTimeMillis, private val newId: ()->String = { UUID.randomUUID().toString() }) {
    var state = initial; private set
    private val all get() = roots + state.replies
    private fun root(c:SocialContent) = all.find { it.id == c.rootId } ?: error("Thread no longer exists.")
    private fun accessible(c:SocialContent, actor:String):Boolean {
        val r=root(c)
        return c.eventId in hosts && (!r.private || actor==r.authorId || actor==hosts[r.eventId]) &&
            !state.blocked(actor,r.authorId) && !state.blocked(actor,c.authorId)
    }
    private fun content(id:String, actor:String):SocialContent = requireNotNull(all.find { it.id==id && accessible(it,actor) }) { "Content unavailable." }
    fun alias(event:String,user:String):String {
        state.aliases[event to user]?.let { return it }
        val name="Anonymous participant %02d".format(state.aliases.keys.count { it.first==event }+1)
        state=state.copy(aliases=state.aliases+((event to user) to name));return name
    }
    private fun label(c:SocialContent) = if(c.anonymous) alias(c.eventId,c.authorId) else names[c.authorId] ?: "Participant"
    fun visible(c:SocialContent, actor:String):Map<String,Any?> {
        val deleted=c.id in state.deleted
        val r=root(c)
        val target=all.find { it.id==c.targetId }
        val canReply=c.id !in state.deleted && r.id !in state.deleted && accessible(c,actor) &&
            (r.kind!="QUESTION" || (questionOpen(r.eventId) && actor in setOf(r.authorId,hosts[r.eventId])))
        return mapOf("id" to c.id,"rootId" to c.rootId,"eventId" to c.eventId,"authorName" to label(c),
            "anonymous" to c.anonymous,"own" to (c.authorId==actor),"organizer" to (!c.anonymous && c.authorId==hosts[c.eventId]),
            "content" to if(deleted) "Content deleted" else c.text,"createdAt" to c.createdAt,"deleted" to deleted,
            "targetName" to target?.let { if(it.id in state.deleted || !accessible(it,actor)) "Content unavailable" else label(it) },
            "private" to r.private,"rating" to if(deleted) 0 else c.rating,"kind" to c.kind,
            "canReply" to canReply,"likes" to state.likes.count { it.first==c.id },"liked" to ((c.id to actor) in state.likes))
    }
    fun read(actor:String,eventId:String?=null,rootId:String?=null):Map<String,Any?> {
        require(actor in names) { "Sign in first." }
        if(rootId!=null){val c=content(rootId,actor);val r=root(c);return mapOf("items" to
            (listOf(r)+state.replies.filter { it.rootId==r.id }.sortedBy { it.createdAt }).filter { accessible(it,actor) }.map { visible(it,actor) })}
        if(eventId!=null) return mapOf("items" to roots.filter { it.eventId==eventId && accessible(it,actor) }.sortedByDescending { it.createdAt }.map { c ->
            val answer = if(c.kind=="QUESTION" && c.id !in state.deleted) state.replies
                .filter { it.rootId==c.id && it.authorId==hosts[c.eventId] && it.id !in state.deleted && accessible(it,actor) }
                .maxByOrNull { it.createdAt } else null
            visible(c,actor) + ("latestAnswer" to answer?.let { visible(it,actor) })
        })
        return mapOf("blocks" to state.blocks.filter { it.owner==actor }.map { mapOf("id" to it.id,"kind" to it.kind,"label" to it.label) },
            "maintainer" to (actor in maintainers),"reports" to state.reports.filter { it.reporter==actor || actor in maintainers }.map { report ->
                val c=all.find { it.id==report.contentId }
                mapOf("id" to report.id,"contentId" to report.contentId,"reason" to report.reason,"status" to report.status,"outcome" to report.outcome,
                    "content" to if(actor in maintainers) c?.text else null,"authorName" to if(actor in maintainers) c?.let(::label) else null,
                    "resolvedAt" to report.resolvedAt) })
    }
    fun mutate(actor:String,action:String,args:Map<String,Any?>):Map<String,Any?> {
        require(actor in names) { "Sign in first." }
        fun text(key:String)=args[key] as? String ?: ""
        fun body(key:String)=text(key).trim().also { require(it.length in 1..100) { "Use 1–100 characters." } }
        if(action=="unblock") {
            val b=requireNotNull(state.blocks.find { it.id==text("blockId") && it.owner==actor }) { "Block not found." }
            state=state.copy(blocks=state.blocks-b);return mapOf("ok" to true)
        }
        if(action=="resolveReport") {
            require(actor in maintainers) { "Only project maintainers can handle reports." }
            val report=state.reports.find { it.id==text("reportId") } ?: error("Report not found.")
            require(report.status=="OPEN") { "This report has already been handled." }
            val resolution=text("resolution");require(resolution in setOf("DISMISSED","REMOVED"))
            val outcome=body("outcome")
            state=state.copy(reports=state.reports.map { if(it.id==report.id) it.copy(status=resolution,outcome=outcome,moderator=actor,resolvedAt=now()) else it },
                deleted=if(resolution=="REMOVED") state.deleted+report.contentId else state.deleted)
            return mapOf("ok" to true)
        }
        if(action=="blockUser") {
            val kind=text("kind");require(kind in setOf("PERSONAL","REGISTRATION"))
            val c=text("contentId").takeIf { it.isNotEmpty() }?.let { content(it,actor) }
            val target=c?.authorId ?: text("targetUserId")
            require(target in names && target!=actor) { "Choose another user." }
            // A registration list is owned by the organizer, and applies to that organizer's events.
            if(kind=="REGISTRATION") require(hosts.values.any { it==actor }) { "Only organizers can manage registration restrictions." }
            val display = c?.let(::label) ?: names.getValue(target)
            if(state.blocks.none { it.owner==actor && it.target==target && it.kind==kind })
                state=state.copy(blocks=state.blocks+SocialBlock(newId(),actor,target,kind,display))
            return mapOf("ok" to true)
        }
        val c=content(text("contentId"),actor);val r=root(c)
        when(action) {
            "reply" -> {
                require(c.id !in state.deleted && r.id !in state.deleted) { "This content has been deleted." }
                if(r.kind=="QUESTION") require(questionOpen(r.eventId) && actor in setOf(r.authorId,hosts[r.eventId])) { "Only the question author and organizer can continue this conversation while the event is open." }
                // Anonymous reviewers keep their alias in every reply in this event.
                val anonymous=actor!=hosts[r.eventId] && (roots.any { it.eventId==r.eventId && it.authorId==actor && it.anonymous } || args["anonymous"]==true)
                val reply=SocialContent(newId(),c.eventId,actor,body("content"),now(),"REPLY",r.id,c.id,anonymous,r.private)
                if(anonymous) alias(c.eventId,actor)
                state=state.copy(replies=state.replies+reply, notices=state.notices+interactionDeliveries(reply,r,c,hosts.getValue(c.eventId),state::blocked));return mapOf("id" to reply.id)
            }
            "like" -> {
                require(c.kind=="REVIEW" && c.id !in state.deleted) { "Only active reviews can be liked." }
                require(args["liked"] is Boolean) { "Specify the desired like state." }
                state=state.copy(likes=if(args["liked"]==true) state.likes+(c.id to actor) else state.likes-(c.id to actor))
            }
            "delete" -> { require(c.authorId==actor) { "Only the author can delete this content." };state=state.copy(deleted=state.deleted+c.id) }
            "report" -> {
                require(c.authorId!=actor && c.id !in state.deleted) { "Choose active content from another user." }
                require(state.reports.none { it.reporter==actor && it.contentId==c.id && it.status=="OPEN" }) { "Your report is already awaiting review." }
                state=state.copy(reports=state.reports+SocialReport(newId(),actor,c.id,body("reason"),now()))
            }
            else -> error("Unknown social action.")
        }
        return mapOf("ok" to true)
    }
}

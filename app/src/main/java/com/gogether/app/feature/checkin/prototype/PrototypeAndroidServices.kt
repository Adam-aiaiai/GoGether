package com.gogether.app.feature.checkin.prototype

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

class PreferencesPrototypeStore(private val context: Context) : PrototypeStore {
    private companion object { val writeLock = FeedbackStorageLock.monitor }
    private val prefs = context.getSharedPreferences("checkin_prototype_v1", Context.MODE_PRIVATE)
    override fun registrationSnapshot(id: String): CheckInRegistrationSnapshot? = prefs.getString("registrations.$id", null)?.let {
        val value = JSONObject(it)
        val people = value.getJSONArray("participants")
        val waiting = value.getJSONArray("waiting")
        CheckInRegistrationSnapshot((0 until people.length()).map { index -> people.getJSONObject(index).let { person ->
            PrototypeAttendee(person.getString("id"), person.getString("name"))
        } }, (0 until waiting.length()).map { index -> waiting.getString(index) }.toSet())
    }
    override fun rememberRegistrations(id: String, snapshot: CheckInRegistrationSnapshot) = synchronized(writeLock) {
        require(snapshot.participants.map { it.userId }.distinct().size == snapshot.participants.size)
        val value = JSONObject().put("participants", JSONArray(snapshot.participants.map {
            JSONObject().put("id", it.userId).put("name", it.name)
        })).put("waiting", JSONArray(snapshot.waitlisted.toList()))
        check(prefs.edit().putString("registrations.$id", value.toString()).commit()) { "Could not save registration details. Try again." }
    }
    override fun activityInfo(id: String): CheckInActivityInfo? = prefs.getString("activity-info.$id", null)?.let { raw ->
        val value = JSONObject(raw)
        CheckInActivityInfo(PrototypeSessionStorage.decode(value.getString("session")),
            value.getString("organizer"), value.getString("title"), value.getString("meeting"), value.getString("introduction"),
            value.optString("sourceEventId").takeIf { it.isNotBlank() },
            value.optString("organizerName").takeIf { it.isNotBlank() })
    }
    override fun rememberActivityInfo(info: CheckInActivityInfo) = synchronized(writeLock) {
        val previous = activityInfo(info.session.id)
        require(previous == null || previous == info) { "The organizer or event details do not match the saved record." }
        if (previous == info) return@synchronized
        writeActivityInfo(info, updateSession = false)
    }
    override fun updateLinkedActivity(info: CheckInActivityInfo) = synchronized(writeLock) {
        val previous = requireNotNull(activityInfo(info.session.id))
        require(info.sourceEventId != null && previous.sourceEventId == info.sourceEventId && previous.organizerId == info.organizerId) {
            "The event owner or identity has changed."
        }
        writeActivityInfo(info, updateSession = true)
    }
    private fun writeActivityInfo(info: CheckInActivityInfo, updateSession: Boolean) {
        val value = JSONObject().put("session", PrototypeSessionStorage.encode(info.session))
            .put("organizer", info.organizerId).put("title", info.title).put("meeting", info.meetingName).put("introduction", info.introduction)
            .put("sourceEventId", info.sourceEventId)
            .put("organizerName", info.organizerDisplayName)
        val edit = prefs.edit().putString("activity-info.${info.session.id}", value.toString())
        if (updateSession) edit.putString("session.${info.session.id}", PrototypeSessionStorage.encode(info.session))
        check(edit.commit()) { "Could not save event details. Try again." }
    }
    override var hostedSession: PrototypeSession?
        get() = prefs.getString("hosted", null)?.let {
            runCatching { PrototypeSessionStorage.decode(it) }.getOrNull()
        }
        set(value) {
            val raw = value?.let { PrototypeSessionStorage.encode(it) }
            require(prefs.edit().putString("hosted", raw).commit()) { "Could not save the test event. Try again." }
        }
    override fun isCancelled(id: String) = prefs.getBoolean("cancelled.$id", false)
    override fun cancel(id: String) {
        require(prefs.edit().putBoolean("cancelled.$id", true).commit()) { "Could not save the cancellation." }
    }
    override fun receipt(sessionId: String, userId: String): PrototypeReceipt? {
        val time = prefs.getLong("receipt.$sessionId.$userId", 0)
        return if (time > 0) PrototypeReceipt(sessionId, userId, time,
            prefs.getString("receipt-key.$sessionId.$userId", null), prefs.getBoolean("receipt-host.$sessionId.$userId", false)) else null
    }
    override fun save(receipt: PrototypeReceipt) = synchronized(writeLock) {
        val previous = this.receipt(receipt.sessionId, receipt.userId)
        require(previous == null || previous.registrationKey != receipt.registrationKey) { "You are already checked in." }
        val edit = prefs.edit()
        previous?.let { edit.putString("receipt-archive.${it.sessionId}.${it.userId}.${it.registrationKey ?: it.checkedInAt}",
            JSONObject().put("at", it.checkedInAt).put("registrationKey", it.registrationKey).put("organizer", it.organizer).toString()) }
        require(edit.putLong("receipt.${receipt.sessionId}.${receipt.userId}", receipt.checkedInAt)
            .putString("receipt-key.${receipt.sessionId}.${receipt.userId}", receipt.registrationKey)
            .putBoolean("receipt-host.${receipt.sessionId}.${receipt.userId}", receipt.organizer).commit()) {
            "Could not save check-in. Try again."
        }
    }
    override fun rememberSession(userId: String, session: PrototypeSession) = synchronized(writeLock) {
        val key = "session.${session.id}"
        val previous = prefs.getString(key, null)?.let { PrototypeSessionStorage.decode(it) }
        require(previous == null || previous == session) { "Event details do not match the saved record. Get a new code." }
        val raw = PrototypeSessionStorage.encode(session)
        require(prefs.edit().putString(key, raw).putBoolean("member.${session.id}.$userId", true).commit()) {
            "Could not save the event record. Try again."
        }
    }
    override fun sessions(userId: String): List<PrototypeSession> = prefs.all.entries
        .filter { it.key.startsWith("member.") && it.key.endsWith(".$userId") && it.value == true }
        .mapNotNull {
            val id = it.key.removePrefix("member.").removeSuffix(".$userId")
            prefs.getString("session.$id", null)?.let { raw -> runCatching { PrototypeSessionStorage.decode(raw) }.getOrNull() }
        }
    private val social get() = com.gogether.app.feature.social.LocalSocialStore(context)
    override fun isDeleted(id: String) = id in social.load().deleted
    override fun isBlocked(viewer: String, author: String) = social.load().blocked(viewer, author)
    override fun feedback(sessionId: String, userId: String): PrototypeFeedback? = rawFeedback(sessionId,userId)?.takeUnless {
        isDeleted(it.contentId)
    }?.let { if(it.details.anonymous) it.copy(anonymousName=social.anonymousName(activityInfo(sessionId)?.sourceEventId ?: sessionId,userId)) else it }
    fun rawFeedback(sessionId: String, userId: String): PrototypeFeedback? =
        prefs.getString("feedback.$sessionId.$userId", null)?.let {
            val value = JSONObject(it)
            val details = value.optJSONObject("details")
            val tags = details?.optJSONArray("tags")
            val photos = details?.optJSONArray("photos")
            PrototypeFeedback(sessionId, userId, value.getInt("rating"), value.getString("comment"), value.getLong("createdAt"),
                FeedbackDetails(details?.optInt("organization") ?: 0, details?.optInt("venue") ?: 0,
                    details?.optInt("atmosphere") ?: 0,
                    if (tags == null) emptyList() else (0 until tags.length()).map { index -> tags.getString(index) },
                    details?.optBoolean("anonymous") ?: false,
                    when (details?.optString("visibility")) {
                        null, "", "PUBLIC" -> FeedbackVisibility.PUBLIC
                        else -> FeedbackVisibility.ORGANIZER_ONLY
                    },
                    if (photos == null) emptyList() else (0 until photos.length()).map { index -> photos.getString(index) },
                    details?.optString("positiveComment").orEmpty(), details?.optString("improvementComment").orEmpty()),
                "",value.optString("publicId"))
        }
    /** Current records including tombstones; archived reviews cannot return to the public score. */
    fun currentRawFeedbackForSession(sessionId:String):List<PrototypeFeedback> = prefs.all.keys
        .filter {it.startsWith("feedback.$sessionId.")}
        .mapNotNull {rawFeedback(sessionId,it.removePrefix("feedback.$sessionId."))}
    fun rawFeedbackForSession(sessionId: String): List<PrototypeFeedback> = prefs.all.keys
        .filter { it.startsWith("feedback.$sessionId.") || it.startsWith("feedback-archive.$sessionId.") }
        .mapNotNull { key -> if(key.startsWith("feedback.")) rawFeedback(sessionId,key.removePrefix("feedback.$sessionId.")) else {
            val value=JSONObject(prefs.getString(key,null)!!)
            PrototypeFeedback(sessionId,value.getString("userId"),value.getInt("rating"),value.getString("comment"),value.getLong("createdAt"),
                FeedbackDetails(anonymous=value.optBoolean("anonymous"),visibility=if(value.optBoolean("private")) FeedbackVisibility.ORGANIZER_ONLY else FeedbackVisibility.PUBLIC),publicId=value.optString("publicId"))
        } }
    override fun feedbackForSession(sessionId: String): List<PrototypeFeedback> = prefs.all.keys
        .filter { it.startsWith("feedback.$sessionId.") }
        .mapNotNull { feedback(sessionId, it.removePrefix("feedback.$sessionId.")) }
    fun reviewNotices(): List<com.gogether.app.feature.social.InteractionNotice> = prefs.all.keys
        .filter { it.startsWith("interaction.") }.flatMap { key ->
            val a=JSONArray(prefs.getString(key,"[]"));(0 until a.length()).map { com.gogether.app.feature.social.LocalSocialStore.decodeNotice(a.getJSONObject(it)) }
        }
    override fun saveFeedback(feedback: PrototypeFeedback) = synchronized(writeLock) {
        val key = "feedback.${feedback.sessionId}.${feedback.userId}"
        require(this.feedback(feedback.sessionId, feedback.userId) == null) { "You have already reviewed this event." }
        feedback.details.requireValid(maxTextLength = 500)
        val photoStore = FeedbackPhotoStore(context)
        require(feedback.details.photos.all { photoStore.isOwned(it, feedback.userId, feedback.sessionId) }) { "This photo is no longer available. Remove it and choose it again." }
        val draftPhotos = runCatching { FeedbackDraftStore(context).load(feedback.userId, feedback.sessionId).details.photos }.getOrDefault(emptyList())
        val value = JSONObject().put("publicId", feedback.contentId).put("rating", feedback.rating).put("comment", feedback.comment).put("createdAt", feedback.createdAt)
            .put("details", JSONObject().put("organization", feedback.details.organization).put("venue", feedback.details.venue)
                .put("atmosphere", feedback.details.atmosphere).put("tags", JSONArray(feedback.details.tags))
                .put("anonymous", feedback.details.anonymous).put("visibility", feedback.details.visibility.name)
                .put("photos", JSONArray(feedback.details.photos))
                .put("positiveComment", feedback.details.positiveComment).put("improvementComment", feedback.details.improvementComment))
        val edit = prefs.edit()
        activityInfo(feedback.sessionId)?.let { info -> info.sourceEventId?.let { eventId ->
            val content=com.gogether.app.feature.social.SocialContent(feedback.contentId,eventId,feedback.userId,feedback.comment,feedback.createdAt,"REVIEW",anonymous=feedback.details.anonymous,private=feedback.details.visibility==FeedbackVisibility.ORGANIZER_ONLY)
            if(content.anonymous) social.anonymousName(eventId,feedback.userId)
            val notices=com.gogether.app.feature.social.interactionDeliveries(content,content,content,info.organizerId,social.load()::blocked)
            // The durable delivery record and review commit together; projection never backfills old reviews.
            edit.putString("interaction.${feedback.contentId}",JSONArray(notices.map(com.gogether.app.feature.social.LocalSocialStore::encodeNotice)).toString())
        } }
        rawFeedback(feedback.sessionId,feedback.userId)?.let { old ->
            edit.putString("feedback-archive.${old.sessionId}.${old.userId}.${old.createdAt}",JSONObject().put("publicId",old.contentId).put("userId",old.userId).put("rating",old.rating).put("comment",old.comment).put("createdAt",old.createdAt).put("anonymous",old.details.anonymous).put("private",old.details.visibility==FeedbackVisibility.ORGANIZER_ONLY).toString())
        }
        require(edit.putString(key, value.toString())
            .remove(FeedbackDraftStore.key(feedback.userId, feedback.sessionId)).commit()) { "Could not save your review. Try again." }
        draftPhotos.filterNot { it in feedback.details.photos }.forEach {
            runCatching { photoStore.removeDraft(it, feedback.userId, feedback.sessionId) }
        }
    }
    fun latestReceipt(userId: String): PrototypeReceipt? = prefs.all.entries
        .filter { it.key.startsWith("receipt.") && it.key.endsWith(".$userId") && it.value is Long }
        .maxByOrNull { it.value as Long }?.let {
            receipt(it.key.removePrefix("receipt.").removeSuffix(".$userId"), userId)
        }
}

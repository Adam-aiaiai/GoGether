package com.gogether.app.feature.checkin.cloud

import android.content.Context
import android.os.SystemClock
import com.gogether.app.feature.checkin.prototype.*
import org.json.JSONArray
import org.json.JSONObject

internal fun Map<*, *>.string(key: String): String = this[key] as? String ?: ""
internal fun Map<*, *>.number(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L
internal fun Map<*, *>.map(key: String): Map<*, *>? = this[key] as? Map<*, *>
internal fun Map<*, *>.rows(key: String): List<Map<*, *>> = (this[key] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
internal fun details(value: Map<*, *>?): FeedbackDetails = value?.let {
    FeedbackDetails(it.number("organization").toInt(), it.number("venue").toInt(), it.number("atmosphere").toInt(),
        (it["tags"] as? List<*>)?.filterIsInstance<String>().orEmpty(), it["anonymous"] == true,
        if (it["visibility"] == "ORGANIZER_ONLY") FeedbackVisibility.ORGANIZER_ONLY else FeedbackVisibility.PUBLIC,
        (it["photos"] as? List<*>)?.filterIsInstance<String>().orEmpty())
} ?: FeedbackDetails()
internal fun FeedbackDetails.payload(photos: List<String> = this.photos): Map<String, Any?> = mapOf(
    "organization" to organization, "venue" to venue, "atmosphere" to atmosphere, "tags" to tags,
    "anonymous" to anonymous, "visibility" to visibility.name, "photos" to photos)

/** The elapsed age cannot accidentally become wall-clock age or a future timestamp. */
internal fun PositionFix.cloudPayload(elapsedNow: Long = SystemClock.elapsedRealtime()): Map<String, Any?> {
    validatePosition(this, elapsedNow)
    return mapOf("latitude" to point.latitude, "longitude" to point.longitude,
        "accuracyMeters" to accuracyMeters, "ageMillis" to elapsedNow - elapsedMillis, "simulated" to simulated)
}

internal data class CloudPendingCheckIn(val id: String, val code: String, val fix: Map<String, Any?>)

/** Pending intent is durable before sending. A lost response never enables a new check-in attempt. */
internal class CloudCheckInPendingStore(context: Context) {
    private val preferences = context.getSharedPreferences("cloud_checkin_pending_v1", Context.MODE_PRIVATE)
    private fun key(actor: String, event: String) = "$actor:$event"
    fun load(actor: String, event: String): CloudPendingCheckIn? = preferences.getString(key(actor,event),null)?.let {
        val value=JSONObject(it); val fix=value.getJSONObject("fix")
        CloudPendingCheckIn(value.getString("id"),value.getString("code"),fix.keys().asSequence().associateWith { field -> fix.get(field) })
    }
    fun save(actor: String,event: String,value: CloudPendingCheckIn) {
        val previous=load(actor,event)
        check(previous == null || previous.id == value.id) { "Resolve the previous check-in before scanning again." }
        check(preferences.edit().putString(key(actor,event),JSONObject().put("id",value.id).put("code",value.code)
            .put("fix",JSONObject(value.fix)).toString()).commit()) { "Could not save check-in progress." }
    }
    fun clear(actor: String,event: String) { check(preferences.edit().remove(key(actor,event)).commit()) { "Could not update check-in progress." } }
}

/** Cloud draft photos are local normalized copies until submitted. This never writes prototype receipts. */
internal class CloudFeedbackDraftStore(context: Context) {
    private val preferences=context.getSharedPreferences("cloud_feedback_drafts_v1",Context.MODE_PRIVATE)
    private fun key(actor:String,event:String)="$actor:$event"
    fun load(actor:String,event:String): FeedbackDraft = preferences.getString(key(actor,event),null)?.let {
        val json=JSONObject(it)
        fun strings(name:String)=json.optJSONArray(name)?.let { a -> (0 until a.length()).map { index -> a.getString(index) } }.orEmpty()
        FeedbackDraft(json.optInt("rating"),json.optString("comment"),FeedbackDetails(json.optInt("organization"),json.optInt("venue"),
            json.optInt("atmosphere"),strings("tags"),json.optBoolean("anonymous"),
            if(json.optString("visibility")=="ORGANIZER_ONLY") FeedbackVisibility.ORGANIZER_ONLY else FeedbackVisibility.PUBLIC,strings("photos")))
    } ?: FeedbackDraft()
    fun save(actor:String,event:String,value:FeedbackDraft) {
        val fields=value.details.payload().toMutableMap(); fields["rating"]=value.rating; fields["comment"]=value.comment
        check(preferences.edit().putString(key(actor,event),JSONObject(fields).toString()).commit()) { "Could not save the review draft." }
    }
    fun clear(actor:String,event:String) { check(preferences.edit().remove(key(actor,event)).commit()) { "Could not clear the saved draft." } }
}

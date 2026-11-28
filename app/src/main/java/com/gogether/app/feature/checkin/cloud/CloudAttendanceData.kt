package com.gogether.app.feature.checkin.cloud

import android.content.Context
import com.gogether.app.core.model.Registration
import com.gogether.app.core.model.RegistrationStatus
import com.gogether.app.core.model.EventStatus
import com.gogether.app.feature.checkin.prototype.*
import org.json.JSONArray
import org.json.JSONObject

internal fun Map<*, *>.string(key: String): String = this[key] as? String ?: ""
internal fun Map<*, *>.number(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L
internal fun Map<*, *>.map(key: String): Map<*, *>? = this[key] as? Map<*, *>
internal fun Map<*, *>.rows(key: String): List<Map<*, *>> = (this[key] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
/** A polled receipt must not outlive a newer registration snapshot on the screen. */
internal fun currentCloudReceipt(receipt: Map<*, *>?, registration: Registration?, status: EventStatus): Map<*, *>? {
    if (receipt == null || status == EventStatus.CANCELLED || registration?.status != RegistrationStatus.CONFIRMED) return null
    val key = receipt["registrationKey"] as? String
    return receipt.takeIf { if (key != null) key == registration.attendanceKey else it.number("checkedInAt") >= registration.joinedAt }
}

private fun reviewVisibility(value: Any?): FeedbackVisibility =
    if (value == null || value == "" || value == "PUBLIC") FeedbackVisibility.PUBLIC else FeedbackVisibility.ORGANIZER_ONLY

internal fun details(value: Map<*, *>?): FeedbackDetails = value?.let {
    FeedbackDetails(it.number("organization").toInt(), it.number("venue").toInt(), it.number("atmosphere").toInt(),
        (it["tags"] as? List<*>)?.filterIsInstance<String>().orEmpty(), it["anonymous"] == true,
        reviewVisibility(it["visibility"]),
        (it["photos"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
        it["positiveComment"] as? String ?: "", it["improvementComment"] as? String ?: "")
} ?: FeedbackDetails()
internal fun FeedbackDetails.payload(photos: List<String> = this.photos): Map<String, Any?> = mapOf(
    "organization" to organization, "venue" to venue, "atmosphere" to atmosphere, "tags" to tags,
    "anonymous" to anonymous, "visibility" to visibility.name, "photos" to photos,
    "positiveComment" to positiveComment, "improvementComment" to improvementComment)

internal data class CloudPendingCheckIn(val id: String, val code: String, val registrationKey: String)

/** Pending intent is durable before sending. A lost response never enables a new check-in attempt. */
internal class CloudCheckInPendingStore(context: Context) {
    private val preferences = context.getSharedPreferences("cloud_checkin_pending_v1", Context.MODE_PRIVATE)
    private fun key(actor: String, event: String) = "$actor:$event"
    fun load(actor: String, event: String): CloudPendingCheckIn? = preferences.getString(key(actor,event),null)?.let {
        val value=JSONObject(it)
        CloudPendingCheckIn(value.getString("id"),value.getString("code"),value.optString("registrationKey", ""))
    }
    fun save(actor: String,event: String,value: CloudPendingCheckIn) {
        val previous=load(actor,event)
        check(previous == null || previous.id == value.id) { "Resolve the previous check-in before scanning again." }
        check(preferences.edit().putString(key(actor,event),JSONObject().put("id",value.id).put("code",value.code)
            .put("registrationKey",value.registrationKey).toString()).commit()) { "Could not save check-in progress." }
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
            reviewVisibility(json.optString("visibility")),strings("photos"),
            json.optString("positiveComment"),json.optString("improvementComment")))
    } ?: FeedbackDraft()
    fun save(actor:String,event:String,value:FeedbackDraft) {
        val fields=value.details.payload().toMutableMap(); fields["rating"]=value.rating; fields["comment"]=value.comment
        check(preferences.edit().putString(key(actor,event),JSONObject(fields).toString()).commit()) { "Could not save the review draft." }
    }
    fun clear(actor:String,event:String) { check(preferences.edit().remove(key(actor,event)).commit()) { "Could not clear the saved draft." } }
}

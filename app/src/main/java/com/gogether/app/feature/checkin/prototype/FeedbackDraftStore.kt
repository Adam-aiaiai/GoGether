package com.gogether.app.feature.checkin.prototype

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal object FeedbackStorageLock { val monitor = Any() }

data class FeedbackDraft(val rating: Int = 0, val comment: String = "", val details: FeedbackDetails = FeedbackDetails())

/** Small local drafts share the feedback transaction lock and preference file. */
class FeedbackDraftStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("checkin_prototype_v1", Context.MODE_PRIVATE)
    companion object {
        internal fun key(actor: String, sessionId: String): String {
            require(actor.isNotBlank() && sessionId.isNotBlank())
            fun encode(value: String) = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
            return "draft.${encode(actor)}.${encode(sessionId)}"
        }
    }

    fun load(actor: String, sessionId: String): FeedbackDraft = synchronized(FeedbackStorageLock.monitor) {
        if (PreferencesPrototypeStore(context).feedback(sessionId, actor) != null) return@synchronized FeedbackDraft()
        val raw = prefs.getString(key(actor, sessionId), null) ?: return@synchronized FeedbackDraft()
        val value = JSONObject(raw)
        fun strings(name: String): List<String> = value.optJSONArray(name)?.let { array ->
            (0 until array.length()).map { array.getString(it) }
        }.orEmpty()
        FeedbackDraft(value.optInt("rating"), value.optString("comment"), FeedbackDetails(
            value.optInt("organization"), value.optInt("venue"), value.optInt("atmosphere"), strings("tags"),
            value.optBoolean("anonymous"), if (value.optString("visibility", "PUBLIC") == "PUBLIC")
                FeedbackVisibility.PUBLIC else FeedbackVisibility.ORGANIZER_ONLY, strings("photos")))
            .also { require(it.rating in 0..5); it.details.requireValid() }
    }

    /** Commit before acknowledging or deleting photos; no delayed writer can resurrect a submitted draft. */
    fun save(actor: String, sessionId: String, draft: FeedbackDraft) = synchronized(FeedbackStorageLock.monitor) {
        if (PreferencesPrototypeStore(context).feedback(sessionId, actor) != null) return@synchronized
        require(draft.rating in 0..5)
        draft.details.requireValid()
        require(draft.details.photos.all { FeedbackPhotoStore(context).isOwned(it, actor, sessionId) }) {
            "图片已失效，请移除后重新选择。"
        }
        val details = draft.details
        val value = JSONObject().put("rating", draft.rating).put("comment", draft.comment)
            .put("organization", details.organization).put("venue", details.venue).put("atmosphere", details.atmosphere)
            .put("tags", JSONArray(details.tags)).put("anonymous", details.anonymous)
            .put("visibility", details.visibility.name).put("photos", JSONArray(details.photos))
        val edit = prefs.edit()
        if (draft == FeedbackDraft()) edit.remove(key(actor, sessionId)) else edit.putString(key(actor, sessionId), value.toString())
        check(edit.commit()) { "草稿保存失败，点此重试" }
    }

    internal fun references(actor: String, sessionId: String, photo: String): Boolean =
        prefs.getString(key(actor, sessionId), null)?.contains("\"$photo\"") == true
}

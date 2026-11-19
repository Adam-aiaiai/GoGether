package com.gogether.app.feature.checkin.prototype

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
        val value = JSONObject().put("session", PrototypeSessionStorage.encode(info.session))
            .put("organizer", info.organizerId).put("title", info.title).put("meeting", info.meetingName).put("introduction", info.introduction)
            .put("sourceEventId", info.sourceEventId)
            .put("organizerName", info.organizerDisplayName)
        check(prefs.edit().putString("activity-info.${info.session.id}", value.toString()).commit()) { "Could not save event details. Try again." }
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
        return if (time > 0) PrototypeReceipt(sessionId, userId, time) else null
    }
    override fun save(receipt: PrototypeReceipt) = synchronized(writeLock) {
        require(this.receipt(receipt.sessionId, receipt.userId) == null) { "You are already checked in." }
        require(prefs.edit().putLong("receipt.${receipt.sessionId}.${receipt.userId}", receipt.checkedInAt).commit()) {
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
    override fun feedback(sessionId: String, userId: String): PrototypeFeedback? =
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
                    if (photos == null) emptyList() else (0 until photos.length()).map { index -> photos.getString(index) }))
        }
    override fun feedbackForSession(sessionId: String): List<PrototypeFeedback> = prefs.all.keys
        .filter { it.startsWith("feedback.$sessionId.") }
        .mapNotNull { feedback(sessionId, it.removePrefix("feedback.$sessionId.")) }
    override fun saveFeedback(feedback: PrototypeFeedback) = synchronized(writeLock) {
        val key = "feedback.${feedback.sessionId}.${feedback.userId}"
        require(!prefs.contains(key)) { "You have already reviewed this event." }
        feedback.details.requireValid()
        val photoStore = FeedbackPhotoStore(context)
        require(feedback.details.photos.all { photoStore.isOwned(it, feedback.userId, feedback.sessionId) }) { "This photo is no longer available. Remove it and choose it again." }
        val draftPhotos = runCatching { FeedbackDraftStore(context).load(feedback.userId, feedback.sessionId).details.photos }.getOrDefault(emptyList())
        val value = JSONObject().put("rating", feedback.rating).put("comment", feedback.comment).put("createdAt", feedback.createdAt)
            .put("details", JSONObject().put("organization", feedback.details.organization).put("venue", feedback.details.venue)
                .put("atmosphere", feedback.details.atmosphere).put("tags", JSONArray(feedback.details.tags))
                .put("anonymous", feedback.details.anonymous).put("visibility", feedback.details.visibility.name)
                .put("photos", JSONArray(feedback.details.photos)))
        require(prefs.edit().putString(key, value.toString())
            .remove(FeedbackDraftStore.key(feedback.userId, feedback.sessionId)).commit()) { "Could not save your review. Try again." }
        draftPhotos.filterNot { it in feedback.details.photos }.forEach {
            runCatching { photoStore.removeDraft(it, feedback.userId, feedback.sessionId) }
        }
    }
    fun latestReceipt(userId: String): PrototypeReceipt? = prefs.all.entries
        .filter { it.key.startsWith("receipt.") && it.key.endsWith(".$userId") && it.value is Long }
        .maxByOrNull { it.value as Long }?.let {
            PrototypeReceipt(it.key.removePrefix("receipt.").removeSuffix(".$userId"), userId, it.value as Long)
        }
}

fun interface PositionSource { suspend fun locate(): PositionFix }

/** Foreground, bounded request; removes GPS/network listeners on success, timeout and cancellation. */
class AndroidPositionSource(private val context: Context) : PositionSource {
    override suspend fun locate(): PositionFix {
        require(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED) { "Allow precise location in settings to check the 200 m distance." }
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { manager.allProviders.contains(it) && manager.isProviderEnabled(it) }
        require(providers.isNotEmpty()) { "Location services are off. Enable them and try again." }
        var reason = "Could not get your location. Try in an open area and check location settings."
        return withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!continuation.isActive) return
                        @Suppress("DEPRECATION")
                        val mock = location.isFromMockProvider
                        val fix = PositionFix(MeetingPoint(location.latitude, location.longitude),
                            if (location.hasAccuracy()) location.accuracy.toDouble() else Double.NaN,
                            location.elapsedRealtimeNanos / 1_000_000, mock)
                        try {
                            validatePosition(fix, SystemClock.elapsedRealtime())
                            manager.removeUpdates(this)
                            continuation.resume(fix)
                        } catch (e: IllegalArgumentException) { reason = e.message ?: reason }
                    }
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                    @Deprecated("Legacy Android callback")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                try {
                    providers.forEach { manager.requestLocationUpdates(it, 1_000L, 0f, listener, Looper.getMainLooper()) }
                } catch (e: SecurityException) {
                    manager.removeUpdates(listener)
                    if (continuation.isActive) continuation.resumeWithException(
                        IllegalArgumentException("Location permission has changed. Grant permission again.", e))
                } catch (e: IllegalArgumentException) {
                    manager.removeUpdates(listener)
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } ?: throw IllegalArgumentException(reason)
    }
}

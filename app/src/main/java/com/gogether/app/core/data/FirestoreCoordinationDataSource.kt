package com.gogether.app.core.data

import com.gogether.app.core.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.functions.FirebaseFunctions
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

/** Shared contract for A/B/C integration. No Demo identity is accepted by the server. */
data class SyncedData<T>(val value: T, val fromCache: Boolean, val pendingWrites: Boolean)
data class MyActivities(val joined: List<Event>, val waitlisted: List<Event>, val created: List<Event>)

class FirestoreCoordinationDataSource(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) {
    private val gson = Gson()
    private fun uid() = requireNotNull(auth.currentUser?.uid) { "Sign in to sync activities." }

    private fun <T> watch(query: Query, type: Class<T>): Flow<SyncedData<List<T>>> = callbackFlow {
        val owner = uid()
        val identityListener = FirebaseAuth.AuthStateListener {
            if (it.currentUser?.uid != owner) close(IllegalStateException("Session changed. Start new subscriptions."))
        }
        auth.addAuthStateListener(identityListener)
        val listener = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (auth.currentUser?.uid != owner) return@addSnapshotListener
            if (error != null) { close(error); return@addSnapshotListener }
            if (snapshot != null) {
                try {
                    val values = snapshot.documents.map { document ->
                        gson.fromJson(gson.toJson(document.data.orEmpty() + ("id" to document.id)), type)
                    }
                    trySend(SyncedData(values, snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites()))
                } catch (failure: Exception) { close(failure) }
            }
        }
        awaitClose { listener.remove(); auth.removeAuthStateListener(identityListener) }
    }

    fun notifications(): Flow<SyncedData<List<Notification>>> =
        watch(db.collection("notifications").whereEqualTo("userId", uid()), Notification::class.java)
            .map { it.copy(value = it.value.sortedByDescending { n -> n.createdAt }) }

    fun announcements(eventId: String): Flow<SyncedData<List<Announcement>>> =
        watch(db.collection("announcements").whereEqualTo("eventId", eventId), Announcement::class.java)
            .map { it.copy(value = it.value.sortedByDescending { a -> a.createdAt }) }

    fun myActivities(): Flow<SyncedData<MyActivities>> {
        val owner = uid()
        val events = watch(db.collection("events"), Event::class.java)
        val registrations = watch(db.collection("registrations").whereEqualTo("userId", owner), Registration::class.java)
        return combine(events, registrations) { e, r ->
            val statuses = r.value.associate { it.eventId to it.status }
            val sorted = e.value.sortedByDescending { it.startAt }
            SyncedData(MyActivities(
                sorted.filter { statuses[it.id] == RegistrationStatus.CONFIRMED },
                sorted.filter { statuses[it.id] == RegistrationStatus.WAITLISTED },
                sorted.filter { it.organizerId == owner }), e.fromCache || r.fromCache,
                e.pendingWrites || r.pendingWrites)
        }
    }

    fun participants(eventId: String): Flow<SyncedData<List<Registration>>> =
        watch(db.collection("registrations").whereEqualTo("eventId", eventId), Registration::class.java)

    // Keep requestId for retries of the same submission; generate a new UUID for a new submission.
    suspend fun postAnnouncement(eventId: String, content: String, requestId: String) {
        uid()
        functions.getHttpsCallable("coordinate").call(mapOf("action" to "announcement",
            "eventId" to eventId, "content" to content, "requestId" to requestId)).await()
    }
    suspend fun updateMeeting(eventId: String, update: MeetingUpdate, requestId: String) {
        uid()
        functions.getHttpsCallable("coordinate").call(mapOf("action" to "meeting",
            "eventId" to eventId, "meetingAt" to update.meetingAt, "location" to update.location,
            "notes" to update.notes, "requestId" to requestId)).await()
    }
    suspend fun markAllRead() {
        val owner = uid()
        val unread = db.collection("notifications").whereEqualTo("userId", owner)
            .get().await().documents.filter { it.getBoolean("isRead") != true }
        for (chunk in unread.chunked(450)) {
            require(uid() == owner) { "Session changed. Please retry." }
            val batch = db.batch()
            chunk.forEach { batch.update(it.reference, "isRead", true) }
            batch.commit().await()
        }
    }

    suspend fun markRead(notificationId: String) {
        uid()
        db.collection("notifications").document(notificationId).update("isRead", true).await()
    }
}

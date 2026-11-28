package com.gogether.app.core.data.cloud

import android.content.Context
import android.net.Uri
import com.gogether.app.core.account.AccountMode
import com.gogether.app.core.account.AccountRules
import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.data.FileSnapshotStore
import android.util.AtomicFile
import java.io.File
import com.gogether.app.core.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID

class FirebaseGogetherRepository(context: Context, private val services: FirebaseServices = FirebaseRuntime.services(context)) :
    GogetherRepository, CloudOperations {
    private val context = context.applicationContext
    private val auth = services.auth
    private val preferences = context.getSharedPreferences("cloud-requests-${services.app.name}", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(AppSnapshot(sessionRestoring = true))
    override val state = mutableState.asStateFlow()
    private val mutableSync = MutableStateFlow(CloudSyncState())
    override val sync = mutableSync.asStateFlow()
    override val storage get() = services.storage
    override val accountMode = AccountMode.CONNECTED
    override val storageDescription = "Accounts, activities and messages are shared through the team backend."
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val refreshMutex = Mutex()
    private val accountMutex = Mutex()
    private val cacheLock = Any()
    private fun cacheFile(uid: String): File {
        val hash = fingerprint(uid, "snapshot-cache", mapOf("project" to services.app.options.projectId))
        return File(context.filesDir, "cloud-snapshots/$hash.json")
    }
    private fun clearCache(uid: String) {
        synchronized(cacheLock) { AtomicFile(cacheFile(uid)).delete() }
        synchronized(preferences) {
            val editor = preferences.edit()
            preferences.all.filter { (key, value) -> key.startsWith("actor:request:") && value == uid }
                .keys.forEach { editor.remove("receipt:${it.removePrefix("actor:")}") }
            editor.commit()
        }
    }
    private suspend fun restoreCache(uid: String) {
        val epoch = generation
        val cached = withContext(Dispatchers.IO) { synchronized(cacheLock) {
            runCatching { FileSnapshotStore(cacheFile(uid)).load() }.getOrNull()
        } }
        verify(uid, epoch)
        if (cached != null && cached.currentUserId == uid && cached.currentUser?.status == UserStatus.ACCOUNT &&
            cached.notifications.all { it.userId == uid }) {
            mutableState.value = cached.copy(sessionRestoring = false, sessionError = null)
            mutableSync.value = CloudSyncState(message = "Showing saved data. Reconnecting to the team backend…")
        }
    }
    private val invalidations = Channel<Unit>(Channel.CONFLATED)
    private val listeners = mutableListOf<ListenerRegistration>()
    @Volatile private var observedUid: String? = null
    @Volatile private var generation = 0L
    private val authListener = FirebaseAuth.AuthStateListener { syncIdentity() }
    init {
        auth.addAuthStateListener(authListener)
        scope.launch {
            for (ignored in invalidations) {
                delay(150)
                if (auth.currentUser != null) runCatching { refresh() }
            }
        }
    }
    private fun actor(): String = requireNotNull(auth.currentUser?.uid) { "Sign in first." }
    private fun verify(uid: String, epoch: Long) {
        check(auth.currentUser?.uid == uid && generation == epoch) { "The account changed. Please try again." }
    }
    private fun canonical(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().apply { value.keys.map { it.toString() }.sorted().forEach { put(it, canonical(value[it])) } }
        is Iterable<*> -> org.json.JSONArray().apply { value.forEach { put(canonical(it)) } }
        else -> value
    }
    private fun fingerprint(uid: String, action: String, payload: Map<String, Any?>): String =
        MessageDigest.getInstance("SHA-256").digest("$uid:$action:${canonical(payload)}".toByteArray())
            .joinToString("") { "%02x".format(it) }
    private data class PendingRequest(val key: String, val id: String, val scope: String, val sequence: Long)
    private fun intentGroup(action: String) = when (action) {
        "join", "cancelRegistration" -> "registration"
        "updateEvent", "updateMeeting", "cancelEvent", "bindMeeting" -> "event-update"
        "ensureProfile", "updateProfile" -> "profile"
        else -> action
    }
    private fun intentResource(action: String, payload: Map<String, Any?>): Map<String, Any?> = when {
        action in setOf("createEvent", "postAnnouncement", "askQuestion") -> payload
        payload["eventId"] != null -> mapOf("eventId" to payload["eventId"])
        payload["questionId"] != null -> mapOf("questionId" to payload["questionId"])
        payload["notificationId"] != null -> mapOf("notificationId" to payload["notificationId"])
        else -> emptyMap()
    }
    private val readActions = setOf("snapshot", "reviews", "attendance", "eventInfo", "resolveCheckIn", "mediaRead", "resolveRequest")
    private val inFlight = mutableMapOf<String, Int>()
    private fun removePending(editor: android.content.SharedPreferences.Editor, key: String) {
        editor.remove(key).remove("scope:$key").remove("sequence:$key").remove("actor:$key")
            .remove("action:$key").remove("receipt:$key")
    }
    private fun acknowledge(pending: PendingRequest) = synchronized(preferences) {
        val editor = preferences.edit()
        preferences.all.filter { (name, value) -> name.startsWith("scope:request:") && value == pending.scope }
            .keys.map { it.removePrefix("scope:") }.forEach { candidate ->
                val candidateId = preferences.getString(candidate, null)
                if (preferences.getLong("sequence:$candidate", 0L) <= pending.sequence &&
                    (candidateId == pending.id || candidateId !in inFlight)) removePending(editor, candidate)
            }
        // An unpersisted acknowledgement is safe: the next attempt replays the same server request.
        editor.commit()
    }
    override suspend fun call(action: String, payload: Map<String, Any?>, requestId: String?): Map<String, Any?> {
        val uid = actor(); val epoch = generation
        require("action" !in payload && "requestId" !in payload) { "Invalid request fields." }
        // Reading newly arrived notifications is a new intent even after an older read-all lost its reply.
        val intentPayload = if (action == "markAllRead") payload + mapOf("unreadIds" to mutableState.value.notifications
            .filter { !it.isRead }.map { it.id }.sorted()) else payload
        val key = "request:${fingerprint(uid, action, intentPayload)}"
        val scope = "intent:${fingerprint(uid, intentGroup(action), intentResource(action, payload))}"
        var recovered: Map<String, Any?>? = null
        val pending = if (action in readActions) null else synchronized(preferences) {
            val existing = preferences.getString(key, null)
            val id = requestId ?: existing ?: UUID.randomUUID().toString()
            if (id == existing) preferences.getString("receipt:$key", null)?.let {
                recovered = CloudDto.objectValue(jsonValue(JSONObject(it)))
            }
            val sequence = if (existing == id) preferences.getLong("sequence:$key", 0L) else
                maxOf(System.currentTimeMillis(), preferences.getLong("requestSequence", 0L) + 1)
            val editor = preferences.edit()
            if (existing != id) editor.remove("receipt:$key")
            check(editor.putString(key, id).putString("scope:$key", scope).putString("actor:$key", uid)
                .putString("action:$key", action).putLong("sequence:$key", sequence).putLong("requestSequence", sequence).commit()) {
                "Could not save request progress. Try again."
            }
            inFlight[id] = (inFlight[id] ?: 0) + 1
            PendingRequest(key, id, scope, sequence)
        }
        val id = pending?.id
        try {
            verify(uid, epoch)
            val data = payload + mapOf("action" to action) + (id?.let { mapOf("requestId" to it) } ?: emptyMap())
            val decoded = recovered ?: services.invokeCallable(data)
            verify(uid, epoch)
            if (pending != null) acknowledge(pending)
            return decoded
        } catch (e: Exception) {
            val code = when (e) {
                is FirebaseFunctionsException -> e.code
                is CloudTransportException -> e.code
                else -> null
            }
            if (code == FirebaseFunctionsException.Code.UNAUTHENTICATED && auth.currentUser?.uid == uid && generation == epoch) {
                clearCache(uid)
                auth.signOut(); syncIdentity()
                mutableState.value = AppSnapshot(sessionError = "Your session expired. Sign in again.")
                mutableSync.value = CloudSyncState()
            }
            if (code != null && code !in setOf(FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                    FirebaseFunctionsException.Code.INTERNAL, FirebaseFunctionsException.Code.UNKNOWN)) synchronized(preferences) {
                if (preferences.getString(key, null) == id) preferences.edit().also { removePending(it, key) }.commit()
            }
            if (e is FirebaseAuthException && e.errorCode in setOf("ERROR_USER_DISABLED", "ERROR_USER_NOT_FOUND", "ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN") &&
                auth.currentUser?.uid == uid && generation == epoch) {
                clearCache(uid)
                auth.signOut(); syncIdentity()
                mutableState.value = AppSnapshot(sessionError = "Your session expired. Sign in again.")
                mutableSync.value = CloudSyncState()
            }
            throw e
        } finally {
            if (id != null) synchronized(preferences) {
                val remaining = (inFlight[id] ?: 1) - 1
                if (remaining == 0) inFlight.remove(id) else inFlight[id] = remaining
            }
        }
    }
    private suspend fun recoverCommittedRequests(uid: String, epoch: Long) {
        val candidates = synchronized(preferences) {
            preferences.all.filter { (key, value) -> key.startsWith("request:") && value is String &&
                preferences.getString("actor:$key", null) == uid && value !in inFlight && !preferences.contains("receipt:$key") }
                .entries.take(16).map { it.key to it.value as String }
        }
        for ((key, id) in candidates) {
            val answer = call("resolveRequest", mapOf("requestIdToResolve" to id))
            verify(uid, epoch)
            if (answer["status"] == "COMMITTED") synchronized(preferences) {
                if (preferences.getString(key, null) == id && id !in inFlight &&
                    preferences.getString("action:$key", null) == answer["action"]) {
                    // Keep a one-shot receipt: retrying an announcement after a lost response must not publish it twice.
                    val receipt = JSONObject(CloudDto.objectValue(answer["result"])).toString()
                    preferences.edit().putString("receipt:$key", receipt).commit()
                }
            }
        }
    }
    private fun observe(uid: String) {
        if (listeners.isNotEmpty()) return
        listeners += services.firestore.collection("sync").document("global").addSnapshotListener { _, error ->
            if (auth.currentUser?.uid == uid && error == null) invalidations.trySend(Unit)
        }
        listeners += services.firestore.collection("events").addSnapshotListener { _, error ->
            if (auth.currentUser?.uid == uid) {
                if (error == null) invalidations.trySend(Unit)
                else mutableSync.value = CloudSyncState(message = "Live updates unavailable. Check your connection and retry.")
            }
        }
        listeners += services.firestore.collection("notifications").whereEqualTo("userId", uid).addSnapshotListener { _, error ->
            if (auth.currentUser?.uid == uid) {
                if (error == null) invalidations.trySend(Unit)
                else mutableSync.value = CloudSyncState(message = "Notifications could not sync. Check your connection and retry.")
            }
        }
    }
    override suspend fun refresh() = refreshMutex.withLock {
        val uid = actor(); val epoch = generation
        mutableSync.value = CloudSyncState(refreshing = true)
        try {
            val response = call("snapshot")
            verify(uid, epoch)
            val received = CloudDto.snapshot(CloudDto.objectValue(response["snapshot"]), uid)
            mutableState.value = received
            mutableSync.value = CloudSyncState()
            try {
                withContext(Dispatchers.IO) { synchronized(cacheLock) {
                    verify(uid, epoch)
                    val file = cacheFile(uid)
                    check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "Could not save offline data." }
                    FileSnapshotStore(file).save(received)
                } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (auth.currentUser?.uid == uid && generation == epoch)
                mutableSync.value = CloudSyncState(message = "Shared data loaded, but its offline copy could not be saved.") }
            verify(uid, epoch)
            observe(uid)
            try { recoverCommittedRequests(uid, epoch) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Pending IDs remain retryable; the authoritative snapshot is already loaded. */ }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (auth.currentUser?.uid == uid && generation == epoch) {
                val message = error.message ?: "Could not refresh shared data. Check your connection."
                mutableSync.value = CloudSyncState(message = message)
                mutableState.value = mutableState.value.copy(sessionRestoring = false, sessionError = message)
            }
            throw error
        }
    }
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    private suspend fun <T> mutation(action: String, payload: Map<String, Any?>, decode: (Map<String, Any?>) -> T): Result<T> = attempt {
        val uid = actor(); val epoch = generation
        val result = decode(call(action, payload))
        // A committed operation remains successful even if its follow-up refresh loses the connection.
        try { refresh() } catch (_: Exception) { /* The write is already confirmed, including when this refresh is cancelled. */ }
        verify(uid, epoch)
        result
    }
    private fun signupKey(email: String) = "signup:${fingerprint(email.trim().lowercase(), "profile", emptyMap())}"
    private suspend fun ensureProfile(name: String? = null, interests: Set<Category>? = null): User {
        val profile = auth.currentUser ?: error("Sign in first.")
        val pending = preferences.getString(signupKey(profile.email.orEmpty()), null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val pendingInterests = pending?.optJSONArray("interests")?.let { a -> (0 until a.length()).map { a.getString(it) } }
        val result = call("ensureProfile", mapOf("name" to (name ?: pending?.optString("name") ?: profile.displayName ?: "Member"),
            "interests" to (interests?.map { it.name } ?: pendingInterests ?: emptyList<String>())))
        val user = CloudDto.user(CloudDto.objectValue(result["user"]))
        preferences.edit().remove(signupKey(profile.email.orEmpty())).commit()
        return user
    }
    private suspend fun <T> accountAction(block: suspend () -> T): Result<T> = attempt {
        accountMutex.withLock {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val message = error.message ?: "Could not connect to your account. Retry the connection."
                val current = mutableState.value
                mutableState.value = if (current.currentUser != null && current.currentUserId == auth.currentUser?.uid)
                    current.copy(sessionRestoring = false, sessionError = message) else AppSnapshot(sessionError = message)
                mutableSync.value = CloudSyncState(message = if (mutableState.value.currentUser != null)
                    "Showing saved data. $message" else message)
                throw error
            }
        }
    }
    override suspend fun restoreAccountSession(): Result<Unit> = accountAction {
        if (auth.currentUser == null) mutableState.value = AppSnapshot()
        else { syncIdentity(); restoreCache(actor()); ensureProfile(); refresh() }
    }
    override suspend fun signIn(email: String, password: String): Result<Unit> = accountAction {
        AccountRules.password(password)
        auth.signInWithEmailAndPassword(AccountRules.email(email), password).await()
        syncIdentity(); restoreCache(actor()); ensureProfile(); refresh()
    }
    private fun syncIdentity() {
        val uid = auth.currentUser?.uid
        if (observedUid != uid) {
            val previous = observedUid
            observedUid = uid; generation++
            listeners.forEach(ListenerRegistration::remove); listeners.clear()
            mutableState.value = AppSnapshot(sessionRestoring = uid != null)
            mutableSync.value = CloudSyncState()
            // Even an external Firebase sign-out/account switch must remove the former account's cached private data.
            if (previous != null) clearCache(previous)
        }
    }
    override suspend fun signUp(email: String, password: String, name: String, interests: Set<Category>): Result<User> = accountAction {
        val cleanName = AccountRules.name(name); AccountRules.password(password, creating = true)
        val cleanEmail = AccountRules.email(email)
        val pending = JSONObject().put("name", cleanName).put("interests", org.json.JSONArray(interests.map { it.name })).toString()
        check(preferences.edit().putString(signupKey(cleanEmail), pending).commit()) { "Could not save account setup progress." }
        auth.createUserWithEmailAndPassword(cleanEmail, password).await()
        syncIdentity()
        // Profile creation is authoritative on the server; intended fields survive interrupted setup.
        val user = ensureProfile(cleanName, interests)
        runCatching { auth.currentUser?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(cleanName).build())?.await() }
        refresh(); user
    }
    override suspend fun updateProfile(name: String, interests: Set<Category>) = mutation("updateProfile",
        mapOf("name" to name, "interests" to interests.map { it.name })) { CloudDto.user(CloudDto.objectValue(it["user"])) }
    override suspend fun updateProfile(profile: ProfileUpdate): Result<User> = attempt {
        val uid = actor(); val epoch = generation
        require(profile.signature.trim().length <= 100) { "Use a signature of up to 100 characters." }
        val avatar = sharedImage(profile.avatarUri, "profiles")
        val cover = sharedImage(profile.coverUri, "profiles")
        verify(uid, epoch)
        mutation("updateProfile", mapOf("name" to profile.name, "interests" to profile.interests.map { it.name },
            "avatarUri" to avatar, "coverUri" to cover, "gender" to profile.gender.name,
            "showGender" to profile.showGender, "signature" to profile.signature.trim())) {
            CloudDto.user(CloudDto.objectValue(it["user"]))
        }.getOrThrow()
    }
    override suspend fun logout(): Result<Unit> = attempt {
        accountMutex.withLock {
            val uid = auth.currentUser?.uid
            auth.signOut(); syncIdentity(); if (uid != null) clearCache(uid)
            mutableState.value = AppSnapshot(); mutableSync.value = CloudSyncState()
        }
    }
    override suspend fun login(userId: String): Result<Unit> = Result.failure(IllegalStateException("Use email and password to sign in."))
    override suspend fun register(name: String, interests: Set<Category>): Result<User> = Result.failure(IllegalStateException("Use account registration."))
    private suspend fun sharedDraft(draft: EventDraft): EventDraft {
        return draft.copy(imageUri = sharedImage(draft.imageUri, "covers"))
    }
    private suspend fun sharedImage(imageUri: String, folder: String): String {
        if (!imageUri.startsWith("content://") && !imageUri.startsWith("file://")) return imageUri
        val uid = actor(); val epoch = generation
        val uri = Uri.parse(imageUri)
        val type = context.contentResolver.getType(uri) ?: "image/jpeg"
        require(type in setOf("image/jpeg", "image/png", "image/webp")) { "Choose a JPEG, PNG or WebP cover." }
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n)
                    require(output.size() <= 2 * 1024 * 1024) { "Choose a cover smaller than 2 MB." } }
                output.toByteArray()
            } ?: error("Could not open the selected cover.")
        }
        val contentHash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val key = "${if (folder == "covers") "cover" else folder}:$uid:$contentHash"
        val path = preferences.getString(key, null) ?: "$folder/$uid/${UUID.randomUUID()}".also {
            check(preferences.edit().putString(key, it).commit()) { "Could not save upload progress." }
        }
        verify(uid, epoch)
        val reference = storage.reference.child(path)
        val previous = try { reference.metadata.await() } catch (error: StorageException) {
            if (error.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) null else throw error
        }
        if (previous == null) reference.putBytes(bytes, StorageMetadata.Builder().setContentType(type)
            .setCustomMetadata("sha256", contentHash).build()).await()
        else require(previous.getCustomMetadata("sha256") == contentHash) { "The cover upload changed. Choose the image again." }
        verify(uid, epoch)
        return path
    }
    override suspend fun createEvent(draft: EventDraft): Result<Event> = attempt {
        mutation("createEvent", mapOf("draft" to CloudDto.draft(sharedDraft(draft)))) { CloudDto.event(CloudDto.objectValue(it["event"])) }.getOrThrow()
    }
    override suspend fun updateEvent(eventId: String, draft: EventDraft): Result<Event> = attempt {
        mutation("updateEvent", mapOf("eventId" to eventId, "draft" to CloudDto.draft(sharedDraft(draft)))) { CloudDto.event(CloudDto.objectValue(it["event"])) }.getOrThrow()
    }
    override suspend fun join(eventId: String) = mutation("join", mapOf("eventId" to eventId)) { CloudDto.registration(CloudDto.objectValue(it["registration"])) }
    override suspend fun cancelRegistration(eventId: String) = mutation("cancelRegistration", mapOf("eventId" to eventId)) { Unit }
    override suspend fun cancelEvent(eventId: String) = mutation("cancelEvent", mapOf("eventId" to eventId)) { Unit }
    override suspend fun postAnnouncement(eventId: String, content: String) = mutation("postAnnouncement", mapOf("eventId" to eventId, "content" to content)) { Unit }
    override suspend fun askQuestion(eventId: String, content: String) = mutation("askQuestion", mapOf("eventId" to eventId, "content" to content)) { Unit }
    override suspend fun answerQuestion(questionId: String, content: String) = mutation("answerQuestion", mapOf("questionId" to questionId, "content" to content)) { Unit }
    override suspend fun updateMeeting(eventId: String, update: MeetingUpdate) = mutation("updateMeeting", mapOf("eventId" to eventId,
        "meetingAt" to update.meetingAt, "location" to update.location, "notes" to update.notes)) { Unit }
    override suspend fun markRead(notificationId: String) = mutation("markRead", mapOf("notificationId" to notificationId)) { Unit }
    override suspend fun markAllRead() = mutation("markAllRead", emptyMap()) { Unit }
    override suspend fun checkIn(eventId: String): Result<CheckIn> = Result.failure(IllegalStateException("Scan the organizer's current QR code to check in."))
    override suspend fun submitFeedback(eventId: String, rating: Int, comment: String) = mutation("submitFeedback",
        mapOf("eventId" to eventId, "rating" to rating, "comment" to comment, "details" to emptyMap<String, Any?>())) {
        CloudDto.feedback(CloudDto.objectValue(it["feedback"]))
    }
    fun close() { auth.removeAuthStateListener(authListener); listeners.forEach(ListenerRegistration::remove); scope.cancel() }
}

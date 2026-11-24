package com.gogether.app.core.data.cloud

import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.StateFlow

/** Shared authenticated network seam used by the independent attendance and review screens. */
interface CloudOperations {
    val storage: FirebaseStorage
    val sync: StateFlow<CloudSyncState>
    suspend fun call(action: String, payload: Map<String, Any?> = emptyMap(), requestId: String? = null): Map<String, Any?>
    suspend fun refresh()
}

data class CloudSyncState(val refreshing: Boolean = false, val message: String? = null)

package com.gogether.app.feature.checkin.prototype

/** E-owned read seam. The local adapter uses mock membership and persisted local receipts. */
fun interface AttendanceSource {
    suspend fun load(actor: String, session: PrototypeSession): List<PrototypeAttendee>
}

data class AttendanceLoadState(
    val session: PrototypeSession? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val updatedAt: Long? = null,
)

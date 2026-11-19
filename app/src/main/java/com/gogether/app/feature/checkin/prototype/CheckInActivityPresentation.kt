package com.gogether.app.feature.checkin.prototype

import androidx.compose.runtime.staticCompositionLocalOf

/** Install the same B adapter used by the backend for every nested E page and private image reader. */
internal val LocalCheckInActivities = staticCompositionLocalOf<CheckInActivitySource> { LegacyCheckInActivities }

internal fun CheckInActivitySource.infoOrNull(session: PrototypeSession?) = session?.let { runCatching { activity(it) }.getOrNull() }

package com.gogether.app.feature.coordination

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.gogether.app.core.model.AppSnapshot
import com.gogether.app.core.ui.*

/** A's profile and the standalone inbox share D's notification component. */
@Composable
fun NotificationSection(state: AppSnapshot, busy: Boolean, read: (String) -> Unit,
                        readAll: () -> Unit, openEvent: (String) -> Unit, explore: () -> Unit) {
    InboxContent(state, busy, read, readAll, openEvent, explore)
}

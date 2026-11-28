package com.gogether.app.feature.checkin.prototype

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.gogether.app.core.ui.AppPhotoGallery

@Composable
internal fun FeedbackPhotoPreview(photos: List<String>, initialPage: Int, viewer: String, sessionId: String,
    close: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val activities = LocalCheckInActivities.current
    val loadPhoto = remember(viewer, sessionId, activities) {
        val store = FeedbackPhotoStore(context, activities)
        val loader: suspend (String) -> android.graphics.Bitmap? = { id -> store.bitmap(id, viewer, sessionId, 1600) }
        loader
    }
    AppPhotoGallery(photos, initialPage, loadPhoto, close)
}

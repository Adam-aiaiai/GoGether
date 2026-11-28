package com.gogether.app.feature.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.gogether.app.GogetherApplication
import com.gogether.app.R
import com.gogether.app.core.data.cloud.CloudOperations
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

internal fun defaultCover(context: Context) = "android.resource://${context.packageName}/${R.drawable.profile_default_cover}"

/** Copy the selected image into durable app storage; a picker grant may expire after restart. */
internal suspend fun importProfilePhoto(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val bitmap = requireNotNull(loadProfilePhoto(context, uri.toString())) { "Could not open this photo. Try another image." }
    val directory = File(context.filesDir, "profile_photos").apply { mkdirs() }
    val file = File(directory, "${UUID.randomUUID()}.jpg")
    try {
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
        require(file.length() <= 2 * 1024 * 1024) { "Choose a smaller image." }
        Uri.fromFile(file).toString()
    } catch (error: Exception) { file.delete(); throw error }
}

internal suspend fun loadProfilePhoto(context: Context, source: String): Bitmap? = withContext(Dispatchers.IO) {
    if (source.isBlank()) return@withContext null
    val cloud = (context.applicationContext as? GogetherApplication)?.repository as? CloudOperations
    val bytes = if (source.startsWith("profiles/") || source.startsWith("covers/") || source.startsWith("gs://")) {
        val storage = requireNotNull(cloud) { "Sign in to load this photo." }.storage
        (if (source.startsWith("gs://")) storage.getReferenceFromUrl(source) else storage.reference.child(source))
            .getBytes(2L * 1024 * 1024).await()
    } else null
    fun stream() = bytes?.inputStream() ?: context.contentResolver.openInputStream(Uri.parse(source))
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    stream().use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    val bitmap = stream().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: return@withContext null
    val orientation = runCatching { stream().use { ExifInterface(requireNotNull(it)).getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix().apply { when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
    } }
    if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

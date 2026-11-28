package com.gogether.app.feature.checkin.prototype

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** App-private copies: no external storage permission and no original image modification. */
class FeedbackPhotoStore(private val context: Context, private val activities: CheckInActivitySource = LegacyCheckInActivities) {
    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
        internal const val ORPHAN_GRACE_MS = 24L * 60 * 60 * 1000
        private val activeImports = mutableSetOf<String>()
    }
    private val directory get() = File(context.filesDir, "feedback_photos").also { it.mkdirs() }
    private fun file(id: String, suffix: String): File {
        require(photoIdPattern.matches(id)) { "Invalid photo ID." }
        return File(directory, "$id.$suffix")
    }
    private fun metadata(id: String): JSONObject? = runCatching { JSONObject(file(id, "json").readText()) }.getOrNull()
    fun isOwned(id: String, actor: String, sessionId: String): Boolean {
        val meta = metadata(id) ?: return false
        return meta.optString("owner") == actor && meta.optString("session") == sessionId && file(id, "jpg").isFile
    }

    suspend fun importUris(actor: String, sessionId: String, uris: List<Uri>, remaining: Int): List<String> = withContext(Dispatchers.IO) {
        require(uris.size <= remaining && remaining in 1..3) { "You can add up to 3 photos. Select fewer photos." }
        val imported = mutableListOf<String>()
        try {
            for (uri in uris.distinct()) {
                currentCoroutineContext().ensureActive()
                val mime = context.contentResolver.getType(uri)
                require(mime?.startsWith("image/") == true) { "Choose a photo. Videos are not supported." }
                val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Could not read this photo. Choose it again." }
                imported += input.use { importImage(actor, sessionId, it) }
            }
            currentCoroutineContext().ensureActive()
            imported.toList()
        } catch (error: Throwable) {
            imported.forEach { removeDraft(it, actor, sessionId) }
            throw error
        }
    }

    internal suspend fun importImage(actor: String, sessionId: String, input: InputStream): String = withContext(Dispatchers.IO) {
        require(actor.isNotBlank() && sessionId.isNotBlank())
        val id = UUID.randomUUID().toString()
        val source = file(id, "tmp")
        val output = file(id, "jpg")
        synchronized(FeedbackStorageLock.monitor) { activeImports.add(id) }
        try {
            // Ownership survives an interrupted copy, allowing scoped recovery next time.
            file(id, "json").writeText(JSONObject().put("owner", actor).put("session", sessionId).toString())
            source.outputStream().use { destination ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    require(total <= MAX_BYTES) { "Each photo must be 10 MB or less. Choose another." }
                    destination.write(buffer, 0, count)
                }
                require(total > 0) { "This photo is empty. Choose another." }
            }
            val decoded = decode(source, 1600) ?: throw IllegalArgumentException("Could not read this photo. Choose another.")
            try {
                // Normalize orientation and drop source EXIF (including GPS) from the stored copy.
                val clean = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
                try {
                    Canvas(clean).apply { drawColor(Color.WHITE); drawBitmap(decoded, 0f, 0f, null) }
                    output.outputStream().use { require(clean.compress(Bitmap.CompressFormat.JPEG, 90, it)) { "Could not save the photo. Try again." } }
                } finally { clean.recycle() }
            } finally { decoded.recycle() }
            currentCoroutineContext().ensureActive()
            file(id, "json").writeText(JSONObject().put("owner", actor).put("session", sessionId).toString())
            id
        } catch (error: Throwable) {
            output.delete(); file(id, "json").delete()
            throw error
        } finally {
            source.delete()
            synchronized(FeedbackStorageLock.monitor) { activeImports.remove(id) }
        }
    }

    fun canRead(id: String, viewer: String, sessionId: String): Boolean {
        val meta = metadata(id) ?: return false
        if (viewer.isBlank() || meta.optString("session") != sessionId || !file(id, "jpg").isFile) return false
        val owner = meta.optString("owner")
        if (viewer == owner) return true
        val store = PreferencesPrototypeStore(context)
        val review = store.feedback(sessionId, owner) ?: return false
        if (store.isBlocked(viewer, owner)) return false
        val session = store.sessions(owner).find { it.id == sessionId }
        val organizer = session?.let { runCatching {
            val info = if (activities === LegacyCheckInActivities) store.activityInfo(sessionId) ?: activities.activity(it) else activities.activity(it)
            require(info.session == it)
            info.organizerId
        }.getOrNull() }
        return id in review.details.photos && review.visibleTo(viewer, organizer.orEmpty()) != null
    }

    suspend fun bitmap(id: String, viewer: String, sessionId: String, size: Int = 320): Bitmap? = withContext(Dispatchers.IO) {
        if (!canRead(id, viewer, sessionId)) return@withContext null
        runCatching { decode(file(id, "jpg"), size) }.getOrNull()
    }

    fun removeDraft(id: String, actor: String, sessionId: String) = synchronized(FeedbackStorageLock.monitor) {
        val meta = metadata(id) ?: return@synchronized
        if (meta.optString("owner") != actor || meta.optString("session") != sessionId || id in activeImports) return@synchronized
        if (FeedbackDraftStore(context).references(actor, sessionId, id)) return@synchronized
        if (id in PreferencesPrototypeStore(context).feedback(sessionId, actor)?.details?.photos.orEmpty()) return@synchronized
        file(id, "jpg").delete(); file(id, "tmp").delete(); file(id, "json").delete()
        Unit
    }

    /** Only old unreferenced files belonging to this exact user/activity are reclaimed. */
    fun cleanUnreferenced(actor: String, sessionId: String, now: Long = System.currentTimeMillis()) =
        synchronized(FeedbackStorageLock.monitor) {
            directory.listFiles()?.filter { it.extension == "json" && photoIdPattern.matches(it.nameWithoutExtension) }
                ?.forEach { meta ->
                    if (now - meta.lastModified() >= ORPHAN_GRACE_MS) {
                        // A malformed record is kept rather than risking removal of someone else's data.
                        runCatching { removeDraft(meta.nameWithoutExtension, actor, sessionId) }
                    }
                }
            Unit
        }

    private fun decode(source: File, maximum: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(1.0, maximum.toDouble() / max(info.size.width, info.size.height))
            decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maximum) sample *= 2
        val bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val orientation = runCatching { ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}

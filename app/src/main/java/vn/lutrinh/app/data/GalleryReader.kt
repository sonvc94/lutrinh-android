package vn.lutrinh.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.lutrinh.app.domain.model.PhotoSource
import vn.lutrinh.app.domain.usecase.GalleryPhoto
import vn.lutrinh.app.domain.usecase.PhotoGrouping
import vn.lutrinh.app.domain.usecase.PhotoWindow
import vn.lutrinh.app.domain.usecase.StopPhotos
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How much of the gallery the app may read (R19). */
enum class GalleryAccess {
    /** READ_MEDIA_IMAGES (Android 13+) or READ_EXTERNAL_STORAGE (≤ 12). */
    FULL,

    /** Android 14+ "limited access": only the photos the user selected are visible. */
    PARTIAL,
    NONE,
}

/**
 * Reads the phone's gallery (MediaStore) for "Ảnh chụp trong lúc dừng" (R19, docs/06-photos.md
 * "Gallery photos of a stop"). Read-only; nothing is copied until the step is saved, and then
 * through [PhotoStorage.import] like any picked photo.
 */
class GalleryReader(context: Context) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver

    fun access(): GalleryAccess {
        fun granted(p: String) = ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> GalleryAccess.FULL
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> GalleryAccess.PARTIAL
            Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> GalleryAccess.FULL
            else -> GalleryAccess.NONE
        }
    }

    /**
     * Gallery images that may lie in [w]: `DATE_TAKEN` within [StopPhotos.queryRange], or no
     * `DATE_TAKEN` at all (MediaStore leaves it empty when the EXIF date has no offset and the
     * file's modification time is far from it, e.g. photos copied onto the phone later); newest
     * first, at most [MAX_CANDIDATES]. The capture time of each is read like at import (EXIF
     * `DateTimeOriginal` … as local wall clock, docs/06), else `DATE_TAKEN` in [zone]; no date =
     * unknown. [StopPhotos.offer] does the exact filtering. Empty without access.
     */
    suspend fun candidates(w: PhotoWindow, zone: ZoneId): List<GalleryPhoto> = withContext(Dispatchers.IO) {
        if (access() == GalleryAccess.NONE) return@withContext emptyList()
        val range = StopPhotos.queryRange(w, zone)
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val taken = MediaStore.Images.Media.DATE_TAKEN
        val projection = arrayOf(
            MediaStore.Images.Media._ID, taken, MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.SIZE,
        )
        val selection = "($taken BETWEEN ? AND ?) OR $taken IS NULL OR $taken = 0"
        val args = arrayOf(range.first.toString(), range.last.toString())
        val order = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        val rows = mutableListOf<Row>()
        runCatching {
            resolver.query(collection, projection, selection, args, order)?.use { c ->
                while (c.moveToNext() && rows.size < MAX_CANDIDATES) {
                    rows += Row(
                        id = c.getLong(0),
                        takenMs = c.getLong(1).takeIf { !c.isNull(1) && it > 0 },
                        modified = c.getLong(2),
                        size = if (c.isNull(3)) null else c.getLong(3),
                    )
                }
            }
        }
        rows.map { r ->
            val uri = ContentUris.withAppendedId(collection, r.id)
            val takenAt = cachedExifTime(r, uri)
                ?: r.takenMs?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).truncatedTo(ChronoUnit.MINUTES) }
            GalleryPhoto(r.id.toString(), uri.toString(), takenAt, r.size)
        }
    }

    private data class Row(val id: Long, val takenMs: Long?, val modified: Long, val size: Long?)

    /** EXIF times already read, by MediaStore id; dropped when the file's modification time changes. */
    private val exifCache = object : LinkedHashMap<Long, Pair<Long, LocalDateTime?>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Pair<Long, LocalDateTime?>>?) = size > 2 * MAX_CANDIDATES
    }

    private fun cachedExifTime(r: Row, uri: Uri): LocalDateTime? {
        synchronized(exifCache) { exifCache[r.id] }?.takeIf { it.first == r.modified }?.let { return it.second }
        return exifTime(uri).also { synchronized(exifCache) { exifCache[r.id] = r.modified to it } }
    }

    private fun exifTime(uri: Uri): LocalDateTime? = runCatching {
        resolver.openInputStream(uri)?.use { ExifInterface(it) }?.let {
            PhotoGrouping.parseExifDateTime(it.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                ?: PhotoGrouping.parseExifDateTime(it.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED))
                ?: PhotoGrouping.parseExifDateTime(it.getAttribute(ExifInterface.TAG_DATETIME))
        }
    }.getOrNull()

    /**
     * Origin of a picked or gallery URI for de-duplication (R19): the MediaStore id when the URI
     * names one (gallery `content://media/external/images/media/<id>`, documents `image:<id>`;
     * Photo Picker URIs give none) and the original byte size. Never throws.
     */
    suspend fun sourceOf(uri: Uri): PhotoSource = withContext(Dispatchers.IO) {
        val id = runCatching { mediaId(uri) }.getOrNull()
        val size = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
        }.getOrNull()
        PhotoSource(id, size?.takeIf { it > 0 })
    }

    private fun mediaId(uri: Uri): String? = when (uri.authority) {
        MediaStore.AUTHORITY -> {
            val seg = uri.pathSegments
            // external[_primary]/images/media/<id>; picker URIs (media/picker/…) do not qualify.
            if (seg.size == 4 && seg[1] == "images" && seg[2] == "media") seg[3].takeIf { s -> s.all { it.isDigit() } } else null
        }
        MEDIA_DOCUMENTS -> DocumentsContract.getDocumentId(uri)
            .takeIf { it.startsWith("image:") }?.removePrefix("image:")?.takeIf { s -> s.isNotEmpty() && s.all { it.isDigit() } }
        else -> null
    }

    companion object {
        /** What to request for [GalleryAccess.FULL] (and, on Android 14+, to allow the partial choice). */
        fun permissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        /** Upper bound of rows read per query (a day either side of a stop is rarely more). */
        const val MAX_CANDIDATES = 500
        private const val MEDIA_DOCUMENTS = "com.android.providers.media.documents"
    }
}

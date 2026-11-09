package vn.lutrinh.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.lutrinh.app.data.photo.Orientation
import vn.lutrinh.app.data.photo.PhotoImportPlan
import vn.lutrinh.app.data.photo.PixelSize
import vn.lutrinh.app.domain.repository.PhotoFileStore
import vn.lutrinh.app.domain.usecase.VideoProblem
import vn.lutrinh.app.domain.usecase.VideoRules
import java.io.File
import java.util.UUID

/**
 * Photos are COPIED from the system Photo Picker into app-private storage
 * (`<filesDir>/photos/<uuid>.<ext>`), upright, downscaled and without location EXIF. The database stores only the file name, so the
 * photo survives app restarts and does not depend on the picker's temporary URI grant.
 * No storage permission is needed (docs/06-photos.md). Short videos (R29) live in the same folder
 * (`<uuid>.mp4`), copied byte for byte.
 */
class PhotoStorage(private val context: Context) : PhotoFileStore {
    private val resolver = context.contentResolver
    private val dir = File(context.filesDir, DIR).apply { mkdirs() }
    private val cacheDir = context.cacheDir

    fun fileFor(fileName: String): File = File(dir, fileName)

    /** The folder of every stored photo (backup/restore, R26). */
    val photoDir: File get() = dir

    /**
     * Imports one picked item; returns the new file name, or null when it cannot be read.
     * The bytes are first copied to a temp file, then processed (docs/06-photos.md):
     * EXIF orientation applied, long edge <= 2560 px, location EXIF removed.
     */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("import", ".tmp", cacheDir)
        try {
            val copied = resolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            if (copied == null) null else process(temp, extFor(resolver.getType(uri)))
        } catch (e: Throwable) { // IO errors, decoder errors, out of memory on huge images
            null
        } finally {
            temp.delete()
        }
    }

    /** R29: the picked item is a video (Photo Picker "photos and videos"). */
    fun isVideo(uri: Uri): Boolean = runCatching { resolver.getType(uri) }.getOrNull()?.startsWith("video/") == true

    /**
     * R29: imports one picked or recorded video AS IS (no trimming, no re-encoding) when it is at
     * most [VideoRules.MAX_SECONDS] long and [VideoRules.MAX_BYTES] big; otherwise nothing is
     * copied and the reason is returned. Length and size are checked before the copy, the size
     * again while copying (a provider may not report it). A `file:` URI (our own capture file in
     * the cache) is copied too, not renamed: a file moved out of the cache keeps the cache's
     * group and would still be counted as cache by the system; the capture file is deleted after.
     */
    suspend fun importVideo(uri: Uri): VideoImport = withContext(Dispatchers.IO) {
        val ownFile = if (uri.scheme == "file") uri.path?.let(::File) else null
        val size = ownFile?.length() ?: sizeOf(uri)
        val meta = runCatching { VideoMeta.read(context, uri) }.getOrNull()
        VideoRules.check(meta?.durationMs, size)?.let { return@withContext VideoImport.Refused(it, meta?.durationMs, size) }
        val durationMs = meta!!.durationMs!!
        val name = "${UUID.randomUUID()}.${videoExtFor(ownFile?.extension ?: resolver.getType(uri))}"
        val out = File(dir, name)
        try {
            val copied = resolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> copyCapped(input, output) }
            }
            when {
                copied == null -> { out.delete(); VideoImport.Refused(VideoProblem.UNREADABLE, durationMs, size) }
                copied < 0 -> { out.delete(); VideoImport.Refused(VideoProblem.TOO_LARGE, durationMs, null) }
                else -> VideoImport.Added(name, durationMs, meta.takenAt)
            }
        } catch (e: Throwable) {
            out.delete()
            VideoImport.Refused(VideoProblem.UNREADABLE, durationMs, size)
        }
    }

    /** Copies at most [VideoRules.MAX_BYTES]; returns the byte count, or -1 when the source is bigger. */
    private fun copyCapped(input: java.io.InputStream, output: java.io.OutputStream): Long {
        val buf = ByteArray(DEFAULT_BUFFER_SIZE * 8)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return total
            total += n
            if (VideoRules.exceedsSize(total)) return -1
            output.write(buf, 0, n)
        }
    }

    private fun sizeOf(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    private fun videoExtFor(mimeOrExt: String?) = when (mimeOrExt?.lowercase()) {
        "video/3gpp", "3gp" -> "3gp"
        "video/webm", "webm" -> "webm"
        "video/quicktime", "mov" -> "mov"
        "video/x-matroska", "mkv" -> "mkv"
        else -> "mp4"
    }

    private fun extFor(mime: String?) = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        "image/gif" -> "gif"
        else -> "jpg"
    }

    /** Turns the temp copy into a file in [dir]; returns its name. */
    private fun process(temp: File, ext: String): String {
        // GIF: may be animated and carries no EXIF -> kept as is.
        if (ext == "gif") return keep(temp, ext)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(temp.path, bounds)
        val source = PixelSize(bounds.outWidth, bounds.outHeight)
        // Not decodable by the platform (e.g. HEIC before Android 9): keep the original bytes.
        if (source.width <= 0 || source.height <= 0) return keep(temp, ext)

        val exif = runCatching { ExifInterface(temp) }.getOrNull()
        val orientation = Orientation.fromExif(
            exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ?: ExifInterface.ORIENTATION_NORMAL,
        )
        val canStripInPlace = ext in STRIPPABLE
        if (!PhotoImportPlan.needsReencode(source, orientation, canStripInPlace)) {
            val name = keep(temp, ext)
            val stripped = runCatching { stripLocation(fileFor(name)) }.isSuccess
            if (stripped) return name
            fileFor(name).delete() // could not rewrite the metadata -> fall back to re-encoding
        }
        return reencode(temp, source, orientation)
    }

    private fun keep(temp: File, ext: String): String {
        val name = "${UUID.randomUUID()}.$ext"
        temp.copyTo(File(dir, name))
        return name
    }

    /** Removes every GPS tag in place (JPEG / PNG / WebP). Throws when the file cannot be rewritten. */
    private fun stripLocation(file: File) {
        val exif = ExifInterface(file)
        if (exif.latLong == null && GPS_TAGS.none { exif.getAttribute(it) != null }) return
        GPS_TAGS.forEach { exif.setAttribute(it, null) }
        exif.saveAttributes()
    }

    /** Decodes (subsampled), scales to the target size, applies the orientation, writes a new file without EXIF. */
    private fun reencode(temp: File, source: PixelSize, orientation: Orientation): String {
        val opts = BitmapFactory.Options().apply { inSampleSize = PhotoImportPlan.sampleSize(source) }
        val decoded = BitmapFactory.decodeFile(temp.path, opts) ?: error("cannot decode")
        val target = PhotoImportPlan.scaledSize(source)
        val scale = target.longEdge.toFloat() / maxOf(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postRotate(orientation.rotation.toFloat())
            if (orientation.mirror) postScale(-1f, 1f)
        }
        val upright = if (matrix.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        // Keep transparency (PNG/WebP with alpha) lossless; everything else becomes JPEG.
        val png = upright.hasAlpha()
        val name = "${UUID.randomUUID()}.${if (png) "png" else "jpg"}"
        val out = File(dir, name)
        try {
            out.outputStream().use { stream ->
                val ok = upright.compress(
                    if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    PhotoImportPlan.JPEG_QUALITY,
                    stream,
                )
                check(ok) { "encode failed" }
            }
        } catch (e: Throwable) {
            out.delete()
            throw e
        } finally {
            if (upright !== decoded) upright.recycle()
            decoded.recycle()
        }
        return name
    }

    /** Writes generated bytes (sample data). */
    suspend fun write(bytes: ByteArray, ext: String = "jpg"): String = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.$ext"
        File(dir, name).writeBytes(bytes)
        name
    }

    override suspend fun delete(fileNames: Collection<String>) {
        if (fileNames.isEmpty()) return
        withContext(Dispatchers.IO) {
            fileNames.forEach { name ->
                // Only plain file names are accepted: never follow a path out of the photo dir.
                if (name.isNotBlank() && !name.contains('/') && !name.contains("..")) File(dir, name).delete()
            }
        }
    }

    companion object {
        const val DIR = "photos"

        /** Formats whose EXIF ExifInterface can rewrite in place. */
        private val STRIPPABLE = setOf("jpg", "png", "webp")

        private val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_VERSION_ID, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_STATUS, ExifInterface.TAG_GPS_MEASURE_MODE, ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_SPEED_REF, ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_IMG_DIRECTION_REF, ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_MAP_DATUM, ExifInterface.TAG_GPS_DEST_LATITUDE_REF, ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF, ExifInterface.TAG_GPS_DEST_LONGITUDE, ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_BEARING, ExifInterface.TAG_GPS_DEST_DISTANCE_REF, ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_DIFFERENTIAL, ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
        )
    }
}

/** Result of [PhotoStorage.importVideo] (R29). */
sealed interface VideoImport {
    data class Added(val fileName: String, val durationMs: Long, val takenAt: java.time.LocalDateTime?) : VideoImport

    /** Nothing was copied. [durationMs]/[sizeBytes] as far as they were read (for the message). */
    data class Refused(val problem: VideoProblem, val durationMs: Long?, val sizeBytes: Long?) : VideoImport
}

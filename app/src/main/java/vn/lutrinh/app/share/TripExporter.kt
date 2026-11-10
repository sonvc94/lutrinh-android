package vn.lutrinh.app.share

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.lutrinh.app.data.PhotoStorage
import vn.lutrinh.app.data.VideoMeta
import vn.lutrinh.app.data.photo.Orientation
import vn.lutrinh.app.data.photo.PhotoImportPlan
import vn.lutrinh.app.data.photo.PixelSize
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.R
import vn.lutrinh.app.ui.common.dateRange
import vn.lutrinh.app.domain.usecase.TripPage
import vn.lutrinh.app.map.MapEngine
import vn.lutrinh.app.map.StaticTripMap
import vn.lutrinh.app.map.basemap.BasemapProvider
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Android side of "Chia sẻ chuyến đi" (R18): renders the map and the summary image, encodes
 * photos for the page, writes the files to `cacheDir/share/` (temporary, shared through
 * [FileProvider]) and saves copies to shared storage (MediaStore). The rules live in
 * domain/usecase/TripShare.kt and TripPage.kt.
 */
class TripExporter(
    context: Context,
    private val photos: PhotoStorage,
    private val engine: MapEngine,
    /** The basemap of share images/pages: an offline-capable one ("Bản đồ"), so a share works in airplane mode. */
    val basemap: BasemapProvider,
) {
    private val context = context.applicationContext
    private val dir: File get() = File(context.cacheDir, DIR).apply { mkdirs() }

    /** The share cache (exposed through the FileProvider); also holds GPX exports (R25). */
    val shareDir: File get() = dir

    /** The trip map for the summary image (also embedded in the page). */
    suspend fun map(input: StaticTripMap.Input): StaticTripMap.Output = StaticTripMap.render(
        context, input, SummaryImage.MAP_WIDTH, SummaryImage.MAP_HEIGHT, SummaryImage.MAP_PIXEL_RATIO, SummaryImage.MAP_PADDING, engine, basemap,
    )

    private val fonts: SummaryImage.Fonts by lazy {
        SummaryImage.Fonts(
            title = ResourcesCompat.getFont(this.context, R.font.noto_sans_bold) ?: Typeface.DEFAULT_BOLD,
            text = ResourcesCompat.getFont(this.context, R.font.noto_sans_regular) ?: Typeface.DEFAULT,
            numerals = ResourcesCompat.getFont(this.context, R.font.barlow_condensed_bold) ?: Typeface.DEFAULT_BOLD,
        )
    }

    /** Renders the summary image with [chosen] photos (TripShare.imagePhotos). */
    suspend fun summaryImage(base: SummaryImage.Input, chosen: List<Photo>): Bitmap = withContext(Dispatchers.Default) {
        val bitmaps = chosen.mapNotNull { bitmapOf(it, IMAGE_PHOTO_PX) }
        try {
            SummaryImage.render(base.copy(photos = bitmaps, fonts = fonts))
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    /** Writes [bitmap] as a PNG named [name] into the share cache. */
    suspend fun writePng(bitmap: Bitmap, name: String): File = withContext(Dispatchers.IO) {
        val file = File(dir, name)
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "png encode failed" } }
        file
    }

    /** Builds the page within the size cap (R18) and writes it as [name] into the share cache. */
    suspend fun writePage(content: TripPage.Content, name: String): Pair<File, TripPage.Result> = withContext(Dispatchers.Default) {
        val result = TripPage.buildWithinCap(content) { photo, level -> encodePhoto(photo, level) }
        Log.i(TAG, "page: ${result.bytes} B, level ${result.level}, ${result.photosIncluded} photos, ${result.photosOmitted} omitted")
        val file = File(dir, name)
        withContext(Dispatchers.IO) { file.writeText(result.html, Charsets.UTF_8) }
        file to result
    }

    /** The map as a JPEG for the page. */
    fun mapJpeg(map: Bitmap): ByteArray = ByteArrayOutputStream().also { map.compress(Bitmap.CompressFormat.JPEG, MAP_JPEG_QUALITY, it) }.toByteArray()

    /** A stored photo downscaled for export (docs/06-photos.md "Export"): upright JPEG, no metadata. */
    fun encodePhoto(photo: Photo, level: TripPage.Level): ByteArray? {
        val bmp = bitmapOf(photo, level.maxPx) ?: return null
        return try {
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, level.quality, it) }.toByteArray()
        } finally {
            bmp.recycle()
        }
    }

    /** Trip dates as on the trip screen (open-ended: "từ …"). */
    fun datesOf(trip: Trip): String = dateRange(trip.startDate, trip.endDate) { context.getString(R.string.trip_open_ended, it) }

    /** Removes earlier exports from the share cache (they are temporary). */
    suspend fun clearCache() = withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)

    /** ACTION_SEND for [file] with read permission on its content:// URI, wrapped in the system chooser. */
    fun shareIntent(file: File, mime: String, title: String): Intent {
        val uri = uriFor(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }

    /**
     * Android 10+: copies [file] into shared storage — PNG to Pictures/LuTrinh, HTML to
     * Download/LuTrinh — and returns the folder shown to the user. Older Android versions use
     * the system "save as" picker instead ([copyTo]).
     */
    suspend fun saveToDevice(file: File, mime: String): String = withContext(Dispatchers.IO) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val image = mime.startsWith("image/")
        val folder = (if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS) + "/" + SAVE_FOLDER
        val collection = if (image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("no output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
        Log.i(TAG, "saved $folder/${file.name} as $uri")
        folder
    }

    /** Copies [file] to a document the user picked ("save as", Android 8–9). */
    suspend fun copyTo(file: File, target: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("no output stream")
    }

    /** Decodes [file] with its long edge at most [maxPx], upright; null when missing/unreadable. */
    /** A photo downscaled, or a video's still frame (R29: shares never carry the video itself). */
    private fun bitmapOf(photo: Photo, maxPx: Int): Bitmap? {
        val file = photos.fileFor(photo.fileName)
        return if (photo.isVideo) file.takeIf { it.exists() }?.let { VideoMeta.still(it, maxPx) } else decodeScaled(file, maxPx)
    }

    private fun decodeScaled(file: File, maxPx: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val source = PixelSize(bounds.outWidth, bounds.outHeight)
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = PhotoImportPlan.sampleSize(source, maxPx) })
            ?: return null
        // Stored copies are upright already (R11), except originals kept as-is; honour their tag.
        val orientation = runCatching {
            Orientation.fromExif(ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
        }.getOrDefault(Orientation.NORMAL)
        val target = PhotoImportPlan.scaledSize(source, maxPx)
        val scale = target.longEdge.toFloat() / maxOf(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postRotate(orientation.rotation.toFloat())
            if (orientation.mirror) postScale(-1f, 1f)
        }
        if (matrix.isIdentity) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
    }

    companion object {
        private const val TAG = "TripExporter"
        const val DIR = "share"
        const val AUTHORITY_SUFFIX = ".fileprovider"
        const val SAVE_FOLDER = "LuTrinh"
        const val MIME_PNG = "image/png"
        const val MIME_HTML = "text/html"

        /** Long edge of a photo decoded for the summary image (it is drawn ~225 px wide). */
        private const val IMAGE_PHOTO_PX = 640
        private const val MAP_JPEG_QUALITY = 85
    }
}

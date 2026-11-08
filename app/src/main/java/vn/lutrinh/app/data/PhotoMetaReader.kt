package vn.lutrinh.app.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.PhotoGrouping
import vn.lutrinh.app.domain.usecase.PhotoMeta

/**
 * Reads capture date and GPS position from a picked photo BEFORE it is imported: the stored copy
 * has its location removed (R11), so this is the only moment the position is known (R13, docs/06).
 * Nothing read here is stored except as the step's location/arrival chosen by the user.
 */
class PhotoMetaReader(context: Context) {
    private val resolver = context.contentResolver

    suspend fun read(uris: List<Uri>): List<PhotoMeta> = withContext(Dispatchers.IO) {
        uris.mapIndexed { i, uri -> readOne(uri, i) }
    }

    private fun readOne(uri: Uri, index: Int): PhotoMeta {
        val exif = runCatching { resolver.openInputStream(uri)?.use { ExifInterface(it) } }.getOrNull()
        val takenAt = exif?.let {
            PhotoGrouping.parseExifDateTime(it.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                ?: PhotoGrouping.parseExifDateTime(it.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED))
                ?: PhotoGrouping.parseExifDateTime(it.getAttribute(ExifInterface.TAG_DATETIME))
        }
        val location = exif?.latLong
            ?.let { (lat, lng) -> GeoPoint(lat, lng) }
            ?.takeIf { PhotoGrouping.isUsable(it) }
        return PhotoMeta(uri.toString(), index, takenAt, location)
    }
}

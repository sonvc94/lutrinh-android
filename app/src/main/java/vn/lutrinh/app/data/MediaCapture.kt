package vn.lutrinh.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * R28 "Chụp ảnh" / R29 "Quay video" with the SYSTEM camera app (no CAMERA permission, no camera
 * code of our own): the camera writes into a file we own, `cacheDir/capture/<uuid>.<ext>`, handed
 * over as a FileProvider `content://` URI. After the camera returns, [finish] imports that file
 * like a picked one (photo: R11 processing; video: R29 limits) and always deletes it, so a
 * cancelled or failed capture leaves no file anywhere.
 */
class MediaCapture(private val context: Context) {
    enum class Kind(val ext: String) { PHOTO("jpg"), VIDEO("mp4") }

    private val dir: File get() = File(context.cacheDir, DIR).apply { mkdirs() }

    /** A new empty target file for the camera. */
    fun newTarget(kind: Kind): File = File(dir, "${UUID.randomUUID()}.${kind.ext}").apply { createNewFile() }

    /** Resolves a target name kept across process death (SavedStateHandle) back to its file; only plain names inside [dir]. */
    fun target(name: String): File? = name.takeIf { it.isNotBlank() && '/' !in it && ".." !in it }?.let { File(dir, it) }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)

    /**
     * The camera returned ([ok] = RESULT_OK). Imports [target] with [import] when the camera wrote
     * something into it, else ([fallback]: a camera that ignored our file and returned its own
     * URI) imports that. [target] is deleted in every case.
     */
    suspend fun <T> finish(target: File, ok: Boolean, fallback: Uri? = null, import: suspend (Uri) -> T?): T? {
        try {
            if (!ok) return null
            val wrote = withContext(Dispatchers.IO) { target.exists() && target.length() > 0 }
            return when {
                wrote -> import(Uri.fromFile(target))
                fallback != null -> import(fallback)
                else -> null
            }
        } finally {
            withContext(Dispatchers.IO) { target.delete() }
        }
    }

    /** Capture files left by a process that died while the camera was open (called when no capture is pending). */
    suspend fun clearLeftovers() = withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }

    companion object {
        const val DIR = "capture"

        /** Same provider as the share exports (TripExporter); res/xml/share_paths.xml lists `capture/`. */
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}

package vn.lutrinh.app.data

import vn.lutrinh.app.domain.catalog.ByteSink
import vn.lutrinh.app.domain.catalog.RouteStaging
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * R40 download staging: `files/route-staging/<code>/<sha256>` (private app storage, kept across
 * restarts for resume, never in backups). Cleared after the import or by "Hủy tải".
 */
class RouteStagingDir(private val dir: File) : RouteStaging {
    private fun file(sha256: String): File {
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "bad file name" }
        return File(dir, sha256)
    }

    fun fileOf(sha256: String): File = file(sha256)

    override fun stagedBytes(sha256: String): Long = file(sha256).takeIf { it.isFile }?.length() ?: 0L

    override suspend fun <T> write(sha256: String, append: Boolean, block: suspend (ByteSink) -> T): T {
        dir.mkdirs()
        return FileOutputStream(file(sha256), append).use { out ->
            block(ByteSink { buf, len -> out.write(buf, 0, len) })
        }
    }

    override fun hashOf(sha256: String): String? {
        val f = file(sha256).takeIf { it.isFile } ?: return null
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    override fun delete(sha256: String) { file(sha256).delete() }

    override fun clear() { dir.deleteRecursively() }

    /** Bytes staged so far (for "Tạm dừng" state after a restart). */
    fun totalBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    companion object {
        fun forCode(filesDir: File, code: String) = RouteStagingDir(File(File(filesDir, "route-staging"), code))
    }
}

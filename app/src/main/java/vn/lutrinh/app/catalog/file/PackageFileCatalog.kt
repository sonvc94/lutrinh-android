package vn.lutrinh.app.catalog.file

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.lutrinh.app.catalog.PackageJson
import vn.lutrinh.app.catalog.PackageRead
import vn.lutrinh.app.domain.catalog.ByteSink
import vn.lutrinh.app.domain.catalog.CatalogError
import vn.lutrinh.app.domain.catalog.CatalogResult
import vn.lutrinh.app.domain.catalog.DownloadTicket
import vn.lutrinh.app.domain.catalog.FetchedRoute
import vn.lutrinh.app.domain.catalog.LinkRef
import vn.lutrinh.app.domain.catalog.MediaRef
import vn.lutrinh.app.domain.catalog.RemoteStatus
import vn.lutrinh.app.domain.catalog.RouteCatalog
import vn.lutrinh.app.domain.catalog.RouteCode
import vn.lutrinh.app.domain.catalog.RoutePackage
import vn.lutrinh.app.domain.catalog.RouteStatus
import vn.lutrinh.app.domain.catalog.RouteSummary
import vn.lutrinh.app.domain.usecase.Geo
import vn.lutrinh.app.domain.usecase.Polyline
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * A route package carried by hand (docs/02 "Route package file"): a `.lutrinh-route.zip` or its
 * unpacked folder with `route.json`, an optional `manifest.json` (`{"code": "HG4N7KQE"}`) and
 * `media/<sha256>.jpg|mp4`. Same [RouteCatalog] as the server, so the download and import path is
 * the one users get; it never goes on the network and has no newer versions.
 */
class PackageFileCatalog(private val source: File) : RouteCatalog {
    override val id = "file"

    private fun open(name: String): InputStream? =
        if (source.isDirectory) File(source, name).takeIf { it.isFile }?.inputStream()
        else ZipFile(source).let { zip ->
            val entry = zip.getEntry(name) ?: return null.also { zip.close() }
            object : java.io.FilterInputStream(zip.getInputStream(entry)) {
                override fun close() { super.close(); zip.close() }
            }
        }

    private fun bytes(name: String): ByteArray? = open(name)?.use { it.readBytes() }

    private fun manifestCode(): String? = bytes("manifest.json")?.let {
        runCatching { JSONObject(it.toString(Charsets.UTF_8)).optString("code").takeIf(String::isNotEmpty) }.getOrNull()
    }

    override suspend fun resolve(code: RouteCode): CatalogResult<RouteSummary> = withContext(Dispatchers.IO) {
        val bytes = bytes("route.json") ?: return@withContext CatalogResult.Refused(CatalogError.BAD_PACKAGE)
        val pkg = when (val read = PackageJson.read(bytes.toString(Charsets.UTF_8))) {
            is PackageRead.Ok -> read.pkg
            is PackageRead.Unsupported -> return@withContext CatalogResult.Refused(CatalogError.FORMAT_UNSUPPORTED)
            is PackageRead.Invalid -> return@withContext CatalogResult.Refused(CatalogError.BAD_PACKAGE)
        }
        val own = manifestCode()?.let { RouteCode.of(it) }
        if (own != null && own != code) return@withContext CatalogResult.Refused(CatalogError.CODE_NOT_FOUND)
        CatalogResult.Ok(summaryOf(code, pkg))
    }

    override suspend fun fetchRoute(ticket: DownloadTicket): CatalogResult<FetchedRoute> = withContext(Dispatchers.IO) {
        val bytes = bytes("route.json") ?: return@withContext CatalogResult.Refused(CatalogError.BAD_PACKAGE)
        when (val read = PackageJson.read(bytes.toString(Charsets.UTF_8))) {
            is PackageRead.Ok -> CatalogResult.Ok(FetchedRoute(PackageJson.sha256(bytes), read.pkg))
            is PackageRead.Unsupported -> CatalogResult.Refused(CatalogError.FORMAT_UNSUPPORTED)
            is PackageRead.Invalid -> CatalogResult.Refused(CatalogError.BAD_PACKAGE)
        }
    }

    override suspend fun fetchMedia(ticket: DownloadTicket, ref: MediaRef, sink: ByteSink, from: Long): CatalogResult<Unit> =
        withContext(Dispatchers.IO) {
            val input = open("media/${ref.sha256}.${if (ref.video) "mp4" else "jpg"}")
                ?: return@withContext CatalogResult.Refused(CatalogError.BAD_PACKAGE)
            input.use {
                var skip = from
                while (skip > 0) { val n = it.skip(skip); if (n <= 0) break; skip -= n }
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = it.read(buf)
                    if (n < 0) break
                    sink.write(buf, n)
                }
            }
            CatalogResult.Ok(Unit)
        }

    override suspend fun checkUpdates(links: List<LinkRef>): CatalogResult<List<RouteStatus>> =
        CatalogResult.Ok(links.map { RouteStatus(it, RemoteStatus.ACTIVE) })

    companion object {
        /** The preview summary of a package read on the device (what the server's lookup returns). */
        fun summaryOf(code: RouteCode, pkg: RoutePackage): RouteSummary {
            val points = pkg.stops.map { vn.lutrinh.app.domain.model.GeoPoint(it.lat, it.lng) }
            val km = (1 until points.size).sumOf { Geo.distanceKm(points[it - 1], points[it]) }
            val legM = pkg.legs.sumOf { it.distanceM }
            val overview = pkg.legs.flatMap { runCatching { Polyline.decode6(it.path) }.getOrDefault(emptyList()) }.ifEmpty { points }
            return RouteSummary(
                code = code,
                routeId = pkg.route.id,
                version = pkg.route.version,
                formatVersion = pkg.formatVersion,
                title = pkg.route.title,
                description = pkg.route.description,
                days = pkg.route.days,
                stops = pkg.stops.size,
                distanceM = if (legM > 0) legM else km * 1000,
                photos = pkg.photos,
                videos = pkg.videos,
                bytes = pkg.sizeMedia,
                files = pkg.sizeFiles,
                bounds = pkg.route.bounds,
                readyMade = pkg.route.readyMade,
                overview = overview,
                ticket = DownloadTicket(code, pkg.route.id, pkg.route.version, "file"),
            )
        }
    }
}

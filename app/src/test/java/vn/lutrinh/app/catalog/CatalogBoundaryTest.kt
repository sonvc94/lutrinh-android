package vn.lutrinh.app.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R40 (like R35 maps, R36 routing): the app reaches route catalogs only through
 * `domain/catalog` (RouteCatalog); the server's API lives in `catalog/server`, the package-file
 * reader in `catalog/file`, the server address only in `catalog/Catalogs.kt`.
 */
class CatalogBoundaryTest {
    // Unit tests run in the module directory (app/).
    private val root = File("src/main/java").also { assertTrue(it.absolutePath, it.isDirectory) }
    private val app = File(root, "vn/lutrinh/app")

    private fun sources(): Sequence<File> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }

    private fun File.under(dir: String) = canonicalPath.startsWith(File(app, dir).canonicalPath + File.separator)

    private fun leaks(allowed: (File) -> Boolean, needle: (String) -> Boolean): List<String> =
        sources().filterNot(allowed).filter { f -> f.readLines().any(needle) }.map { it.relativeTo(root).path }.sorted().toList()

    @Test fun serverAddressOnlyInCatalogs() {
        assertEquals(emptyList<String>(), leaks({ it.under("catalog") && it.name == "Catalogs.kt" }) { "ROUTE_SERVER_URL" in it })
        val hosts = listOf("10.0.2.2", "lutrinh.vn", "localhost:8000")
        assertEquals(emptyList<String>(), leaks({ it.under("catalog") && it.name == "Catalogs.kt" }) { l -> hosts.any { it in l } })
    }

    @Test fun publicApiOnlyInTheServerAdapter() {
        val words = listOf("/api/v1", "downloadToken", "vnd.lutrinh.route", "/dl/", "/updates")
        assertEquals(emptyList<String>(), leaks({ it.under("catalog/server") }) { l -> words.any { it in l } })
        assertEquals(emptyList<String>(), leaks({ it.under("catalog") }) { "catalog.server" in it && "import" in it })
    }

    @Test fun packageFileReaderOnlyInItsAdapter() {
        assertEquals(emptyList<String>(), leaks({ it.under("catalog") }) { "catalog.file" in it && "import" in it })
        assertEquals(emptyList<String>(), leaks({ it.under("catalog") }) { "PackageJson" in it })
    }

    @Test fun appUsesCatalogsOnlyThroughTheDomainInterface() {
        // Outside catalog/, only the wiring (AppContainer) may name the catalog package.
        val wiring = setOf("AppContainer.kt")
        assertEquals(emptyList<String>(), leaks({ it.under("catalog") || it.name in wiring }) { "import vn.lutrinh.app.catalog" in it })
        // The domain side stays free of HTTP, JSON and Android.
        val domain = sources().filter { it.under("domain/catalog") }
            .filter { f -> f.readLines().any { "okhttp3" in it || "org.json" in it || "android." in it || "java.net" in it } }
            .map { it.relativeTo(root).path }.toList()
        assertEquals(emptyList<String>(), domain)
    }

    @Test fun httpClientOnlyInAdapters() {
        val adapters = listOf("routing", "map/maplibre", "catalog/server", "sharein")
        assertEquals(emptyList<String>(), leaks({ f -> adapters.any { f.under(it) } }) { "okhttp3" in it })
    }
}

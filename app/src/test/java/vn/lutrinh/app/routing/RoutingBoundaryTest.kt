package vn.lutrinh.app.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R36 (like R35 for maps): the app talks to routers only through `domain/routing` (RoutingProvider);
 * each router's API lives in its adapter package, its server address only in routing/Routers.kt.
 */
class RoutingBoundaryTest {
    // Unit tests run in the module directory (app/).
    private val root = File("src/main/java").also { assertTrue(it.absolutePath, it.isDirectory) }
    private val app = File(root, "vn/lutrinh/app")

    private fun sources(): Sequence<File> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }

    private fun File.under(dir: String) = canonicalPath.startsWith(File(app, dir).canonicalPath + File.separator)

    private fun leaks(allowed: (File) -> Boolean, needle: (String) -> Boolean): List<String> =
        sources().filterNot(allowed).filter { f -> f.readLines().any(needle) }.map { it.relativeTo(root).path }.sorted().toList()

    @Test fun valhallaApiOnlyInItsAdapter() {
        val words = listOf("motor_scooter", "\"costing\"", "valhalla1.", "/route\"")
        assertEquals(emptyList<String>(), leaks({ it.under("routing/valhalla") || it.name == "Routers.kt" }) { l -> words.any { it in l } })
        assertEquals(emptyList<String>(), leaks({ it.under("routing") }) { "routing.valhalla" in it })
    }

    @Test fun osrmApiOnlyInItsAdapter() {
        val words = listOf("route/v1", "routed-car", "geometries=")
        assertEquals(emptyList<String>(), leaks({ it.under("routing/osrm") || it.name == "Routers.kt" }) { l -> words.any { it in l } })
        assertEquals(emptyList<String>(), leaks({ it.under("routing") }) { "routing.osrm" in it })
    }

    @Test fun routerAddressesOnlyInTheRegistry() {
        val hosts = listOf("openstreetmap.de", "project-osrm.org")
        assertEquals(emptyList<String>(), leaks({ it.under("routing") && it.name == "Routers.kt" }) { l -> hosts.any { it in l } })
    }

    @Test fun appUsesRoutingOnlyThroughTheDomainInterface() {
        // Outside routing/, only the wiring (AppContainer) may name the routing package.
        val wiring = setOf("AppContainer.kt")
        assertEquals(emptyList<String>(), leaks({ it.under("routing") || it.name in wiring }) { "import vn.lutrinh.app.routing" in it })
        // The domain stays free of HTTP and JSON.
        val domain = sources().filter { it.under("domain") }
            .filter { f -> f.readLines().any { "okhttp3" in it || "org.json" in it || "android." in it } }
            .map { it.relativeTo(root).path }.toList()
        assertEquals(emptyList<String>(), domain)
    }

    @Test fun httpClientOnlyInAdapters() {
        assertEquals(emptyList<String>(), leaks({ it.under("routing") || it.under("map/maplibre") }) { "okhttp3" in it })
    }
}

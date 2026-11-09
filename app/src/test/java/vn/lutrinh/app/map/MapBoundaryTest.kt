package vn.lutrinh.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** R35: the map SDK is used only by its adapter package; everything else talks to the map interface. */
class MapBoundaryTest {
    @Test
    fun mapSdk_onlyInTheAdapterPackage() {
        // Unit tests run in the module directory (app/).
        val root = File("src/main/java")
        assertTrue(root.absolutePath, root.isDirectory)
        val adapter = File(root, "vn/lutrinh/app/map/maplibre").canonicalPath
        val leaks = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.canonicalPath.startsWith(adapter) }
            .filter { f -> f.readLines().any { it.contains("org.maplibre") } }
            .map { it.relativeTo(root).path }
            .toList()
        assertEquals(emptyList<String>(), leaks)
    }
}

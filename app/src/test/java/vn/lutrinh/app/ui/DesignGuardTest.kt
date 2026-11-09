package vn.lutrinh.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Design guard (docs/08-design.md "Guard"): colours come from the tokens in `ui/theme/` and the
 * app draws no decorative gradients or generated pictures. Scans the main sources as text, so it
 * runs in `testDebugUnitTest` (Gradle runs JVM tests from the module directory).
 */
class DesignGuardTest {
    private val root = File("src/main/java/vn/lutrinh/app")

    private fun sources(): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().also {
            assertTrue("no sources under ${root.absolutePath}", it.size > 50)
        }

    private fun rel(f: File) = f.relativeTo(root).invariantSeparatorsPath

    private fun hits(files: List<File>, pattern: Regex): List<String> = files.flatMap { f ->
        f.readLines().mapIndexedNotNull { i, line ->
            if (pattern.containsMatchIn(line)) "${rel(f)}:${i + 1}: ${line.trim()}" else null
        }
    }

    @Test
    fun hardCodedColoursOnlyInTheTheme() {
        val outside = sources().filterNot { rel(it).startsWith("ui/theme/") }
        val found = hits(outside, Regex("""Color\(0x|\b0x[Ff][Ff][0-9A-Fa-f]{6}\b"""))
        assertTrue("colour literals outside ui/theme (use Palette/MaterialTheme tokens):\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun noGradientsInUi() {
        val ui = sources().filter { rel(it).startsWith("ui/") }
        val found = hits(ui, Regex("""Brush\.(linear|vertical|horizontal|radial|sweep)Gradient"""))
        assertTrue("decorative gradients in ui/ (a trip without a photo shows its route):\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun noShadowedFabs() {
        // docs/08 §5 "Buttons": flat FlatFab / MapButton only; the Material FABs live in Buttons.kt.
        val ui = sources().filter { rel(it).startsWith("ui/") && rel(it) != "ui/common/Buttons.kt" }
        val found = hits(ui, Regex("""\b(Small|Large|Extended)?FloatingActionButton\("""))
        assertTrue("Material FABs (shadow, pill) outside ui/common/Buttons.kt:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun noPillButtons() {
        // docs/08 §4: 4 dp corners; M3 filled/tonal/outlined buttons default to a full pill.
        val ui = sources().filter { rel(it).startsWith("ui/") }
        val found = ui.flatMap { f ->
            Regex("""(?<![\w.])(Button|OutlinedButton|FilledTonalButton|ElevatedButton)\(""").findAll(f.readText())
                .filterNot { f.readText().startsWith("shape =", it.range.last + 1) }
                .map { "${rel(f)}: ${it.value}" }.toList()
        }
        assertTrue("buttons without `shape = MaterialTheme.shapes.small` first:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun noSystemBlueOnMaps() {
        // The my-location dot is the ChamBright token, not the Google blue.
        val found = hits(sources(), Regex("""1A73E8""", RegexOption.IGNORE_CASE))
        assertTrue("system blue left:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun noGeneratedSamplePictures() {
        val sample = File(root, "data/SampleData.kt").readText()
        assertTrue("SampleData must not draw pictures (real photos in debug assets)", "fun landscape" !in sample && "LinearGradient" !in sample)
    }

    @Test
    fun samplePhotosHaveASourceAndLicence() {
        val dir = File("src/debug/assets/sample-photos")
        val photos = dir.listFiles { f -> f.extension == "jpg" }.orEmpty().map { it.name }
        assertTrue("sample photos missing in ${dir.absolutePath}", photos.isNotEmpty())
        val readme = File(dir, "README.md").readText()
        val rows = readme.lines().filter { it.startsWith("| `") }
        photos.forEach { name ->
            val row = rows.singleOrNull { it.startsWith("| `$name`") }
            assertTrue("$name has no README row", row != null)
            assertTrue("$name row lacks a Commons source", row!!.contains("commons.wikimedia.org/wiki/File:"))
            assertTrue("$name row lacks a licence", Regex("""CC0|CC BY|Public domain""").containsMatchIn(row))
        }
        assertTrue("README lists files that do not exist", rows.size == photos.size)
    }
}

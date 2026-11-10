package vn.lutrinh.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Design guard (docs/08-design.md §11 "Guard"), the checkable rules of the topo-outdoor style:
 * colours, radii and shadows come from the tokens in `ui/theme/` (Palette, Radii, Lift), icons are
 * TopoIcons, only the licensed fonts are bundled, and the app draws no gradients or generated
 * pictures. Scans the main sources as text, so it runs in `testDebugUnitTest` (Gradle runs JVM
 * tests from the module directory).
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
    fun fabsOnlyInButtons() {
        // docs/08 §6 "FAB": the forest ChipFab / MapButton only; the Material FABs live in Buttons.kt.
        val ui = sources().filter { rel(it).startsWith("ui/") && rel(it) != "ui/common/Buttons.kt" }
        val found = hits(ui, Regex("""\b(Small|Large|Extended)?FloatingActionButton\("""))
        assertTrue("Material FABs (pill, own shadow) outside ui/common/Buttons.kt (use ChipFab):\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun dialogsUseTheThreeTypesAndTheDialogTitleToken() {
        // docs/08 §5 "Dialogs", R38: AlertDialog only in ui/common/Dialogs.kt (the time picker has no title and
        // keeps its own); titles are titleLarge 22, and the old 26 sp title slot (headlineSmall) is not used by name.
        val ui = sources().filter { rel(it).startsWith("ui/") }
        val raw = hits(ui.filterNot { rel(it) in setOf("ui/common/Dialogs.kt", "ui/steps/StepEditScreen.kt") }, Regex("""\bAlertDialog\("""))
        assertTrue("AlertDialog outside ui/common/Dialogs.kt (use ConfirmDialog / NoticeDialog / MessageDialog):\n" + raw.joinToString("\n"), raw.isEmpty())
        val sizes = hits(ui.filterNot { rel(it).startsWith("ui/theme/") }, Regex("""headlineSmall|fontSize\s*=\s*26|\b26\.sp"""))
        assertTrue("26 sp title outside the theme (dialog and state titles are titleLarge 22):\n" + sizes.joinToString("\n"), sizes.isEmpty())
        val dialogs = File(root, "ui/common/Dialogs.kt").readText()
        assertTrue("every dialog title is titleLarge", Regex("""style = MaterialTheme\.typography\.titleLarge""").findAll(dialogs).count() >= 3)
    }

    @Test
    fun noPillButtons() {
        // docs/08 §6 "Buttons": 12 dp corners (shapes.small); M3 filled/tonal/outlined buttons default to a full pill.
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
        // The my-location dot is the Waypoint token (specimen #2B6CB0), not the Google blue.
        val found = hits(sources(), Regex("""1A73E8""", RegexOption.IGNORE_CASE))
        assertTrue("system blue left:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun cornerRadiiOnlyFromTokens() {
        // docs/08 §4: the four radii 8/12/18/22 are Radii tokens (or MaterialTheme.shapes); CircleShape is for dots.
        val outside = sources().filterNot { rel(it).startsWith("ui/theme/") }
        val found = hits(outside, Regex("""RoundedCornerShape\((?!Radii\.)|CutCornerShape\("""))
        assertTrue("corner radii not from Radii/MaterialTheme.shapes:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun shadowsOnlyFromLiftTokens() {
        // docs/08 §4 "Elevation": cards sit on a ledge; only things over the map lift, by Lift.Chip / Lift.Panel.
        val found = hits(sources(), Regex("""shadowElevation\s*=|\.shadow\(|Elevation\(\s*defaultElevation\s*=\s*[1-9]"""))
            .filterNot { "Lift." in it }
        assertTrue("shadows not from the Lift tokens:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun bundledFontsAreTheSpecFontsWithALicence() {
        // docs/08 §3: Noto Sans (body) + Barlow Condensed (numerals), SIL OFL, licence text shipped in assets/licenses.
        val fonts = File("src/main/res/font").listFiles().orEmpty().map { it.name }.sorted()
        assertTrue("no fonts in res/font", fonts.isNotEmpty())
        val families = mapOf("noto_sans_" to "NotoSans-OFL.txt", "barlow_condensed_" to "BarlowCondensed-OFL.txt")
        fonts.forEach { name ->
            val licence = families.entries.firstOrNull { name.startsWith(it.key) }?.value
            assertTrue("font $name is not a docs/08 font", licence != null)
            assertTrue("$name: licence $licence missing", File("src/main/assets/licenses/$licence").isFile)
        }
        val old = hits(sources(), Regex("""be_vietnam|AtlasIcons|\bFlatFab\b|\bCham(Bright)?\b"""))
        assertTrue("references to the old atlas design:\n" + old.joinToString("\n"), old.isEmpty())
    }

    /**
     * Files not yet redone in the topo style (docs/09-redesign-inventory.md, batches 2-4). They may
     * still use stock Material icons and other font weights; each later batch removes its files
     * here, and a listed file that is already clean fails the test so the list only shrinks.
     */
    private val notYetTopo = emptySet<String>()

    private val legacyLook = Regex("""import androidx\.compose\.material\.icons|FontWeight\.(Thin|ExtraLight|Light|SemiBold|ExtraBold|Black)\b""")

    @Test
    fun topoIconsAndTypeWeights() {
        // docs/08 §3/§5: TopoIcons only; Noto Sans 400/500/700 (Barlow 500-700 via numerals()).
        val files = sources().filterNot { rel(it).startsWith("ui/theme/") }
        val found = hits(files.filterNot { rel(it) in notYetTopo }, legacyLook)
        assertTrue("stock Material icons or off-scale font weights (use TopoIcons / the type scale):\n" + found.joinToString("\n"), found.isEmpty())
        val clean = notYetTopo.filter { name -> files.none { rel(it) == name } || hits(files.filter { rel(it) == name }, legacyLook).isEmpty() }
        assertTrue("already topo, remove from notYetTopo: $clean", clean.isEmpty())
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

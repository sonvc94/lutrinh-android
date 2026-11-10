package vn.lutrinh.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's own icons (docs/08 §5 "Icons"): drawn for Lữ Trình on a 24 grid with one 1.6 unit
 * stroke, square ends and mitred corners, like the lines of a survey sheet; dots are filled.
 * They replace the stock Material icons on the visible actions (bars, FABs, menus, map buttons).
 * Own drawings, no third-party licence. The path data is in docs/08 so iOS draws the same shapes.
 * `Icon` tints them, so the colour here is only a placeholder.
 */
object AtlasIcons {
    private const val STROKE = 1.6f

    /** [lines] are stroked, [dots] are filled; both are SVG path data on a 24 × 24 viewport. */
    private fun icon(name: String, lines: String, dots: String = "", mirror: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).apply {
            addPath(
                pathData = addPathNodes(lines),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter,
            )
            if (dots.isNotEmpty()) addPath(pathData = addPathNodes(dots), fill = SolidColor(Color.Black))
        }.build()

    // Path data (also listed in docs/08 §5).
    const val ADD = "M12 5v14M5 12h14"
    const val BACK = "M19 12H6M11 6.5L5.5 12 11 17.5"
    const val MORE_DOTS = "M11 4.5h2v2h-2zM11 11h2v2h-2zM11 17.5h2v2h-2z"
    const val SHARE = "M12 15V4M8 7.5L12 3.5l4 4M8.5 10H5v10h14V10h-3.5"
    const val EDIT = "M4.5 19.5h4l10-10-4-4-10 10zM12.5 7.5l4 4"
    const val DELETE = "M4.5 6.5h15M9.5 6.5v-2.5h5v2.5M6.5 6.5l1 13h9l1-13M10.5 10.5v5.5M13.5 10.5v5.5"
    /** The route glyph: open start ring, a switchback, the end dot (the same mark as the app icon). */
    const val ROUTE = "M8.5 18H15a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h6.5M6 15.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5"
    const val ROUTE_DOT = "M18 4a2 2 0 1 0 0 4a2 2 0 1 0 0-4z"
    /** "Bản đồ của tôi": a globe drawn as a graticule. */
    const val ATLAS = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M3 12h18M4.5 7.5h15M4.5 16.5h15M12 3c-3.5 3.5-3.5 14.5 0 18M12 3c3.5 3.5 3.5 14.5 0 18"
    const val MY_LOCATION = "M12 6a6 6 0 1 0 0 12a6 6 0 1 0 0-12M12 2.5V6M12 18v3.5M2.5 12H6M18 12h3.5"
    const val MY_LOCATION_DOT = "M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4z"
    const val LAYERS = "M12 4l8 4-8 4-8-4zM4 12.5l8 4 8-4M4 16.5l8 4 8-4"
    const val EXPAND = "M14 4h6v6M20 4l-7 7M10 20H4v-6M4 20l7-7"
    const val FIT = "M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5"
    const val ADD_STEP = "M12 21s-6.5-5.5-6.5-11a6.5 6.5 0 0 1 13 0c0 5.5-6.5 11-6.5 11zM12 7v6M9 10h6"
    const val ADD_PHOTOS = "M3 7.5h12v12H3zM3 16.5l4-4 3 3 2-2 3 3M19 3v7M15.5 6.5h7"
    const val PHOTO = "M4 5h16v14H4zM4 16l5-5 4 4 2.5-2.5L20 17"
    const val PHOTO_DOT = "M15 7.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z"
    const val DOWNLOAD = "M12 4v11M7.5 10.5L12 15l4.5-4.5M4.5 19.5h15"
    const val UPLOAD = "M12 15V4M7.5 8.5L12 4l4.5 4.5M4.5 19.5h15"
    const val FILE_OPEN = "M6 3.5h8l4 4v13H6zM13.5 3.5V8H18M9 14.5h6M12 11.5l3 3-3 3"
    const val RESTORE = "M5 12a7 7 0 1 0 2-5M4.5 4v4.5H9M12 8.5V12l2.5 2.5"
    /** Settings: three sliders (lines with a knob stroke each). */
    const val SETTINGS = "M4 7h9M17 7h3M15 5v4M4 12h3M11 12h9M9 10v4M4 17h11M19 17h1M17 15v4"
    const val PAGE = "M6 3.5h12v17H6zM9 8h6M9 11.5h6M9 15h4"
    /** The location picker's centre needle: a ring head on a line whose end (12, 22) is the picked point. */
    const val NEEDLE = "M12 12v10M12 2.5a4.5 4.5 0 1 0 0 9a4.5 4.5 0 1 0 0-9"
    const val NEEDLE_DOT = "M12 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z"
    const val SEARCH = "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13M15.5 15.5L20 20"
    const val CLOSE = "M6 6l12 12M18 6L6 18"
    const val CHECK = "M5 12.5l4.5 4.5L19 7.5"

    val Add = icon("Add", ADD)
    val Back = icon("Back", BACK, mirror = true)
    val More = icon("More", "", MORE_DOTS)
    val Share = icon("Share", SHARE)
    val Edit = icon("Edit", EDIT)
    val Delete = icon("Delete", DELETE)
    val Route = icon("Route", ROUTE, ROUTE_DOT)
    val Atlas = icon("Atlas", ATLAS)
    val MyLocation = icon("MyLocation", MY_LOCATION, MY_LOCATION_DOT)
    val Layers = icon("Layers", LAYERS)
    val Expand = icon("Expand", EXPAND)
    val Fit = icon("Fit", FIT)
    val AddStep = icon("AddStep", ADD_STEP)
    val AddPhotos = icon("AddPhotos", ADD_PHOTOS)
    val Photo = icon("Photo", PHOTO, PHOTO_DOT)
    val Download = icon("Download", DOWNLOAD)
    val Upload = icon("Upload", UPLOAD)
    val FileOpen = icon("FileOpen", FILE_OPEN)
    val Restore = icon("Restore", RESTORE)
    val Settings = icon("Settings", SETTINGS)
    val Page = icon("Page", PAGE)
    val Needle = icon("Needle", NEEDLE, NEEDLE_DOT)
    val Search = icon("Search", SEARCH)
    val Close = icon("Close", CLOSE)
    val Check = icon("Check", CHECK)
}

package vn.lutrinh.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's own icons (docs/08 §5 "Icons"), drawn for Lữ Trình in the specimen's line grammar:
 * a 24 grid, one 2 unit stroke, round ends and round joins (like its route and contour lines),
 * boxes with 2 unit corners; dots are filled. They replace the stock Material icons on bars, FABs,
 * menus, map buttons, rows and leg-mode chips. Own drawings, no third-party licence. The path data
 * is in docs/08 so iOS draws the same shapes. `Icon` tints them; the colour here is a placeholder.
 */
object TopoIcons {
    private const val STROKE = 2f

    /** [lines] are stroked, [dots] are filled; both are SVG path data on a 24 × 24 viewport. */
    private fun icon(name: String, lines: String, dots: String = "", mirror: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).apply {
            if (lines.isNotEmpty()) {
                addPath(
                    pathData = addPathNodes(lines),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            if (dots.isNotEmpty()) addPath(pathData = addPathNodes(dots), fill = SolidColor(Color.Black))
        }.build()

    /** A filled disc of radius [r] at ([x], [y]) as path data. */
    private fun dot(x: Float, y: Float, r: Float) = "M$x ${y - r}a$r $r 0 1 0 0 ${2 * r}a$r $r 0 1 0 0 ${-2 * r}z"

    // Path data (also listed in docs/08 §5).
    const val ADD = "M12 5v14M5 12h14"
    const val BACK = "M19 12H6M11 6.5L5.5 12 11 17.5"
    val MORE_DOTS = dot(12f, 5f, 1.6f) + dot(12f, 12f, 1.6f) + dot(12f, 19f, 1.6f)
    const val SHARE = "M12 14.5V4M8 7.5l4-4 4 4M8.5 10H7a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2h-1.5"
    const val EDIT = "M4.5 19.5h4l10-10-4-4-10 10zM12.5 7.5l4 4"
    const val DELETE = "M4.5 6.5h15M9.5 6.5V4h5v2.5M6.5 6.5l1 13h9l1-13M10.5 10.5v5.5M13.5 10.5v5.5"
    /** The route glyph: open start ring, a switchback, the end dot (the same mark as the app icon). */
    const val ROUTE = "M8.5 18H15a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h6.5M6 15.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5"
    const val ROUTE_DOT = "M18 4a2 2 0 1 0 0 4a2 2 0 1 0 0-4z"
    /** "Bản đồ của tôi": three contour lines round a summit (the specimen's topo motif). */
    const val ATLAS = "M12 3.5c5 0 8.5 3.3 8.5 8s-3.8 9-8.8 9-8.2-3.7-8.2-8.3S7 3.5 12 3.5zM12 7.5c2.9 0 4.8 2 4.8 4.4s-2.1 4.6-4.9 4.6-4.6-2-4.6-4.4 1.8-4.6 4.7-4.6z"
    val ATLAS_DOT = dot(12f, 12f, 1.6f)
    const val MY_LOCATION = "M12 6a6 6 0 1 0 0 12a6 6 0 1 0 0-12M12 2.5V6M12 18v3.5M2.5 12H6M18 12h3.5"
    val MY_LOCATION_DOT = dot(12f, 12f, 2f)
    const val LAYERS = "M12 4l8 4-8 4-8-4zM4 12.5l8 4 8-4M4 16.5l8 4 8-4"
    const val EXPAND = "M14 4h6v6M20 4l-7 7M10 20H4v-6M4 20l7-7"
    const val FIT = "M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5"
    const val ADD_STEP = "M12 21s-6.5-5.5-6.5-11a6.5 6.5 0 0 1 13 0c0 5.5-6.5 11-6.5 11zM12 7v6M9 10h6"
    const val ADD_PHOTOS = "M5 7.5h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2zM3 16.5l4-4 3 3 2-2 3 3M19 3v7M15.5 6.5h7"
    const val PHOTO = "M6 5h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM4 16l5-5 4 4 2.5-2.5L20 17"
    val PHOTO_DOT = dot(15f, 9f, 1.5f)
    /** A photo that could not be loaded: the photo frame struck through. */
    const val PHOTO_OFF = "M6 5h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM4 4l16 16"
    const val DOWNLOAD = "M12 4v11M7.5 10.5L12 15l4.5-4.5M4.5 19.5h15"
    const val UPLOAD = "M12 15V4M7.5 8.5L12 4l4.5 4.5M4.5 19.5h15"
    const val FILE_OPEN = "M6 3.5h8l4 4v13H6zM13.5 3.5V8H18M9 14.5h6M12 11.5l3 3-3 3"
    const val RESTORE = "M5 12a7 7 0 1 0 2-5M4.5 4v4.5H9M12 8.5V12l2.5 2.5"
    /** Settings: three sliders (lines with a knob stroke each). */
    const val SETTINGS = "M4 7h9M17 7h3M15 5v4M4 12h3M11 12h9M9 10v4M4 17h11M19 17h1M17 15v4"
    const val PAGE = "M8 3.5h8a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2zM9 8h6M9 11.5h6M9 15h4"
    /** The location picker's centre needle: a ring head on a line whose end (12, 22) is the picked point. */
    const val NEEDLE = "M12 12v10M12 2.5a4.5 4.5 0 1 0 0 9a4.5 4.5 0 1 0 0-9"
    const val NEEDLE_DOT = "M12 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z"
    const val SEARCH = "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13M15.5 15.5L20 20"
    const val CLOSE = "M6 6l12 12M18 6L6 18"
    const val CHECK = "M5 12.5l4.5 4.5L19 7.5"

    // Row icons (were stock Material icons inside rows and forms).
    const val TIME = "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17M12 7.5V12l3 2"
    /** "Dẫn đường" (R42): a navigation arrow pointing up-right. */
    const val NAVIGATE = "M20 4L4 10.5l6.5 2.5L13 19.5z"
    const val PLACE = "M12 21s-6.5-5.5-6.5-11a6.5 6.5 0 0 1 13 0c0 5.5-6.5 11-6.5 11z"
    val PLACE_DOT = dot(12f, 10f, 2f)
    const val PREVIOUS = "M14.5 6L8.5 12l6 6"
    const val NEXT = "M9.5 6l6 6-6 6"
    const val NOTE = "M4 6.5h12M4 11h12M4 15.5h6M13 20v-2.5l5.5-5.5 2.5 2.5-5.5 5.5z"
    const val CAMERA = "M4 8h3.5l2-2.5h5l2 2.5H20v11H4zM12 10a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7"
    const val VIDEO = "M5 7h8a1.5 1.5 0 0 1 1.5 1.5v7A1.5 1.5 0 0 1 13 17H5a1.5 1.5 0 0 1-1.5-1.5v-7A1.5 1.5 0 0 1 5 7zM14.5 10.5l6-3.5v10l-6-3.5"
    const val PLAY = "M8 5.5v13l10.5-6.5z"
    // Recording (R16) and the GPS card.
    const val CHEVRON_DOWN = "M6 9.5l6 6 6-6"
    const val CHEVRON_UP = "M6 14.5l6-6 6 6"
    const val PAUSE = "M9 6v12M15 6v12"
    const val STOP = "M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z"
    val RECORD_DOT = dot(12f, 12f, 6f)
    /** Offline (R15): a cloud struck through. */
    /** Date fields: a calendar page with two rings. */
    const val CALENDAR = "M6 5h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM4 10h16M8.5 3v4M15.5 3v4"
    const val CLOUD_OFF = "M7 18h10a3.5 3.5 0 0 0 .6-6.95A5.5 5.5 0 0 0 7.2 9.1 4.5 4.5 0 0 0 7 18zM4 4l16 16"
    /** Battery on its side with a charge bolt (R22 "Ghi hành trình ổn định"). */
    const val BATTERY = "M5 8h12a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2zM21 10.5v3M11.5 9.5L9.5 12h3l-2 2.5"
    /** "Bản đồ" basemap: a folded sheet. */
    const val MAP = "M3.5 6.5l5.5-2 6 2 5.5-2v13l-5.5 2-6-2-5.5 2zM9 4.5v13M15 6.5v13"
    /** "Vệ tinh" basemap: a satellite body with two panels and a signal arc. */
    const val SATELLITE = "M9 12l3-3 3 3-3 3zM10.5 10.5L7 7M13.5 13.5L17 17M4.5 9.5l5-5M14.5 19.5l5-5M15.5 4.5a4 4 0 0 1 4 4"

    // Leg modes (R7): one outline each, tinted by TransportStyle.
    const val WALK = "M11 9.5l-3 3v3M11 9.5l2.5 1 1.5 3 2.5 1M11 9.5l-1 5 3 2.5 1 3.5M10 14.5l-2.5 6"
    val WALK_DOT = dot(13f, 5.5f, 1.8f)
    const val MOTORBIKE = "M6 13a3 3 0 1 0 0 6a3 3 0 1 0 0-6M18 13a3 3 0 1 0 0 6a3 3 0 1 0 0-6M6 16l3.5-5h5L18 16M9 11L7.5 8H5M14.5 11l1-3h3"
    const val CAR = "M3.5 17v-4.5l2.5-5.5h12l2.5 5.5V17zM3.5 12.5h17M6 17v2.5M18 17v2.5"
    val CAR_DOTS = dot(7.5f, 14.8f, 1.1f) + dot(16.5f, 14.8f, 1.1f)
    const val BUS = "M7 4h10a2 2 0 0 1 2 2v11H5V6a2 2 0 0 1 2-2zM5 11.5h14M8 17v3M16 17v3"
    val BUS_DOTS = dot(8f, 14.3f, 1.1f) + dot(16f, 14.3f, 1.1f)
    const val TRAIN = "M7 3.5h10a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2zM5 10h14M8.5 16.5L6 20.5M15.5 16.5l2.5 4"
    const val PLANE = "M12 3.5c1 0 1.5 1 1.5 2.5v4l7 4v2l-7-2v4l2 1.5v1.5l-3.5-1-3.5 1v-1.5l2-1.5v-4l-7 2v-2l7-4V6c0-1.5.5-2.5 1.5-2.5z"
    const val BOAT = "M3.5 15.5h17l-2.5 4H6zM6 15.5V11h12v4.5M12 11V4M12 5l5 4.5"

    val Add = icon("Add", ADD)
    val Back = icon("Back", BACK, mirror = true)
    val More = icon("More", "", MORE_DOTS)
    val Share = icon("Share", SHARE)
    val Edit = icon("Edit", EDIT)
    val Delete = icon("Delete", DELETE)
    val Route = icon("Route", ROUTE, ROUTE_DOT)
    val Atlas = icon("Atlas", ATLAS, ATLAS_DOT)
    val MyLocation = icon("MyLocation", MY_LOCATION, MY_LOCATION_DOT)
    val Layers = icon("Layers", LAYERS)
    val Expand = icon("Expand", EXPAND)
    val Fit = icon("Fit", FIT)
    val AddStep = icon("AddStep", ADD_STEP)
    val AddPhotos = icon("AddPhotos", ADD_PHOTOS)
    val Photo = icon("Photo", PHOTO, PHOTO_DOT)
    val PhotoOff = icon("PhotoOff", PHOTO_OFF)
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
    val Time = icon("Time", TIME)
    val Place = icon("Place", PLACE, PLACE_DOT)
    val Navigate = icon("Navigate", NAVIGATE)
    val Previous = icon("Previous", PREVIOUS, mirror = true)
    val Next = icon("Next", NEXT, mirror = true)
    val Note = icon("Note", NOTE)
    val Camera = icon("Camera", CAMERA)
    val Video = icon("Video", VIDEO)
    val Play = icon("Play", "", PLAY)
    val Map = icon("Map", MAP)
    val Satellite = icon("Satellite", SATELLITE)
    val Walk = icon("Walk", WALK, WALK_DOT, mirror = true)
    val Motorbike = icon("Motorbike", MOTORBIKE)
    val Car = icon("Car", CAR, CAR_DOTS)
    val Bus = icon("Bus", BUS, BUS_DOTS)
    val Train = icon("Train", TRAIN)
    val Plane = icon("Plane", PLANE)
    val Boat = icon("Boat", BOAT)
    val ChevronDown = icon("ChevronDown", CHEVRON_DOWN)
    val ChevronUp = icon("ChevronUp", CHEVRON_UP)
    val Pause = icon("Pause", PAUSE)
    val Stop = icon("Stop", STOP)
    val Record = icon("Record", "", RECORD_DOT)
    val Battery = icon("Battery", BATTERY)
    val CloudOff = icon("CloudOff", CLOUD_OFF)
    val Calendar = icon("Calendar", CALENDAR)
}

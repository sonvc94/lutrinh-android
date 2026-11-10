package vn.lutrinh.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.routing.RoutingCredit
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.usecase.LegGeometry
import vn.lutrinh.app.domain.usecase.Polyline
import vn.lutrinh.app.map.basemap.BasemapProvider
import vn.lutrinh.app.map.basemap.Basemaps
import vn.lutrinh.app.ui.theme.Palette
import androidx.compose.ui.graphics.toArgb

/**
 * Static trip map for share exports (R18, docs/05-map.md "Static map for sharing"): the basemap
 * is a snapshot of the map engine ([MapEngine.snapshot]: same style and tile store as the live
 * maps, so an offline area works in airplane mode) of an offline-capable basemap ("Bản đồ", never
 * satellite imagery, whose terms forbid storing it); the legs, recorded tracks, step pins and the map credit
 * are drawn by us on a Canvas with [MapFit]'s projection. Without basemap tiles (no network, no
 * offline area, or a timeout) the same overlay is drawn on the plain map background.
 */
object StaticTripMap {
    private const val TAG = "StaticTripMap"

    /** Waiting longer than this for tiles falls back to the plain background. */
    const val SNAPSHOT_TIMEOUT_MS = 15_000L

    /**
     * Planned route of a trip: step points in timeline order and legModes[i] = mode of leg i -> i+1.
     * R36: legPaths[i] = the stored road line of leg i (with connectors), empty/missing = straight or
     * arc; [routing] = the credits of the routers that made them (drawn with the basemap's).
     */
    data class Input(
        val steps: List<GeoPoint>,
        val legModes: List<TransportMode?>,
        val tracks: List<List<GeoPoint>>,
        val legPaths: List<List<GeoPoint>> = emptyList(),
        val routing: List<RoutingCredit> = emptyList(),
    )

    /** [attribution] = the credits drawn on the image (basemap R34, routers R36), for the share page too. */
    data class Output(val bitmap: Bitmap, val withBasemap: Boolean, val attribution: String)

    /** R36: one router's credit as plain text, with the OSM "fix the map" address. */
    fun routingLine(credit: RoutingCredit): String = "${credit.text} · Sửa bản đồ: ${credit.fixUrl.removePrefix("https://")}"

    private val ROUTE = Palette.Cham.toArgb()
    private val END = Palette.Ink.toArgb()
    private val ACCENT = Palette.ChamBright.toArgb()
    private val TRACK = Color.parseColor(TRACK_COLOR)

    /**
     * Renders a [width] × [height] px map. [pixelRatio] = px per logical map pixel (labels, line
     * widths and pins scale with it); [paddingPx] keeps the route away from the edges.
     */
    suspend fun render(
        context: Context, input: Input, width: Int, height: Int, pixelRatio: Float, paddingPx: Int,
        engine: MapEngine = MapEngines.active,
        basemap: BasemapProvider = Basemaps.registry.offline,
    ): Output {
        val legPaths = input.steps.zipWithNext().mapIndexed { i, (a, b) ->
            Polyline.simplify(input.legPaths.getOrNull(i)?.takeIf { it.size >= 2 } ?: LegGeometry.path(a, b, input.legModes.getOrNull(i)))
        }
        val points = (if (legPaths.isEmpty()) input.steps else legPaths.flatten()) + input.tracks.flatten()
        val lw = (width / pixelRatio).toInt()
        val lh = (height / pixelRatio).toInt()
        val fit = MapFit.fit(points, lw, lh, (paddingPx / pixelRatio).toInt())
        val snapshot = withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
            engine.snapshot(context, basemap.style, MapCamera(fit.center, fit.zoom), fit.width, fit.height, pixelRatio)
        }
        if (snapshot == null) Log.i(TAG, "no basemap: drawing the route on the plain background")
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (snapshot != null) {
            c.drawBitmap(snapshot, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            snapshot.recycle()
        } else {
            c.drawColor(Color.parseColor(MapConfig.BACKGROUND))
        }
        val sx = width.toFloat() / lw
        val sy = height.toFloat() / lh
        fun path(line: List<GeoPoint>) = Path().apply {
            line.forEachIndexed { i, p ->
                val (x, y) = fit.project(p)
                if (i == 0) moveTo((x * sx).toFloat(), (y * sy).toFloat()) else lineTo((x * sx).toFloat(), (y * sy).toFloat())
            }
        }
        val r = pixelRatio
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        // Legs (docs/05 "Legs per transport mode"): ground = solid on a white casing, flight = dashed,
        // boat = dotted, walk = thin dots.
        legPaths.forEachIndexed { i, line ->
            val p = path(line)
            when (input.legModes.getOrNull(i).legLine()) {
                LegLine.GROUND -> {
                    paint.pathEffect = null
                    paint.color = Color.WHITE; paint.strokeWidth = 6.6f * r; c.drawPath(p, paint)
                    paint.color = ROUTE; paint.strokeWidth = 3.8f * r; c.drawPath(p, paint)
                }
                LegLine.FLIGHT -> {
                    paint.strokeCap = Paint.Cap.BUTT
                    paint.pathEffect = DashPathEffect(floatArrayOf(7f * r, 5f * r), 0f)
                    paint.color = ROUTE; paint.strokeWidth = 3f * r; c.drawPath(p, paint)
                    paint.strokeCap = Paint.Cap.ROUND
                }
                LegLine.BOAT -> {
                    paint.pathEffect = DashPathEffect(floatArrayOf(0.1f, 7f * r), 0f)
                    paint.color = ROUTE; paint.strokeWidth = 3.8f * r; c.drawPath(p, paint)
                }
                LegLine.WALK -> {
                    paint.pathEffect = DashPathEffect(floatArrayOf(0.1f, 5f * r), 0f)
                    paint.color = ROUTE; paint.strokeWidth = 2.8f * r; c.drawPath(p, paint)
                }
            }
        }
        paint.pathEffect = null
        // Recorded tracks (R16): crimson on a thin white casing, above the plan.
        input.tracks.filter { it.size >= 2 }.forEach { line ->
            val p = path(line)
            paint.color = Color.WHITE; paint.strokeWidth = 4.6f * r; c.drawPath(p, paint)
            paint.color = TRACK; paint.strokeWidth = 2.6f * r; c.drawPath(p, paint)
        }
        // Pins: later steps on top, start and end above the rest (docs/05 "Step pins").
        val colors = PinColors(ROUTE, END, ACCENT, END)
        val routed = input.steps.size >= 2
        val order = input.steps.indices.sortedBy { i -> if (routed && (i == 0 || i == input.steps.lastIndex)) input.steps.size + i else i }
        order.forEach { i ->
            val kind = when {
                !routed -> PinKind.STEP
                i == 0 -> PinKind.START
                i == input.steps.lastIndex -> PinKind.END
                else -> PinKind.STEP
            }
            val pin = pinBitmap(context, "${i + 1}", kind, false, colors, density = r)
            val (x, y) = fit.project(input.steps[i])
            c.drawBitmap(pin, (x * sx).toFloat() - pin.width / 2f, (y * sy).toFloat() - pin.height / 2f, null)
            pin.recycle()
        }
        val routing = input.routing.map(::routingLine)
        drawAttribution(c, routing + basemapLine(basemap.attribution), width, height, r)
        return Output(out, snapshot != null, (listOf(basemap.attribution) + routing).joinToString(" · "))
    }

    private fun basemapLine(attribution: String) = "© " + attribution.replace(" © ", " · ").replace("©", "").trim()

    /** The required credits (basemap R34, routers R36), bottom right on a translucent white label, one per line. */
    private fun drawAttribution(c: Canvas, credits: List<String>, width: Int, height: Int, r: Float) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.MapCredit.toArgb(); textSize = 8.5f * r }
        val pad = 3f * r
        // A credit wider than the map wraps at its " · " separators (never cut off).
        val lines = wrapCredits(credits, width - 6 * pad) { text.measureText(it) }
        val w = lines.maxOf { text.measureText(it) }
        val lineH = text.textSize + pad
        val box = RectF(width - w - 3 * pad, height - lines.size * lineH - 2 * pad, width - pad, height - pad)
        c.drawRoundRect(box, 2f * r, 2f * r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() })
        lines.forEachIndexed { i, label ->
            val right = box.right - pad - text.measureText(label)
            c.drawText(label, right, box.top + pad + (i + 1) * lineH - pad - text.descent() / 2, text)
        }
    }

    /** Splits each credit at " · " into lines no wider than [maxWidth] (a single part may still be wider). */
    internal fun wrapCredits(credits: List<String>, maxWidth: Float, measure: (String) -> Float): List<String> =
        credits.flatMap { credit ->
            val out = ArrayList<String>()
            var line = ""
            for (part in credit.split(" · ")) {
                val next = if (line.isEmpty()) part else "$line · $part"
                if (line.isNotEmpty() && measure(next) > maxWidth) {
                    out += line
                    line = part
                } else {
                    line = next
                }
            }
            if (line.isNotEmpty()) out += line
            out
        }
}

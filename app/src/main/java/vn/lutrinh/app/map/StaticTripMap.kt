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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.usecase.LegGeometry
import vn.lutrinh.app.domain.usecase.Polyline
import kotlin.coroutines.resume

/**
 * Static trip map for share exports (R18, docs/05-map.md "Static map for sharing"): the basemap
 * comes from a MapLibre [MapSnapshotter] (same style and tile store as the live maps, so an
 * offline area works in airplane mode); the legs, recorded tracks, step pins and the map credit
 * are drawn by us on a Canvas with [MapFit]'s projection. Without basemap tiles (no network, no
 * offline area, or a timeout) the same overlay is drawn on the plain map background.
 */
object StaticTripMap {
    private const val TAG = "StaticTripMap"

    /** Waiting longer than this for tiles falls back to the plain background. */
    const val SNAPSHOT_TIMEOUT_MS = 15_000L

    /** Planned route of a trip: step points in timeline order and legModes[i] = mode of leg i -> i+1. */
    data class Input(val steps: List<GeoPoint>, val legModes: List<TransportMode?>, val tracks: List<List<GeoPoint>>)

    data class Output(val bitmap: Bitmap, val withBasemap: Boolean)

    private const val ROUTE = 0xFF1F5C45.toInt()
    private const val END = 0xFFB85C2E.toInt()
    private const val ACCENT = 0xFFE0A526.toInt()
    private val TRACK = Color.parseColor(TRACK_COLOR)

    /**
     * Renders a [width] × [height] px map. [pixelRatio] = px per logical map pixel (labels, line
     * widths and pins scale with it); [paddingPx] keeps the route away from the edges.
     */
    suspend fun render(context: Context, input: Input, width: Int, height: Int, pixelRatio: Float, paddingPx: Int): Output {
        val legPaths = input.steps.zipWithNext().mapIndexed { i, (a, b) -> Polyline.simplify(LegGeometry.path(a, b, input.legModes.getOrNull(i))) }
        val points = (if (legPaths.isEmpty()) input.steps else legPaths.flatten()) + input.tracks.flatten()
        val lw = (width / pixelRatio).toInt()
        val lh = (height / pixelRatio).toInt()
        val fit = MapFit.fit(points, lw, lh, (paddingPx / pixelRatio).toInt())
        val snapshot = withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) { snapshot(context, fit, pixelRatio) }
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
        drawAttribution(c, width, height, r)
        return Output(out, snapshot != null)
    }

    /** The required map credit (MapConfig.ATTRIBUTION), bottom right on a translucent white label. */
    private fun drawAttribution(c: Canvas, width: Int, height: Int, r: Float) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A524E.toInt(); textSize = 8.5f * r }
        val label = "© " + MapConfig.ATTRIBUTION.replace(" © ", " · ").replace("©", "").trim()
        val w = text.measureText(label)
        val pad = 3f * r
        val box = RectF(width - w - 3 * pad, height - text.textSize - 3 * pad, width - pad, height - pad)
        c.drawRoundRect(box, 2f * r, 2f * r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() })
        c.drawText(label, box.left + pad, box.bottom - pad - text.descent() / 2, text)
    }

    /** The basemap at [fit]'s camera, or null when the style or tiles cannot be loaded. Runs on the main thread. */
    private suspend fun snapshot(context: Context, fit: MapFit, pixelRatio: Float): Bitmap? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val options = MapSnapshotter.Options(fit.width, fit.height)
                .withStyleBuilder(Style.Builder().fromUri(MapConfig.STYLE_URL))
                .withCameraPosition(CameraPosition.Builder().target(LatLng(fit.center.latitude, fit.center.longitude)).zoom(fit.zoom).build())
                .withPixelRatio(pixelRatio)
                .withLogo(false)
            val snapshotter = MapSnapshotter(context.applicationContext, options)
            snapshotter.setObserver(object : MapSnapshotter.Observer {
                // Same warm repaint and Vietnamese-first labels as the live maps (Style.tuneBasemap).
                override fun onDidFinishLoadingStyle() {
                    MapConfig.BASEMAP_TUNING.forEach { (id, paint) ->
                        val layer = snapshotter.getLayer(id) ?: return@forEach
                        paint.forEach { (property, color) -> colorProperty(property, color)?.let { layer.setProperties(it) } }
                    }
                    val label = Expression.coalesce(*MapConfig.LABEL_FIELD.map { Expression.get(it) }.toTypedArray())
                    MapConfig.LABEL_LAYER_IDS.forEach { id ->
                        snapshotter.getLayer(id)?.setProperties(
                            PropertyFactory.textField(label),
                            PropertyFactory.textColor(MapConfig.LABEL_COLOR),
                            PropertyFactory.textHaloColor(MapConfig.LABEL_HALO),
                        )
                    }
                }

                override fun onStyleImageMissing(imageName: String) = Unit
            })
            cont.invokeOnCancellation { android.os.Handler(android.os.Looper.getMainLooper()).post { runCatching { snapshotter.cancel() } } }
            snapshotter.start(
                { snap -> if (cont.isActive) cont.resume(snap.bitmap.copy(Bitmap.Config.ARGB_8888, false)) },
                { error ->
                    Log.w(TAG, "snapshot failed: $error")
                    if (cont.isActive) cont.resume(null)
                },
            )
        }
    }
}

package vn.lutrinh.app.map

import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.usecase.LegGeometry

/** One trip on the overall map: its steps in timeline order and the mode of each leg. */
data class MapTrip(
    val id: String,
    val color: Color,
    val points: List<GeoPoint>,
    /** legModes[i] = mode of points[i] -> points[i+1]; only flights change the shape (arc). */
    val legModes: List<TransportMode?>,
)

private const val SRC_TRIPS = "trips-src"
private const val SRC_TRIP_STOPS = "trip-stops-src"
private const val LAYER_TRIPS_CASING = "trips-casing-layer"
private const val LAYER_TRIPS = "trips-layer"
private const val LAYER_TRIP_STOPS = "trip-stops-layer"

/**
 * "Bản đồ của tôi": every trip as one line in its own colour plus a dot per step (R14).
 * Tapping a line or a dot reports the trip id. The camera fits all steps of all trips.
 */
@Composable
fun AllTripsMap(
    trips: List<MapTrip>,
    selectedId: String?,
    onTripClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    fitPaddingDp: Int = 48,
    fitExtraBottomDp: Int = 0,
    fitExtraTopDp: Int = 0,
) {
    val context = LocalContext.current
    val mapView = rememberMapView()
    val state = remember { AllTripsState() }
    val click = rememberUpdatedState(onTripClick)

    Box(modifier) {
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
                    map.applyCommonSettings(true)
                    map.setStyle(Style.Builder().fromJson(MapConfig.styleJson())) { style ->
                        style.addSource(GeoJsonSource(SRC_TRIPS))
                        style.addSource(GeoJsonSource(SRC_TRIP_STOPS))
                        val selected = Expression.get("selected")
                        style.addLayer(
                            LineLayer(LAYER_TRIPS_CASING, SRC_TRIPS).withProperties(
                                PropertyFactory.lineColor(android.graphics.Color.WHITE),
                                PropertyFactory.lineWidth(Expression.switchCase(selected, Expression.literal(9f), Expression.literal(6f))),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                            ),
                        )
                        style.addLayer(
                            LineLayer(LAYER_TRIPS, SRC_TRIPS).withProperties(
                                PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                                PropertyFactory.lineWidth(Expression.switchCase(selected, Expression.literal(5.5f), Expression.literal(3.5f))),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                            ),
                        )
                        style.addLayer(
                            CircleLayer(LAYER_TRIP_STOPS, SRC_TRIP_STOPS).withProperties(
                                PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                                PropertyFactory.circleRadius(Expression.switchCase(selected, Expression.literal(6f), Expression.literal(4.5f))),
                                PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                                PropertyFactory.circleStrokeWidth(2f),
                            ),
                        )
                        state.map = map
                        state.style = style
                        state.render(mapView)
                    }
                    map.addOnMapClickListener { latLng ->
                        val p = map.projection.toScreenLocation(latLng)
                        val r = 18f * context.resources.displayMetrics.density
                        val box = RectF(p.x - r, p.y - r, p.x + r, p.y + r)
                        val id = map.queryRenderedFeatures(box, LAYER_TRIP_STOPS, LAYER_TRIPS)
                            .firstNotNullOfOrNull { it.getStringProperty("trip") }
                            ?: return@addOnMapClickListener false
                        click.value(id)
                        true
                    }
                }
                mapView
            },
            update = {
                val density = context.resources.displayMetrics.density
                state.trips = trips
                state.selectedId = selectedId
                val p = (fitPaddingDp * density).toInt()
                state.padding = intArrayOf(p, p + (fitExtraTopDp * density).toInt(), p, p + (fitExtraBottomDp * density).toInt())
                state.render(mapView)
            },
        )
        OsmAttribution(Modifier.align(Alignment.BottomEnd))
    }
}

private class AllTripsState {
    var map: MapLibreMap? = null
    var style: Style? = null
    var trips: List<MapTrip> = emptyList()
    var selectedId: String? = null
    var padding: IntArray = intArrayOf(0, 0, 0, 0)
    private var fittedKey: Pair<List<GeoPoint>, List<Int>>? = null

    fun render(mapView: MapView) {
        val map = map ?: return
        val style = style ?: return
        if (!style.isFullyLoaded) return
        // The selected trip is drawn last so it sits on top of the others.
        val ordered = trips.sortedBy { it.id == selectedId }
        val lines = ordered.filter { it.points.size >= 2 }.map { t ->
            val path = t.points.zipWithNext().flatMapIndexed { i, (a, b) ->
                val leg = LegGeometry.path(a, b, t.legModes.getOrNull(i))
                if (i == 0) leg else leg.drop(1)
            }
            Feature.fromGeometry(LineString.fromLngLats(path.map { Point.fromLngLat(it.longitude, it.latitude) })).apply {
                addStringProperty("trip", t.id)
                addStringProperty("color", t.color.hex())
                addBooleanProperty("selected", t.id == selectedId)
            }
        }
        val stops = ordered.flatMap { t ->
            t.points.map { pt ->
                Feature.fromGeometry(Point.fromLngLat(pt.longitude, pt.latitude)).apply {
                    addStringProperty("trip", t.id)
                    addStringProperty("color", t.color.hex())
                    addBooleanProperty("selected", t.id == selectedId)
                }
            }
        }
        style.getSourceAs<GeoJsonSource>(SRC_TRIPS)?.setGeoJson(FeatureCollection.fromFeatures(lines))
        style.getSourceAs<GeoJsonSource>(SRC_TRIP_STOPS)?.setGeoJson(FeatureCollection.fromFeatures(stops))
        val points = trips.flatMap { it.points }
        val key = points to padding.toList()
        if (key != fittedKey) {
            fittedKey = key
            val pad = padding.copyOf()
            mapView.doOnLayout { fitCamera(map, points, pad) }
        }
    }
}

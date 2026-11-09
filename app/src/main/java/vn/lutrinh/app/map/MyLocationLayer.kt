package vn.lutrinh.app.map

import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.Geo
import kotlin.math.cos

/**
 * "Vị trí của tôi" (docs/05-map.md): one device fix drawn as a blue dot with its accuracy circle.
 * Nothing tracks the device while the screen is open: the dot is the fix of the last tap.
 */
data class MyLocationFix(val point: GeoPoint, val accuracyM: Float)

private const val BLUE = "#1A73E8"
private const val METRES_PER_PX_Z20 = 0.07464 // 512 px world tile at the equator

/** Adds the accuracy circle and the dot (own source [src]) above the route lines, below pins and badges. */
internal fun Style.addMyLocationLayers(src: String) {
    addSource(GeoJsonSource(src))
    addLayer(
        CircleLayer("$src-accuracy", src).withProperties(
            // Metres → px: the radius doubles with each zoom level, so interpolate exponentially (base 2).
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.exponential(2), Expression.zoom(),
                    Expression.stop(0, 0f),
                    Expression.stop(20, Expression.get("r20")),
                ),
            ),
            PropertyFactory.circleColor(BLUE),
            PropertyFactory.circleOpacity(0.16f),
            PropertyFactory.circleStrokeColor(BLUE),
            PropertyFactory.circleStrokeOpacity(0.35f),
            PropertyFactory.circleStrokeWidth(1f),
        ),
    )
    addLayer(
        CircleLayer("$src-dot", src).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(BLUE),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}

internal fun Style.showMyLocation(src: String, fix: MyLocationFix?) {
    val features = fix?.let {
        val lat = it.point.latitude
        val r20 = it.accuracyM / METRES_PER_PX_Z20 / cos(Math.toRadians(lat))
        listOf(
            Feature.fromGeometry(Point.fromLngLat(it.point.longitude, lat)).apply { addNumberProperty("r20", r20) },
        )
    }.orEmpty()
    getSourceAs<GeoJsonSource>(src)?.setGeoJson(FeatureCollection.fromFeatures(features))
}

/** Flies to [fix] at [MapConfig.MY_LOCATION_ZOOM] at least, centred in the area left free by [padding] (l, t, r, b px). */
internal fun flyToMyLocation(map: MapLibreMap, fix: MyLocationFix, padding: IntArray) {
    val from = map.cameraPosition
    val zoom = maxOf(from.zoom, MapConfig.MY_LOCATION_ZOOM).coerceAtMost(MapConfig.MAX_ZOOM)
    val target = CameraPosition.Builder()
        .target(LatLng(fix.point.latitude, fix.point.longitude))
        .zoom(zoom)
        .padding(padding.map { it.toDouble() }.toDoubleArray())
        .build()
    val km = from.target?.let { Geo.distanceKm(GeoPoint(it.latitude, it.longitude), fix.point) } ?: 0.0
    map.animateCamera(CameraUpdateFactory.newCameraPosition(target), CameraMath.flyDurationMs(km, from.zoom, zoom))
}

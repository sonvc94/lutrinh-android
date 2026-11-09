package vn.lutrinh.app.map.maplibre

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import vn.lutrinh.app.map.MyLocationFix
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.rgbHex
import kotlin.math.cos

/*
 * "Vị trí của tôi" (docs/05-map.md): the fix of the last tap as a bright-chàm dot with its accuracy
 * circle (docs/08 token ChamBright: the map-only chàm, never the system blue).
 */

private val BLUE: String = Palette.ChamBright.rgbHex()
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

package vn.lutrinh.app.map

import vn.lutrinh.app.BuildConfig

/**
 * THE single place to configure the base map.
 *
 * Today: public OpenStreetMap raster tiles. The OSM tile usage policy
 * (https://operations.osmfoundation.org/policies/tiles/) asks for a valid, identifying
 * User-Agent, visible attribution and no bulk/offline downloading. It is fine for
 * development, NOT for a production app: before release point [TILE_URL] at a paid
 * or self-hosted tile server (or switch [styleJson] to a vector style).
 */
object MapConfig {
    const val TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    const val TILE_SIZE = 256
    const val MAX_ZOOM = 19.0
    const val ATTRIBUTION = "© OpenStreetMap contributors"

    /** Identifies the app to the tile server (no personal data). */
    val USER_AGENT = "LuTrinh/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"

    /** Camera rules (docs/05-map.md). */
    const val SINGLE_POINT_ZOOM = 12.0
    const val FIT_MAX_ZOOM = 14.0
    const val OVERVIEW_ZOOM = 5.0
    const val PICKER_ZOOM = 13.0

    fun styleJson(): String = """
        {
          "version": 8,
          "name": "osm-raster",
          "sources": {
            "osm": {
              "type": "raster",
              "tiles": ["$TILE_URL"],
              "tileSize": $TILE_SIZE,
              "maxzoom": ${MAX_ZOOM.toInt()},
              "attribution": "$ATTRIBUTION"
            }
          },
          "layers": [
            { "id": "osm", "type": "raster", "source": "osm" }
          ]
        }
    """.trimIndent()
}

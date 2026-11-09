package vn.lutrinh.app.map

import vn.lutrinh.app.BuildConfig
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.theme.rgbHex

/**
 * Engine- and provider-neutral map constants (docs/05-map.md): zoom limits, camera rules, the
 * loading colour and the User-Agent. What a basemap looks like (style URL, repaint, label layers,
 * attribution) belongs to its provider in map.basemap (OpenFreeMap, EsriWorldImagery; R32-R35).
 */
object MapConfig {
    /** Vector tiles stop at z14 and are over-zoomed above; streets stay sharp. */
    const val MAX_ZOOM = 19.0

    /** Painted while the style/tiles load (and if they fail), so the map is never black. */
    val BACKGROUND: String = Palette.MapPaper.rgbHex()

    /** Identifies the app to the tile servers (no personal data). */
    val USER_AGENT = "LuTrinh/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"

    /** Camera rules (docs/05-map.md). */
    const val SINGLE_POINT_ZOOM = 12.0
    const val FIT_MAX_ZOOM = 14.0
    const val OVERVIEW_ZOOM = 5.0
    const val PICKER_ZOOM = 13.0

    /** Selecting a step flies to it at this zoom at least (village/town scale). */
    const val STEP_FOCUS_ZOOM = 11.0

    /** Selecting a leg (its mode sheet opens) fits the leg, zoomed in at most this far. */
    const val LEG_FOCUS_MAX_ZOOM = 13.0

    /** "Vị trí của tôi": the camera flies to the fix at this zoom at least (street scale), never zooming out. */
    const val MY_LOCATION_ZOOM = 15.0
}

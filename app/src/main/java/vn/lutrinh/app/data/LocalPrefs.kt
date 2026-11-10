package vn.lutrinh.app.data

import android.content.Context
import vn.lutrinh.app.domain.routing.RoutingStore
import vn.lutrinh.app.domain.usecase.ShowOnce
import vn.lutrinh.app.map.basemap.MapStyleStore

/** Small on-device flags (no account; the flags themselves never leave the phone). One file: `lutrinh_prefs`. */
class LocalPrefs(context: Context) : MapStyleStore, RoutingStore {
    private val prefs = context.getSharedPreferences("lutrinh_prefs", Context.MODE_PRIVATE)

    /** Has the user been shown "Ghi hành trình ổn định" (R22)? */
    var batteryGuideSeen: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_GUIDE_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_GUIDE_SEEN, value).apply()

    val batteryGuideOnce = ShowOnce({ batteryGuideSeen }, { batteryGuideSeen = true })

    /** Id of the chosen basemap ("light" = "Bản đồ", "satellite" = "Vệ tinh"), R32; null = default. */
    override var mapStyle: String?
        get() = prefs.getString(KEY_MAP_STYLE, null)
        set(value) = prefs.edit().putString(KEY_MAP_STYLE, value).apply()

    /** R36: road routing on (default) or off. */
    override var routingEnabled: Boolean
        get() = prefs.getBoolean(KEY_ROUTING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ROUTING_ENABLED, value).apply()

    /** R36: the one-time notice about sending stop positions to the routing server was shown. */
    override var routingNoticeSeen: Boolean
        get() = prefs.getBoolean(KEY_ROUTING_NOTICE_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_ROUTING_NOTICE_SEEN, value).apply()

    private companion object {
        const val KEY_BATTERY_GUIDE_SEEN = "battery_guide_seen"
        const val KEY_MAP_STYLE = "map_style"
        const val KEY_ROUTING_ENABLED = "routing_enabled"
        const val KEY_ROUTING_NOTICE_SEEN = "routing_notice_seen"
    }
}

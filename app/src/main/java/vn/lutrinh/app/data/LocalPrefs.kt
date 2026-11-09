package vn.lutrinh.app.data

import android.content.Context
import vn.lutrinh.app.domain.usecase.ShowOnce
import vn.lutrinh.app.map.basemap.MapStyleStore

/** Small on-device flags (no account, nothing leaves the phone). One file: `lutrinh_prefs`. */
class LocalPrefs(context: Context) : MapStyleStore {
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

    private companion object {
        const val KEY_BATTERY_GUIDE_SEEN = "battery_guide_seen"
        const val KEY_MAP_STYLE = "map_style"
    }
}

package vn.lutrinh.app.data

import android.content.Context
import vn.lutrinh.app.domain.usecase.ShowOnce

/** Small on-device flags (no account, nothing leaves the phone). One file: `lutrinh_prefs`. */
class LocalPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("lutrinh_prefs", Context.MODE_PRIVATE)

    /** Has the user been shown "Ghi hành trình ổn định" (R22)? */
    var batteryGuideSeen: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_GUIDE_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_GUIDE_SEEN, value).apply()

    val batteryGuideOnce = ShowOnce({ batteryGuideSeen }, { batteryGuideSeen = true })

    private companion object {
        const val KEY_BATTERY_GUIDE_SEEN = "battery_guide_seen"
    }
}

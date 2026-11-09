package vn.lutrinh.app.ui.picker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.map.MyLocationFix

/** One-shot device position for "Vị trí của tôi" (picker chip, map buttons): no tracking, nothing stored. */
internal object DeviceLocation {
    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Fine or coarse location is granted (R16 asks for both; either is enough to place a pin). */
    fun hasPermission(context: Context): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun isEnabled(context: Context): Boolean =
        LocationManagerCompat.isLocationEnabled(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)

    /**
     * Asks the best enabled provider for one fix (GPS with fine access, else the network one) and
     * falls back to the last known position; [onResult] gets null when nothing is available.
     * Needs [hasPermission]. Cancel with the returned signal (leaving the screen).
     */
    fun request(context: Context, onResult: (GeoPoint?) -> Unit): CancellationSignal =
        requestFix(context) { onResult(it?.point) }

    /** Like [request], with the fix's accuracy (metres) for the map's accuracy circle. */
    @Suppress("MissingPermission")
    fun requestFix(context: Context, onResult: (MyLocationFix?) -> Unit): CancellationSignal {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = buildList {
            if (fine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }
        val signal = CancellationSignal()
        fun lastKnown(): Location? = providers.firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        fun done(l: Location?) = onResult((l ?: lastKnown())?.let { MyLocationFix(GeoPoint(it.latitude, it.longitude), if (it.hasAccuracy()) it.accuracy else 0f) })
        val first = providers.firstOrNull()
        if (first == null) {
            done(null)
            return signal
        }
        LocationManagerCompat.getCurrentLocation(lm, first, signal, ContextCompat.getMainExecutor(context), Consumer { done(it) })
        return signal
    }
}

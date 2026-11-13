package vn.lutrinh.app.domain.usecase

import vn.lutrinh.app.domain.model.GeoPoint
import java.util.Locale

/**
 * "Dẫn đường" (R42): the links that hand a stop to a maps app. Pure Kotlin, mirrored in Swift
 * (docs/07-ios-port.md). Coordinates are written with 6 decimals and a dot (never the device's
 * decimal comma); the name is percent-encoded.
 */
object StopNavigation {
    /**
     * The Android/web `geo:` URI of the stop: `geo:<lat>,<lng>?q=<lat>,<lng>(<name>)`, so a maps app
     * shows a pin with the stop's name. A name with parentheses would end the label early, so they
     * are replaced by spaces; a blank name leaves the pin without a label.
     */
    fun geoUri(at: GeoPoint, name: String): String {
        val c = coords(at)
        val label = name.replace('(', ' ').replace(')', ' ').trim()
        return "geo:$c?q=$c" + if (label.isEmpty()) "" else "(" + encode(label) + ")"
    }

    /**
     * Google Maps directions to the stop (the browser fallback in the app and the link on the
     * share page): the phone's or computer's own start point is used. The `&` is raw here; HTML
     * escapes it where the URL becomes an attribute.
     */
    fun mapsUrl(at: GeoPoint): String = "https://www.google.com/maps/dir/?api=1&destination=${coords(at)}"

    /** "lat,lng" with 6 decimals each. */
    fun coords(at: GeoPoint): String =
        String.format(Locale.ROOT, "%.6f,%.6f", at.latitude, at.longitude)

    /** Percent-encoding of the UTF-8 bytes; everything but unreserved characters is escaped (space = %20). */
    fun encode(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') sb.append(ch)
            else sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
        return sb.toString()
    }
}

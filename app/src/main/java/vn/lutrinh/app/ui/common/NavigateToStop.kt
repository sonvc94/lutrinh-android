package vn.lutrinh.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.usecase.StopNavigation

/**
 * "Dẫn đường" (R42): hands the stop to a maps app on the phone with a `geo:` view intent (the
 * system picks or asks, as several maps apps may be installed). With no such app, or when it
 * refuses, Google Maps directions open in the browser; with no browser either, nothing happens
 * (never a crash, also offline: nothing is requested from here).
 */
fun Context.navigateToStop(step: Step) {
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse(StopNavigation.geoUri(step.location, step.title)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(geo)
        Log.i("NavigateStop", "geo ${geo.dataString}")
        return
    } catch (_: ActivityNotFoundException) {
        // fall through to the browser
    }
    val web = Intent(Intent.ACTION_VIEW, Uri.parse(StopNavigation.mapsUrl(step.location))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(web)
        Log.i("NavigateStop", "web ${web.dataString}")
    } catch (_: ActivityNotFoundException) {
        Log.w("NavigateStop", "no app can show ${web.dataString}")
    }
}

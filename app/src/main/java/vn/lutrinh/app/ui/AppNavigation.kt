package vn.lutrinh.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import vn.lutrinh.app.ui.theme.Motion
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import vn.lutrinh.app.ui.common.LocalNavAnimatedScope
import vn.lutrinh.app.ui.common.LocalSharedTransitionScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import vn.lutrinh.app.LuTrinhApp
import vn.lutrinh.app.domain.usecase.StopOutcome
import vn.lutrinh.app.domain.usecase.TrackFormat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.Dwell
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.SharedPlaces
import vn.lutrinh.app.ui.share.SharePlaceScreen
import vn.lutrinh.app.ui.offline.OfflineMapsScreen
import vn.lutrinh.app.ui.photos.PhotoViewerScreen
import vn.lutrinh.app.ui.picker.LocationPickerScreen
import vn.lutrinh.app.ui.share.ShareTripScreen
import vn.lutrinh.app.ui.steps.FromPhotosScreen
import vn.lutrinh.app.ui.steps.StepDetailScreen
import vn.lutrinh.app.ui.steps.StepEditScreen
import vn.lutrinh.app.ui.tracks.BatteryGuideScreen
import vn.lutrinh.app.ui.settings.RoutingNoticeDialog
import vn.lutrinh.app.ui.settings.SettingsScreen
import vn.lutrinh.app.ui.routes.RouteCodeScreen
import vn.lutrinh.app.ui.trips.TripDetailScreen
import vn.lutrinh.app.ui.trips.TripEditScreen
import vn.lutrinh.app.ui.trips.TripListScreen
import vn.lutrinh.app.ui.trips.TripMapScreen
import vn.lutrinh.app.ui.trips.MyMapScreen

/** Route table — mirrored in docs/03-screens.md. */
object Routes {
    const val TRIPS = "trips"
    const val MY_MAP = "myMap"
    const val TRIP_EDIT = "tripEdit?tripId={tripId}&record={record}&shared={shared}&sharedTitle={sharedTitle}" +
        "&sharedLat={sharedLat}&sharedLng={sharedLng}&sharedGap={sharedGap}&sharedGuessLat={sharedGuessLat}&sharedGuessLng={sharedGuessLng}"
    const val TRIP = "trip/{tripId}?record={record}&fromRoute={fromRoute}"
    const val TRIP_MAP = "trip/{tripId}/map"
    const val STEP_EDIT =
        "trip/{tripId}/stepEdit?stepId={stepId}&stopTrack={stopTrack}&stopStart={stopStart}&stopEnd={stopEnd}" +
            "&lat={lat}&lng={lng}&title={title}" +
            "&shared={shared}&sharedTitle={sharedTitle}&sharedLat={sharedLat}&sharedLng={sharedLng}&sharedGap={sharedGap}&sharedGuessLat={sharedGuessLat}&sharedGuessLng={sharedGuessLng}"
    const val STEP = "trip/{tripId}/step/{stepId}"
    const val PICKER = "trip/{tripId}/pick?stepId={stepId}&lat={lat}&lng={lng}"
    const val PHOTOS = "trip/{tripId}/step/{stepId}/photos?index={index}"
    const val FROM_PHOTOS = "trip/{tripId}/fromPhotos"
    const val OFFLINE = "offline?tripId={tripId}"
    const val SHARE = "trip/{tripId}/share"
    const val BATTERY_GUIDE = "batteryGuide"
    const val SETTINGS = "settings"
    const val ROUTE_CODE = "routeCode?code={code}"
    const val SHARE_PLACE = "sharePlace?text={text}"

    fun tripEdit(tripId: String? = null) = if (tripId == null) "tripEdit" else "tripEdit?tripId=$tripId"

    /** "Tạo chuyến mới và ghi" (R16): the new-trip form; once saved, the new trip opens and starts recording. */
    fun tripEditAndRecord() = "tripEdit?record=true"
    fun trip(tripId: String, record: Boolean = false) = if (record) "trip/$tripId?record=true" else "trip/$tripId"
    fun tripMap(tripId: String) = "trip/$tripId/map"

    /** A trip just downloaded from a route (R40): its snackbar; [withMap] = an offline map is on its way. */
    fun tripFromRoute(tripId: String, withMap: Boolean) = "trip/$tripId?fromRoute=${if (withMap) "map" else "nomap"}"

    /** "Nhập mã lộ trình" (R40), optionally with the code filled in and looked up. */
    /** "Thêm vào chuyến đi" (R43): the text of a Google Maps share. */
    fun sharePlace(text: String) = "sharePlace?text=${Uri.encode(text)}"

    /** The shared place as navigation arguments (R43): `shared=1`, its name, its position or why it has none. */
    private fun sharedArgs(r: SharedPlaces.Resolved) = buildString {
        append("shared=1&sharedTitle=${Uri.encode(r.name)}")
        when (r) {
            is SharedPlaces.Resolved.Found -> append("&sharedLat=${r.point.latitude}&sharedLng=${r.point.longitude}")
            is SharedPlaces.Resolved.Unreachable -> append("&sharedGap=offline")
            is SharedPlaces.Resolved.NoCoordinates -> append("&sharedGap=none")
        }
        r.guess?.let { append("&sharedGuessLat=${it.latitude}&sharedGuessLng=${it.longitude}") }
    }

    /** New stop of an existing trip, prefilled from a Google Maps share (R43). */
    fun stepFromShare(tripId: String, r: SharedPlaces.Resolved) = "trip/$tripId/stepEdit?${sharedArgs(r)}"

    /** "Chuyến đi mới" from a Google Maps share (R43): the new-trip form; once saved, the stop form follows. */
    fun tripEditFromShare(r: SharedPlaces.Resolved) = "tripEdit?${sharedArgs(r)}"

    fun routeCode(code: String? = null) = if (code == null) "routeCode" else "routeCode?code=$code"
    fun share(tripId: String) = "trip/$tripId/share"
    fun stepEdit(tripId: String, stepId: String? = null) =
        if (stepId == null) "trip/$tripId/stepEdit" else "trip/$tripId/stepEdit?stepId=$stepId"
    /** New step prefilled from a stop suggestion (R17). */
    fun stepFromStop(tripId: String, d: Dwell, title: String) =
        "trip/$tripId/stepEdit?stopTrack=${Uri.encode(d.trackId)}&stopStart=${d.startAt}&stopEnd=${d.endAt}" +
            "&lat=${d.center.latitude}&lng=${d.center.longitude}&title=${Uri.encode(title)}"
    fun step(tripId: String, stepId: String) = "trip/$tripId/step/$stepId"
    fun picker(tripId: String, stepId: String?, at: GeoPoint?) = buildString {
        append("trip/$tripId/pick?")
        val params = mutableListOf<String>()
        if (stepId != null) params += "stepId=$stepId"
        if (at != null) params += listOf("lat=${at.latitude}", "lng=${at.longitude}")
        append(params.joinToString("&"))
    }
    fun fromPhotos(tripId: String) = "trip/$tripId/fromPhotos"
    fun offline(tripId: String? = null) = if (tripId == null) "offline" else "offline?tripId=$tripId"
    fun photos(tripId: String, stepId: String, index: Int) = "trip/$tripId/step/$stepId/photos?index=$index"
}

private const val PICKED_LOCATION = "picked_location"

private fun pickedPoint(v: String): GeoPoint = v.split(',', limit = 3).let { GeoPoint(it[0].toDouble(), it[1].toDouble()) }

private fun pickedName(v: String): String? = v.split(',', limit = 3).getOrNull(2)?.takeIf { it.isNotBlank() }

/** The shared place back from a route's arguments (R43). */
private fun sharedResolved(a: android.os.Bundle?): SharedPlaces.Resolved {
    val name = a?.getString("sharedTitle").orEmpty()
    val lat = a?.getString("sharedLat")?.toDoubleOrNull()
    val lng = a?.getString("sharedLng")?.toDoubleOrNull()
    val guess = a?.getString("sharedGuessLat")?.toDoubleOrNull()?.let { gl -> a.getString("sharedGuessLng")?.toDoubleOrNull()?.let { GeoPoint(gl, it) } }
    return when {
        lat != null && lng != null -> SharedPlaces.Resolved.Found(name, null, GeoPoint(lat, lng))
        a?.getString("sharedGap") == "offline" -> SharedPlaces.Resolved.Unreachable(name, null, guess)
        else -> SharedPlaces.Resolved.NoCoordinates(name, null, guess = guess)
    }
}

private fun optional(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

private fun required(name: String) = navArgument(name) { type = NavType.StringType }

@Composable
fun AppNavigation(
    nav: NavHostController = rememberNavController(),
    /** Text of a Google Maps share waiting to be opened (R43); [onShareHandled] clears it. */
    incomingShare: String? = null,
    onShareHandled: () -> Unit = {},
) {
    // One snackbar for the whole app: a recording is also stopped from its notification (R16).
    val context = LocalContext.current
    val recorder = (context.applicationContext as LuTrinhApp).container.recorder
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(recorder) {
        coroutineScope {
            recorder.stopped.collect { outcome ->
                val text = when (outcome) {
                    is StopOutcome.Saved -> context.getString(R.string.track_saved, TrackFormat.distance(outcome.track.distanceM))
                    is StopOutcome.Dropped -> context.getString(R.string.track_dropped)
                }
                launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(text) }
            }
        }
    }
    LaunchedEffect(incomingShare) {
        if (incomingShare != null) {
            // A second share replaces the first one's screen (its view model holds the first text).
            nav.popBackStack(Routes.SHARE_PLACE, inclusive = true)
            nav.navigate(Routes.sharePlace(incomingShare))
            onShareHandled()
        }
    }
    Box(Modifier.fillMaxSize()) {
        NavGraph(nav)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp))
    }
    // R36: once, before the first stop position is sent to the routing server.
    val container = (context.applicationContext as LuTrinhApp).container
    val noticeDue by container.routeSync.noticeDue.collectAsStateWithLifecycle()
    if (noticeDue) {
        RoutingNoticeDialog(
            onAccept = { container.routingSettings.markNoticeSeen() },
            onTurnOff = { container.routingSettings.setEnabled(false); container.routingSettings.markNoticeSeen() },
        )
    }
}

/** R37: the NavHost sits in a shared-transition scope so a trip-map card can grow into its step detail. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NavGraph(nav: NavHostController) = SharedTransitionLayout {
  val routeServer = (LocalContext.current.applicationContext as LuTrinhApp).container.routeServerConfigured
  val slidePx = with(LocalDensity.current) { Motion.SCREEN_SLIDE_DP.dp.roundToPx() }
  val fade = tween<Float>(Motion.SCREEN_MS, easing = Motion.EaseInOut)
  val slide = tween<IntOffset>(Motion.SCREEN_MS, easing = Motion.EaseInOut)
  // Trip map ↔ step detail: the card's shared bounds do the moving, so these two only fade (R37).
  fun AnimatedContentTransitionScope<NavBackStackEntry>.cardPair() =
      setOf(initialState.destination.route, targetState.destination.route) == setOf(Routes.TRIP_MAP, Routes.STEP)
  CompositionLocalProvider(LocalSharedTransitionScope provides this) {
    NavHost(
        navController = nav,
        startDestination = Routes.TRIPS,
        enterTransition = { fadeIn(fade) + if (cardPair()) EnterTransition.None else slideInHorizontally(slide) { slidePx } },
        exitTransition = { fadeOut(fade) + if (cardPair()) ExitTransition.None else slideOutHorizontally(slide) { -slidePx } },
        popEnterTransition = { fadeIn(fade) + if (cardPair()) EnterTransition.None else slideInHorizontally(slide) { -slidePx } },
        popExitTransition = { fadeOut(fade) + if (cardPair()) ExitTransition.None else slideOutHorizontally(slide) { slidePx } },
    ) {
        composable(Routes.TRIPS) {
            TripListScreen(
                onCreate = { nav.navigate(Routes.tripEdit()) },
                onOpen = { nav.navigate(Routes.trip(it)) },
                onEdit = { nav.navigate(Routes.tripEdit(it)) },
                onShare = { nav.navigate(Routes.share(it)) },
                onOpenMyMap = { nav.navigate(Routes.MY_MAP) },
                onOpenOffline = { nav.navigate(Routes.offline()) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onEnterCode = if (routeServer) ({ nav.navigate(Routes.routeCode()) }) else null,
            )
        }
        composable(Routes.ROUTE_CODE, arguments = listOf(optional("code"))) {
            RouteCodeScreen(
                onBack = { nav.popBackStack() },
                onOpenTrip = { id, withMap ->
                    nav.navigate(Routes.tripFromRoute(id, withMap)) { popUpTo(Routes.TRIPS) { inclusive = false } }
                },
            )
        }
        composable(Routes.OFFLINE, arguments = listOf(optional("tripId"))) {
            OfflineMapsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.MY_MAP) {
            MyMapScreen(onBack = { nav.popBackStack() }, onOpenTrip = { nav.navigate(Routes.trip(it)) })
        }
        composable(Routes.SHARE_PLACE, arguments = listOf(optional("text"))) {
            SharePlaceScreen(
                onBack = { nav.popBackStack() },
                onPick = { tripId, resolved ->
                    if (tripId == null) {
                        nav.navigate(Routes.tripEditFromShare(resolved)) { popUpTo(Routes.SHARE_PLACE) { inclusive = true } }
                    } else {
                        // Back from the stop form lands on the trip, as after "Thêm điểm dừng".
                        nav.navigate(Routes.trip(tripId)) { popUpTo(Routes.SHARE_PLACE) { inclusive = true } }
                        nav.navigate(Routes.stepFromShare(tripId, resolved))
                    }
                },
            )
        }
        composable(
            Routes.TRIP_EDIT,
            arguments = listOf(optional("tripId"), optional("record")) + listOf("shared", "sharedTitle", "sharedLat", "sharedLng", "sharedGap", "sharedGuessLat", "sharedGuessLng").map { optional(it) },
        ) { entry ->
            val record = entry.arguments?.getString("record") == "true"
            val shared = entry.arguments?.getString("shared") == "1"
            TripEditScreen(
                onBack = { nav.popBackStack() },
                onSaved = { id, created ->
                    nav.popBackStack()
                    if (created) nav.navigate(Routes.trip(id, record))
                    // R43: the trip made for a shared place goes straight on to that place's stop form.
                    if (created && shared) nav.navigate(Routes.stepFromShare(id, sharedResolved(entry.arguments)))
                },
                onDeleted = { nav.popBackStack(Routes.TRIPS, inclusive = false) },
            )
        }
        composable(Routes.TRIP, arguments = listOf(required("tripId"), optional("record"), optional("fromRoute"))) { entry ->
            val tripId = entry.arguments?.getString("tripId")!!
            TripDetailScreen(
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.tripEdit(tripId)) },
                onOpenMap = { nav.navigate(Routes.tripMap(tripId)) },
                onShare = { nav.navigate(Routes.share(tripId)) },
                onAddStep = { nav.navigate(Routes.stepEdit(tripId)) },
                onAddFromStop = { dwell, title -> nav.navigate(Routes.stepFromStop(tripId, dwell, title)) },
                onAddFromPhotos = { nav.navigate(Routes.fromPhotos(tripId)) },
                onOpenStep = { nav.navigate(Routes.step(tripId, it)) },
                onNewTripAndRecord = { nav.navigate(Routes.tripEditAndRecord()) },
                onOpenBatteryGuide = { nav.navigate(Routes.BATTERY_GUIDE) },
                autoRecord = entry.arguments?.getString("record") == "true",
                fromRoute = entry.arguments?.getString("fromRoute"),
                onOpenOffline = { nav.navigate(Routes.offline(tripId)) },
                onEnterCode = { nav.navigate(Routes.routeCode(it)) },
            )
        }
        composable(Routes.SHARE, arguments = listOf(required("tripId"))) {
            ShareTripScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.TRIP_MAP, arguments = listOf(required("tripId"))) { entry ->
            val tripId = entry.arguments?.getString("tripId")!!
            CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                TripMapScreen(
                    onBack = { nav.popBackStack() },
                    onOpenStep = { nav.navigate(Routes.step(tripId, it)) },
                    onOpenOffline = { nav.navigate(Routes.offline(tripId)) },
                    onNewTripAndRecord = { nav.navigate(Routes.tripEditAndRecord()) },
                    onOpenBatteryGuide = { nav.navigate(Routes.BATTERY_GUIDE) },
                )
            }
        }
        composable(Routes.BATTERY_GUIDE) { BatteryGuideScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(
            Routes.STEP_EDIT,
            arguments = listOf(required("tripId"), optional("stepId")) +
                listOf("stopTrack", "stopStart", "stopEnd", "lat", "lng", "title", "shared", "sharedTitle", "sharedLat", "sharedLng", "sharedGap", "sharedGuessLat", "sharedGuessLng").map { optional(it) },
        ) { entry ->
            val tripId = entry.arguments?.getString("tripId")!!
            val stepId = entry.arguments?.getString("stepId")
            val picked by entry.savedStateHandle.getStateFlow<String?>(PICKED_LOCATION, null).collectAsState()
            StepEditScreen(
                pickedLocation = picked?.let(::pickedPoint),
                pickedName = picked?.let(::pickedName),
                onPickedConsumed = { entry.savedStateHandle[PICKED_LOCATION] = null },
                onPickLocation = { at -> nav.navigate(Routes.picker(tripId, stepId, at)) },
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
                onDeleted = { nav.popBackStack(Routes.TRIP, inclusive = false) },
            )
        }
        composable(Routes.FROM_PHOTOS, arguments = listOf(required("tripId"))) { entry ->
            val tripId = entry.arguments?.getString("tripId")!!
            val context = LocalContext.current
            val picked by entry.savedStateHandle.getStateFlow<String?>(PICKED_LOCATION, null).collectAsState()
            FromPhotosScreen(
                pickedLocation = picked?.let(::pickedPoint),
                pickedName = picked?.let(::pickedName),
                onPickedConsumed = { entry.savedStateHandle[PICKED_LOCATION] = null },
                onPickLocation = { at -> nav.navigate(Routes.picker(tripId, null, at)) },
                onBack = { nav.popBackStack() },
                onDone = { created, unreadable ->
                    val messages = listOfNotNull(
                        context.getString(R.string.from_photos_created, created).takeIf { created > 0 },
                        context.getString(R.string.step_photos_import_failed, unreadable).takeIf { unreadable > 0 },
                    )
                    if (messages.isNotEmpty()) {
                        Toast.makeText(context, messages.joinToString(" · "), Toast.LENGTH_SHORT).show()
                    }
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.STEP, arguments = listOf(required("tripId"), required("stepId"))) { entry ->
            val tripId = entry.arguments?.getString("tripId")!!
            val stepId = entry.arguments?.getString("stepId")!!
            CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                StepDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.stepEdit(tripId, stepId)) },
                    onOpenPhoto = { nav.navigate(Routes.photos(tripId, stepId, it)) },
                    // Replace this step with its neighbour, so Back keeps returning to where the user came from.
                    onOpenStep = { id -> nav.navigate(Routes.step(tripId, id)) { popUpTo(Routes.STEP) { inclusive = true } } },
                )
            }
        }
        composable(
            Routes.PICKER,
            arguments = listOf(required("tripId"), optional("stepId"), optional("lat"), optional("lng")),
        ) {
            LocationPickerScreen(
                onBack = { nav.popBackStack() },
                onPicked = { p, name ->
                    // "lat,lng" or "lat,lng,<name of the search result>" (R24)
                    val value = "${p.latitude},${p.longitude}" + (name?.let { ",$it" } ?: "")
                    nav.previousBackStackEntry?.savedStateHandle?.set(PICKED_LOCATION, value)
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.PHOTOS, arguments = listOf(required("tripId"), required("stepId"), optional("index"))) {
            PhotoViewerScreen(onClose = { nav.popBackStack() })
        }
    }
  }
}

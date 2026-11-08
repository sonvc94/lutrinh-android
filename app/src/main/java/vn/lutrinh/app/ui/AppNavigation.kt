package vn.lutrinh.app.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.ui.photos.PhotoViewerScreen
import vn.lutrinh.app.ui.picker.LocationPickerScreen
import vn.lutrinh.app.ui.steps.FromPhotosScreen
import vn.lutrinh.app.ui.steps.StepDetailScreen
import vn.lutrinh.app.ui.steps.StepEditScreen
import vn.lutrinh.app.ui.trips.TripDetailScreen
import vn.lutrinh.app.ui.trips.TripEditScreen
import vn.lutrinh.app.ui.trips.TripListScreen
import vn.lutrinh.app.ui.trips.TripMapScreen
import vn.lutrinh.app.ui.trips.MyMapScreen

/** Route table — mirrored in docs/03-screens.md. */
object Routes {
    const val TRIPS = "trips"
    const val MY_MAP = "myMap"
    const val TRIP_EDIT = "tripEdit?tripId={tripId}"
    const val TRIP = "trip/{tripId}"
    const val TRIP_MAP = "trip/{tripId}/map"
    const val STEP_EDIT = "trip/{tripId}/stepEdit?stepId={stepId}"
    const val STEP = "trip/{tripId}/step/{stepId}"
    const val PICKER = "trip/{tripId}/pick?stepId={stepId}&lat={lat}&lng={lng}"
    const val PHOTOS = "trip/{tripId}/step/{stepId}/photos?index={index}"
    const val FROM_PHOTOS = "trip/{tripId}/fromPhotos"

    fun tripEdit(tripId: String? = null) = if (tripId == null) "tripEdit" else "tripEdit?tripId=$tripId"
    fun trip(tripId: String) = "trip/$tripId"
    fun tripMap(tripId: String) = "trip/$tripId/map"
    fun stepEdit(tripId: String, stepId: String? = null) =
        if (stepId == null) "trip/$tripId/stepEdit" else "trip/$tripId/stepEdit?stepId=$stepId"
    fun step(tripId: String, stepId: String) = "trip/$tripId/step/$stepId"
    fun picker(tripId: String, stepId: String?, at: GeoPoint?) = buildString {
        append("trip/$tripId/pick?")
        val params = mutableListOf<String>()
        if (stepId != null) params += "stepId=$stepId"
        if (at != null) params += listOf("lat=${at.latitude}", "lng=${at.longitude}")
        append(params.joinToString("&"))
    }
    fun fromPhotos(tripId: String) = "trip/$tripId/fromPhotos"
    fun photos(tripId: String, stepId: String, index: Int) = "trip/$tripId/step/$stepId/photos?index=$index"
}

private const val PICKED_LOCATION = "picked_location"

private fun optional(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

private fun required(name: String) = navArgument(name) { type = NavType.StringType }

@Composable
fun AppNavigation(nav: NavHostController = rememberNavController()) {
    NavHost(navController = nav, startDestination = Routes.TRIPS) {
        composable(Routes.TRIPS) {
            TripListScreen(
                onCreate = { nav.navigate(Routes.tripEdit()) },
                onOpen = { nav.navigate(Routes.trip(it)) },
                onEdit = { nav.navigate(Routes.tripEdit(it)) },
                onOpenMyMap = { nav.navigate(Routes.MY_MAP) },
            )
        }
        composable(Routes.MY_MAP) {
            MyMapScreen(onBack = { nav.popBackStack() }, onOpenTrip = { nav.navigate(Routes.trip(it)) })
        }
        composable(Routes.TRIP_EDIT, arguments = listOf(optional("tripId"))) {
            TripEditScreen(
                onBack = { nav.popBackStack() },
                onSaved = { id, created ->
                    nav.popBackStack()
                    if (created) nav.navigate(Routes.trip(id))
                },
                onDeleted = { nav.popBackStack(Routes.TRIPS, inclusive = false) },
            )
        }
        composable(Routes.TRIP, arguments = listOf(required("tripId"))) { entry ->
            val tripId = entry.arguments?.getString("tripId")!!
            TripDetailScreen(
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.tripEdit(tripId)) },
                onOpenMap = { nav.navigate(Routes.tripMap(tripId)) },
                onAddStep = { nav.navigate(Routes.stepEdit(tripId)) },
                onAddFromPhotos = { nav.navigate(Routes.fromPhotos(tripId)) },
                onOpenStep = { nav.navigate(Routes.step(tripId, it)) },
            )
        }
        composable(Routes.TRIP_MAP, arguments = listOf(required("tripId"))) { entry ->
            val tripId = entry.arguments?.getString("tripId")!!
            TripMapScreen(onBack = { nav.popBackStack() }, onOpenStep = { nav.navigate(Routes.step(tripId, it)) })
        }
        composable(Routes.STEP_EDIT, arguments = listOf(required("tripId"), optional("stepId"))) { entry ->
            val tripId = entry.arguments?.getString("tripId")!!
            val stepId = entry.arguments?.getString("stepId")
            val picked by entry.savedStateHandle.getStateFlow<String?>(PICKED_LOCATION, null).collectAsState()
            StepEditScreen(
                pickedLocation = picked?.split(',')?.let { GeoPoint(it[0].toDouble(), it[1].toDouble()) },
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
                pickedLocation = picked?.split(',')?.let { GeoPoint(it[0].toDouble(), it[1].toDouble()) },
                onPickedConsumed = { entry.savedStateHandle[PICKED_LOCATION] = null },
                onPickLocation = { at -> nav.navigate(Routes.picker(tripId, null, at)) },
                onBack = { nav.popBackStack() },
                onDone = { created ->
                    if (created > 0) {
                        Toast.makeText(context, context.getString(R.string.from_photos_created, created), Toast.LENGTH_SHORT).show()
                    }
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.STEP, arguments = listOf(required("tripId"), required("stepId"))) { entry ->
            val tripId = entry.arguments?.getString("tripId")!!
            val stepId = entry.arguments?.getString("stepId")!!
            StepDetailScreen(
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.stepEdit(tripId, stepId)) },
                onOpenPhoto = { nav.navigate(Routes.photos(tripId, stepId, it)) },
            )
        }
        composable(
            Routes.PICKER,
            arguments = listOf(required("tripId"), optional("stepId"), optional("lat"), optional("lng")),
        ) {
            LocationPickerScreen(
                onBack = { nav.popBackStack() },
                onPicked = { p ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(PICKED_LOCATION, "${p.latitude},${p.longitude}")
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.PHOTOS, arguments = listOf(required("tripId"), required("stepId"), optional("index"))) {
            PhotoViewerScreen(onClose = { nav.popBackStack() })
        }
    }
}

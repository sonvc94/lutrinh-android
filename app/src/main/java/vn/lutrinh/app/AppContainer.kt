package vn.lutrinh.app

import android.content.Context
import vn.lutrinh.app.data.BackupManager
import vn.lutrinh.app.data.GalleryReader
import vn.lutrinh.app.data.LocalPrefs
import vn.lutrinh.app.data.MediaCapture
import vn.lutrinh.app.data.PhotoMetaReader
import vn.lutrinh.app.data.PhotoStorage
import vn.lutrinh.app.data.PlaceIndex
import vn.lutrinh.app.data.RoomLegRouteRepository
import vn.lutrinh.app.data.RoomRouteRepository
import vn.lutrinh.app.data.RouteDownloads
import vn.lutrinh.app.catalog.Catalogs
import vn.lutrinh.app.domain.catalog.RouteCatalog
import vn.lutrinh.app.data.RoomStepRepository
import vn.lutrinh.app.data.RoomTripImportRepository
import vn.lutrinh.app.data.RoomStopDecisionRepository
import vn.lutrinh.app.data.RoomTrackRepository
import vn.lutrinh.app.data.RoomTripRepository
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.repository.IdGenerator
import vn.lutrinh.app.domain.routing.LegRouteRepository
import vn.lutrinh.app.domain.routing.RouteSync
import vn.lutrinh.app.domain.routing.RoutingProviders
import vn.lutrinh.app.domain.routing.RoutingSettings
import vn.lutrinh.app.domain.usecase.DeleteStep
import vn.lutrinh.app.domain.usecase.DeleteTrack
import vn.lutrinh.app.domain.usecase.DeleteTrip
import vn.lutrinh.app.domain.usecase.ExportGpx
import vn.lutrinh.app.domain.usecase.ImportGpx
import vn.lutrinh.app.domain.usecase.SaveStep
import vn.lutrinh.app.domain.usecase.SaveTrip
import vn.lutrinh.app.domain.usecase.PublishStep
import vn.lutrinh.app.domain.usecase.SetLegNote
import vn.lutrinh.app.domain.usecase.SetLegTransport
import vn.lutrinh.app.domain.usecase.SetTripCover
import vn.lutrinh.app.domain.usecase.TrackRecorder
import vn.lutrinh.app.map.NetworkMonitor
import vn.lutrinh.app.map.MapEngine
import vn.lutrinh.app.map.MapEngines
import vn.lutrinh.app.map.OfflineMapStore
import vn.lutrinh.app.map.basemap.BasemapSettings
import vn.lutrinh.app.map.basemap.Basemaps
import vn.lutrinh.app.routing.Routers
import vn.lutrinh.app.share.TripExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.LocalDate
import java.util.UUID

/** Manual dependency wiring (no DI framework, easy to mirror in Swift). */
class AppContainer(context: Context) {
    private val db = AppDatabase.build(context)

    val clock: Clock = object : Clock {
        override fun nowMillis() = System.currentTimeMillis()
        override fun today(): LocalDate = LocalDate.now()
    }
    val ids = IdGenerator { UUID.randomUUID().toString() }

    val trips = RoomTripRepository(db)
    val steps = RoomStepRepository(db)
    val photos = PhotoStorage(context)
    /** App assets (debug builds also carry the sample photos, data/SampleData.kt). */
    val assets: android.content.res.AssetManager = context.applicationContext.assets
    val photoMeta = PhotoMetaReader(context)
    /** System-camera capture files (R28 photo, R29 video). */
    val capture = MediaCapture(context)
    /** Gallery photos of a stop (R19); read-only. */
    val gallery = GalleryReader(context)
    val tracks = RoomTrackRepository(db)
    val stopDecisions = RoomStopDecisionRepository(db)

    /** Work that must outlive a screen (recording commands, start-up recovery). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val recorder = TrackRecorder(tracks, trips, clock, ids)

    val saveTrip = SaveTrip(trips, steps, clock, ids)
    val deleteTrip = DeleteTrip(trips, photos, recorder)
    val saveStep = SaveStep(trips, steps, photos, clock, ids)
    val deleteStep = DeleteStep(steps, photos)
    val setLegTransport = SetLegTransport(steps, clock)
    val setLegNote = SetLegNote(steps, clock)
    val publishStep = PublishStep(steps, clock)
    val setTripCover = SetTripCover(trips, clock)
    val deleteTrack = DeleteTrack(tracks, recorder)

    /** Offline gazetteer of Vietnam for place search and auto names (R23, R24). */
    val places = PlaceIndex(context, appScope)

    /** On-device flags, e.g. whether the battery guide has been shown (R22). */
    val prefs = LocalPrefs(context)

    val network = NetworkMonitor(context)

    /** The map engine (MapLibre today) and the basemap choice "Bản đồ" / "Vệ tinh" (R32-R35). */
    val mapEngine: MapEngine = MapEngines.active
    val basemaps = BasemapSettings(prefs, Basemaps.registry)

    /** Offline areas (R15) of the offline basemap ("Bản đồ"; satellite imagery may not be stored). */
    val offlineMaps: OfflineMapStore = mapEngine.createOfflineStore(context, network, Basemaps.registry.offline.style)

    /** "Chia sẻ chuyến đi" (R18): summary image + page export, always on the offline basemap. */
    val exporter = TripExporter(context, photos, mapEngine, Basemaps.registry.offline)

    /** GPX export/import of one trip (R25). */
    val exportGpx = ExportGpx(trips, steps, tracks, clock)
    val importGpx = ImportGpx(RoomTripImportRepository(db), clock, ids)

    /** Road-following legs (R36): stored routes, the switch + notice flag, the routers, the fetch queue. */
    val legRoutes: LegRouteRepository = RoomLegRouteRepository(db)
    val routingSettings = RoutingSettings(prefs)
    val routers: RoutingProviders = Routers.registry
    val routeSync = RouteSync(
        steps.observeAllSteps(), legRoutes, routingSettings, network.online, routers, clock,
        log = { android.util.Log.i("Routing", it) },
    )

    /**
     * Ready-made routes by code (R40): the route source (the Lữ Trình server, catalog/Catalogs.kt),
     * downloaded trips + their links, and the background downloads. [routeCatalog] is a var only
     * so tests can put a fake source in.
     */
    var routeCatalog: RouteCatalog = Catalogs.server
    val routeServerConfigured: Boolean get() = Catalogs.serverConfigured
    val routes = RoomRouteRepository(db, photos.photoDir) { photos.delete(it) }
    val routeDownloads = RouteDownloads(context.applicationContext, prefs)

    /** Follows a shared Google Maps link to its position (R43); a var so tests can put a fake in. */
    var linkFollower: vn.lutrinh.app.domain.usecase.LinkFollower = vn.lutrinh.app.sharein.GoogleMapsLinks()

    /** Local backup/restore of all user data (R26). */
    val backup = BackupManager(context, db, photos.photoDir, BuildConfig.VERSION_NAME)
}

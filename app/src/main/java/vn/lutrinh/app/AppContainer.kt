package vn.lutrinh.app

import android.content.Context
import vn.lutrinh.app.data.BackupManager
import vn.lutrinh.app.data.GalleryReader
import vn.lutrinh.app.data.LocalPrefs
import vn.lutrinh.app.data.MediaCapture
import vn.lutrinh.app.data.PhotoMetaReader
import vn.lutrinh.app.data.PhotoStorage
import vn.lutrinh.app.data.PlaceIndex
import vn.lutrinh.app.data.RoomStepRepository
import vn.lutrinh.app.data.RoomTripImportRepository
import vn.lutrinh.app.data.RoomStopDecisionRepository
import vn.lutrinh.app.data.RoomTrackRepository
import vn.lutrinh.app.data.RoomTripRepository
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.repository.IdGenerator
import vn.lutrinh.app.domain.usecase.DeleteStep
import vn.lutrinh.app.domain.usecase.DeleteTrack
import vn.lutrinh.app.domain.usecase.DeleteTrip
import vn.lutrinh.app.domain.usecase.ExportGpx
import vn.lutrinh.app.domain.usecase.ImportGpx
import vn.lutrinh.app.domain.usecase.SaveStep
import vn.lutrinh.app.domain.usecase.SaveTrip
import vn.lutrinh.app.domain.usecase.PublishStep
import vn.lutrinh.app.domain.usecase.SetLegTransport
import vn.lutrinh.app.domain.usecase.SetTripCover
import vn.lutrinh.app.domain.usecase.TrackRecorder
import vn.lutrinh.app.map.NetworkMonitor
import vn.lutrinh.app.map.OfflineMaps
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
    val publishStep = PublishStep(steps, clock)
    val setTripCover = SetTripCover(trips, clock)
    val deleteTrack = DeleteTrack(tracks, recorder)

    /** Offline gazetteer of Vietnam for place search and auto names (R23, R24). */
    val places = PlaceIndex(context, appScope)

    /** On-device flags, e.g. whether the battery guide has been shown (R22). */
    val prefs = LocalPrefs(context)

    val network = NetworkMonitor(context)
    val offlineMaps = OfflineMaps(context, network)

    /** "Chia sẻ chuyến đi" (R18): summary image + page export. */
    val exporter = TripExporter(context, photos)

    /** GPX export/import of one trip (R25). */
    val exportGpx = ExportGpx(trips, steps, tracks, clock)
    val importGpx = ImportGpx(RoomTripImportRepository(db), clock, ids)

    /** Local backup/restore of all user data (R26). */
    val backup = BackupManager(context, db, photos.photoDir, BuildConfig.VERSION_NAME)
}

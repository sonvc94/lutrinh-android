package vn.lutrinh.app

import android.content.Context
import vn.lutrinh.app.data.PhotoStorage
import vn.lutrinh.app.data.RoomStepRepository
import vn.lutrinh.app.data.RoomTripRepository
import vn.lutrinh.app.data.local.AppDatabase
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.repository.IdGenerator
import vn.lutrinh.app.domain.usecase.DeleteStep
import vn.lutrinh.app.domain.usecase.DeleteTrip
import vn.lutrinh.app.domain.usecase.SaveStep
import vn.lutrinh.app.domain.usecase.SaveTrip
import vn.lutrinh.app.domain.usecase.SetLegTransport
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

    val saveTrip = SaveTrip(trips, steps, clock, ids)
    val deleteTrip = DeleteTrip(trips, photos)
    val saveStep = SaveStep(trips, steps, photos, clock, ids)
    val deleteStep = DeleteStep(steps, photos)
    val setLegTransport = SetLegTransport(steps, clock)
}

package vn.lutrinh.app.domain.repository

import kotlinx.coroutines.flow.Flow
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.model.TripSummary
import java.time.LocalDate

/**
 * Storage contracts. Today only a local implementation exists (Room); a server-backed
 * or syncing implementation can be added behind the same interfaces later.
 */
interface TripRepository {
    /** All trips, newest start date first (see docs/04-business-rules.md R1). */
    fun observeTripSummaries(): Flow<List<TripSummary>>
    fun observeTrip(id: String): Flow<Trip?>
    suspend fun getTrip(id: String): Trip?
    suspend fun upsertTrip(trip: Trip)

    /** Deletes the trip and, by cascade, its steps and photo rows. Returns the photo files that belonged to it. */
    suspend fun deleteTrip(id: String): List<String>
}

interface StepRepository {
    /** Steps of a trip in timeline order (R2), each with its ordered photos. */
    fun observeSteps(tripId: String): Flow<List<Step>>
    fun observeStep(id: String): Flow<Step?>
    suspend fun getStep(id: String): Step?
    suspend fun getSteps(tripId: String): List<Step>

    /** Earliest and latest step date of a trip, or null when the trip has no steps. */
    suspend fun stepDateRange(tripId: String): ClosedRange<LocalDate>?

    /**
     * Inserts or updates [step] and replaces its photo list with [Step.photos] atomically.
     * Returns the file names of photos that were removed from the step.
     */
    suspend fun saveStep(step: Step): List<String>

    /** Deletes the step and its photo rows. Returns the photo files that belonged to it. */
    suspend fun deleteStep(id: String): List<String>
}

/** Owns the photo files in app-private storage. */
interface PhotoFileStore {
    suspend fun delete(fileNames: Collection<String>)
}

/** Injected so tests are deterministic. */
interface Clock {
    fun nowMillis(): Long
    fun today(): LocalDate
}

fun interface IdGenerator {
    fun newId(): String
}

/** Convenience for building [Photo] lists in use cases. */
fun List<Photo>.renumbered(): List<Photo> = mapIndexed { i, p -> p.copy(position = i) }

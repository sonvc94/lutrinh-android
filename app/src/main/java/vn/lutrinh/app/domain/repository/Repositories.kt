package vn.lutrinh.app.domain.repository

import kotlinx.coroutines.flow.Flow
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.model.TransportMode
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

    /** Steps of every trip (grouped by trip, timeline order inside a trip), for "Bản đồ của tôi". */
    fun observeAllSteps(): Flow<List<Step>>

    /** Sets the transport of the leg ending at [stepId] (null = clear). False when the step does not exist. */
    suspend fun setArrivalTransport(stepId: String, mode: TransportMode?, now: Long): Boolean
}

/** Recorded GPS tracks and their points (R16). */
interface TrackRepository {
    /** Tracks of a trip, newest first. */
    fun observeTracks(tripId: String): Flow<List<Track>>

    /** Points of every track of a trip, grouped by track, in segment then time order. */
    fun observePoints(tripId: String): Flow<List<TrackPoint>>

    /** Points of every track of every trip, grouped by track ("Bản đồ của tôi"). */
    fun observeAllPoints(): Flow<List<TrackPoint>>

    suspend fun getTrack(id: String): Track?

    /** Tracks still RECORDING or PAUSED (at most one in normal use). */
    suspend fun openTracks(): List<Track>


    suspend fun insertTrack(track: Track)

    suspend fun updateTrack(track: Track)

    /** Stores [point] and the track's new totals in one transaction. */
    suspend fun addPoint(track: Track, point: TrackPoint)

    /** Deletes the track and all its points. False when it did not exist. */
    suspend fun deleteTrack(id: String): Boolean
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

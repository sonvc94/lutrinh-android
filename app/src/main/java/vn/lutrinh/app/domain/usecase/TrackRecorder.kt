package vn.lutrinh.app.domain.usecase

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.lutrinh.app.domain.model.LocationFix
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.repository.Clock
import vn.lutrinh.app.domain.repository.IdGenerator
import vn.lutrinh.app.domain.repository.TrackRepository
import vn.lutrinh.app.domain.repository.TripRepository

/**
 * The single recording of the app (R16): start → pause/resume → stop, fixes in, filtered points
 * saved at once. Platform-free: the Android foreground service (or the iOS location delegate)
 * only feeds [onFix] and follows [active]. Every change is written before [active] changes, so a
 * process death loses nothing that was shown.
 */
class TrackRecorder(
    private val tracks: TrackRepository,
    private val trips: TripRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val mutex = Mutex()
    private val _active = MutableStateFlow<Track?>(null)

    /** The open track (RECORDING or PAUSED) of this process, or null. */
    val active: StateFlow<Track?> = _active.asStateFlow()

    private val _stopped = MutableSharedFlow<StopOutcome>(extraBufferCapacity = 4)

    /** One event per [stop] (screen or notification), for a snackbar. Not replayed. */
    val stopped: SharedFlow<StopOutcome> = _stopped.asSharedFlow()

    private var last: TrackPoint? = null

    sealed interface StartResult {
        data class Started(val track: Track) : StartResult
        /** Only one recording at a time (one GPS); [track] is the one already open. */
        data class AlreadyRecording(val track: Track) : StartResult
        data object TripNotFound : StartResult
    }

    suspend fun start(tripId: String): StartResult = mutex.withLock {
        _active.value?.let { return StartResult.AlreadyRecording(it) }
        trips.getTrip(tripId) ?: return StartResult.TripNotFound
        val now = clock.nowMillis()
        val track = Track(
            id = ids.newId(), tripId = tripId, status = TrackStatus.RECORDING, startedAt = now, endedAt = null,
            activeMs = 0, resumedAt = now, segment = 0, distanceM = 0.0, pointCount = 0, interrupted = false,
            createdAt = now, updatedAt = now,
        )
        tracks.insertTrack(track)
        last = null
        _active.value = track
        StartResult.Started(track)
    }

    /** Closes the open segment; its time counts, the pause does not. */
    suspend fun pause(): Track? = mutex.withLock {
        val t = _active.value ?: return null
        if (t.status != TrackStatus.RECORDING) return t
        val now = clock.nowMillis()
        save(t.copy(status = TrackStatus.PAUSED, activeMs = t.durationMs(now), resumedAt = null, updatedAt = now))
    }

    /** Opens a new segment: no line and no distance join the point before the pause to the next one. */
    suspend fun resume(): Track? = mutex.withLock {
        val t = _active.value ?: return null
        if (t.status != TrackStatus.PAUSED) return t
        val now = clock.nowMillis()
        save(t.copy(status = TrackStatus.RECORDING, resumedAt = now, segment = t.segment + 1, updatedAt = now))
    }

    /**
     * Ends the open track and returns it, or null when nothing was recording. A track with fewer
     * than 2 points or under 50 m ([TrackRules.isWorthKeeping]) is deleted at once; the returned
     * track is then the one that was dropped. Either way [stopped] emits.
     */
    suspend fun stop(): Track? = mutex.withLock {
        val t = _active.value ?: return null
        val now = clock.nowMillis()
        val ended = t.copy(status = TrackStatus.ENDED, activeMs = t.durationMs(now), resumedAt = null, endedAt = now, updatedAt = now)
        val keep = TrackRules.isWorthKeeping(ended)
        runCatching { if (keep) tracks.updateTrack(ended) else tracks.deleteTrack(ended.id) }
        _active.value = null
        last = null
        _stopped.tryEmit(if (keep) StopOutcome.Saved(ended) else StopOutcome.Dropped(ended))
        ended
    }

    /** Stops the recording when it belongs to [tripId] (before the trip is deleted). */
    suspend fun stopIfTrip(tripId: String) {
        if (_active.value?.tripId == tripId) stop()
    }

    /** Filters [fix] (R16) and stores it when accepted. */
    suspend fun onFix(fix: LocationFix): FixVerdict = mutex.withLock {
        val t = _active.value ?: return FixVerdict.IGNORED
        if (t.status != TrackStatus.RECORDING) return FixVerdict.IGNORED
        val verdict = TrackFilter.check(fix, last, t.segment, t.resumedAt ?: t.startedAt)
        if (verdict != FixVerdict.ACCEPT) return verdict
        val prev = last
        val step = if (prev != null && prev.segment == t.segment) TrackMath.distanceM(prev.location, fix.location) else 0.0
        val point = TrackPoint(t.id, t.segment, fix.time, fix.location, fix.accuracyM, fix.altitudeM, fix.speedMps)
        val updated = t.copy(distanceM = t.distanceM + step, pointCount = t.pointCount + 1, updatedAt = clock.nowMillis())
        try {
            tracks.addPoint(updated, point)
        } catch (e: Exception) {
            // The track (or its trip) was deleted under us: the recording is over.
            if (tracks.getTrack(t.id) == null) {
                _active.value = null
                last = null
                return FixVerdict.IGNORED
            }
            throw e
        }
        last = point
        _active.value = updated
        verdict
    }

    /**
     * Run once when the process starts, before anything can record: an open track in storage
     * belongs to a process that died while recording. It is ended with the points already saved;
     * the open segment counts up to the last saved change ([Track.updatedAt]). Returns how many.
     */
    suspend fun recoverInterrupted(): Int = mutex.withLock {
        val open = tracks.openTracks().filter { it.id != _active.value?.id }
        open.forEach { t ->
            val endAt = maxOf(t.updatedAt, t.resumedAt ?: t.updatedAt)
            tracks.updateTrack(
                t.copy(
                    status = TrackStatus.ENDED, activeMs = t.durationMs(endAt), resumedAt = null, endedAt = endAt,
                    interrupted = true, updatedAt = clock.nowMillis(),
                ),
            )
        }
        open.size
    }

    private suspend fun save(t: Track): Track {
        tracks.updateTrack(t)
        _active.value = t
        return t
    }
}

package vn.lutrinh.app.data.local

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.MediaType
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.StopDecision
import vn.lutrinh.app.domain.model.StopDecisionKind
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.model.TripSummary

fun TripEntity.toDomain() = Trip(id, title, description, startDate, endDate, createdAt, updatedAt, coverPhotoId)

fun Trip.toEntity() = TripEntity(id, title, description, startDate, endDate, createdAt, updatedAt, coverPhotoId)

fun TripSummaryRow.toDomain() = TripSummary(trip.toDomain(), stepCount, photoCount, coverFile, draftCount, draftPhotoCount)

fun PhotoEntity.toDomain() =
    Photo(id, stepId, fileName, position, createdAt, takenAt, sourceId, sourceSize, MediaType.fromCode(mediaType), durationMs)

fun Photo.toEntity() = PhotoEntity(id, stepId, fileName, position, createdAt, takenAt, sourceId, sourceSize, mediaType.code, durationMs)

fun StepWithPhotos.toDomain() = Step(
    id = step.id,
    tripId = step.tripId,
    title = step.title,
    note = step.note,
    location = GeoPoint(step.latitude, step.longitude),
    arrivedAt = step.arrivedAt,
    createdAt = step.createdAt,
    updatedAt = step.updatedAt,
    // R27: the stored order (dense 0..n-1 since DB v7); created time and id only break ties of older data.
    photos = photos.sortedWith(compareBy<PhotoEntity> { it.position }.thenBy { it.createdAt }.thenBy { it.id }).map { it.toDomain() },
    arrivalTransport = TransportMode.fromCode(step.transportMode),
    isDraft = step.isDraft,
)

fun Step.toEntity() = StepEntity(
    id = id,
    tripId = tripId,
    title = title,
    note = note,
    latitude = location.latitude,
    longitude = location.longitude,
    arrivedAt = arrivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    transportMode = arrivalTransport?.code,
    isDraft = isDraft,
)

fun TrackEntity.toDomain() = Track(
    id, tripId, TrackStatus.fromCode(status), startedAt, endedAt, activeMs, resumedAt, segment, distanceM, pointCount,
    interrupted, createdAt, updatedAt,
)

fun Track.toEntity() = TrackEntity(
    id, tripId, status.code, startedAt, endedAt, activeMs, resumedAt, segment, distanceM, pointCount, interrupted,
    createdAt, updatedAt,
)

fun TrackPointEntity.toDomain() =
    TrackPoint(trackId, segment, recordedAt, GeoPoint(latitude, longitude), accuracyM, altitudeM, speedMps)

fun TrackPoint.toEntity() = TrackPointEntity(
    trackId = trackId, segment = segment, recordedAt = recordedAt, latitude = location.latitude,
    longitude = location.longitude, accuracyM = accuracyM, altitudeM = altitudeM, speedMps = speedMps,
)

fun StopDecisionEntity.toDomain() =
    StopDecision(trackId, startAt, endAt, StopDecisionKind.fromCode(decision), stepId, decidedAt)

fun StopDecision.toEntity() = StopDecisionEntity(trackId, startAt, endAt, kind.code, stepId, decidedAt)

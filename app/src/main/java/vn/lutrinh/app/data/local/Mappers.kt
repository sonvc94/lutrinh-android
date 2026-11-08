package vn.lutrinh.app.data.local

import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Photo
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.model.Trip
import vn.lutrinh.app.domain.model.TripSummary

fun TripEntity.toDomain() = Trip(id, title, description, startDate, endDate, createdAt, updatedAt)

fun Trip.toEntity() = TripEntity(id, title, description, startDate, endDate, createdAt, updatedAt)

fun TripSummaryRow.toDomain() = TripSummary(trip.toDomain(), stepCount, photoCount, coverFile)

fun PhotoEntity.toDomain() = Photo(id, stepId, fileName, position, createdAt)

fun Photo.toEntity() = PhotoEntity(id, stepId, fileName, position, createdAt)

fun StepWithPhotos.toDomain() = Step(
    id = step.id,
    tripId = step.tripId,
    title = step.title,
    note = step.note,
    location = GeoPoint(step.latitude, step.longitude),
    arrivedAt = step.arrivedAt,
    createdAt = step.createdAt,
    updatedAt = step.updatedAt,
    photos = photos.sortedWith(compareBy<PhotoEntity> { it.position }.thenBy { it.createdAt }).map { it.toDomain() },
    arrivalTransport = TransportMode.fromCode(step.transportMode),
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
)

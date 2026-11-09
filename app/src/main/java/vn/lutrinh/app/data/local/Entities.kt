package vn.lutrinh.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trip_id"), Index(value = ["trip_id", "arrived_at"])],
)
data class StepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    val title: String,
    val note: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "arrived_at") val arrivedAt: LocalDateTime,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** TransportMode.code of the leg ending here, or null (added in DB version 2). */
    @ColumnInfo(name = "transport_mode") val transportMode: String? = null,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = StepEntity::class,
            parentColumns = ["id"],
            childColumns = ["step_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("step_id"), Index(value = ["file_name"], unique = true)],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "step_id") val stepId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** EXIF capture time, "yyyy-MM-ddTHH:mm" local, or null (added in DB version 4, R17). */
    @ColumnInfo(name = "taken_at") val takenAt: LocalDateTime? = null,
    /** Gallery id of the original (MediaStore _ID), or null (added in DB version 5, R19). */
    @ColumnInfo(name = "source_id") val sourceId: String? = null,
    /** Byte size of the original file, or null (added in DB version 5, R19). */
    @ColumnInfo(name = "source_size") val sourceSize: Long? = null,
)

data class StepWithPhotos(
    @Embedded val step: StepEntity,
    @Relation(parentColumn = "id", entityColumn = "step_id")
    val photos: List<PhotoEntity>,
)

data class TripSummaryRow(
    @Embedded val trip: TripEntity,
    @ColumnInfo(name = "step_count") val stepCount: Int,
    @ColumnInfo(name = "photo_count") val photoCount: Int,
    @ColumnInfo(name = "cover_file") val coverFile: String?,
)

data class DateRangeRow(
    @ColumnInfo(name = "first_day") val firstDay: String?,
    @ColumnInfo(name = "last_day") val lastDay: String?,
)

/** A recorded GPS track of a trip (R16, DB version 3). Times are epoch milliseconds. */
@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trip_id")],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    /** TrackStatus.code */
    val status: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "active_ms") val activeMs: Long,
    @ColumnInfo(name = "resumed_at") val resumedAt: Long?,
    val segment: Int,
    @ColumnInfo(name = "distance_m") val distanceM: Double,
    @ColumnInfo(name = "point_count") val pointCount: Int,
    val interrupted: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A stored (filtered, unsimplified) GPS point of a track (DB version 3). */
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["track_id", "segment", "recorded_at"])],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_id") val trackId: String,
    val segment: Int,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "accuracy_m") val accuracyM: Float?,
    @ColumnInfo(name = "altitude_m") val altitudeM: Double?,
    @ColumnInfo(name = "speed_mps") val speedMps: Float?,
)

/**
 * The user's answer to a stop suggestion (R17, DB version 4). A suggestion itself is never
 * stored: it is recomputed from the track's points; a decision is matched to it by track and
 * overlapping time window. Goes with its track (cascade).
 */
@Entity(
    tableName = "stop_decisions",
    primaryKeys = ["track_id", "start_at"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("track_id")],
)
data class StopDecisionEntity(
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_at") val endAt: Long,
    /** StopDecisionKind.code */
    val decision: String,
    /** Step created from the suggestion ("added"); no foreign key: the step may be deleted later. */
    @ColumnInfo(name = "step_id") val stepId: String?,
    @ColumnInfo(name = "decided_at") val decidedAt: Long,
)

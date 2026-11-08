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

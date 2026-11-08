package vn.lutrinh.app.data.local

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Dates are stored as ISO-8601 text so that the SQL ordering equals the time ordering
 * and the same file layout can be read by an iOS (SQLite / SwiftData) port.
 *  - LocalDate      -> "yyyy-MM-dd"
 *  - LocalDateTime  -> "yyyy-MM-dd'T'HH:mm" (minute precision, no zone)
 */
class Converters {
    @TypeConverter
    fun dateToText(value: LocalDate?): String? = value?.format(DATE)

    @TypeConverter
    fun textToDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it, DATE) }

    @TypeConverter
    fun dateTimeToText(value: LocalDateTime?): String? = value?.format(DATE_TIME)

    @TypeConverter
    fun textToDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it, DATE_TIME) }

    companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    }
}

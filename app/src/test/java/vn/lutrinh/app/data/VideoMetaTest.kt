package vn.lutrinh.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** R29: a video's recording time (MediaMetadataRetriever METADATA_KEY_DATE, UTC) as a local minute. */
class VideoMetaTest {
    private val hanoi = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test fun utcRecordingTimeBecomesLocalWallClockMinute() {
        assertEquals(LocalDateTime.of(2025, 10, 20, 15, 30), VideoMeta.parseDate("20251020T083015.000Z", hanoi))
        assertEquals(LocalDateTime.of(2025, 10, 21, 6, 5), VideoMeta.parseDate("20251020T230559Z", hanoi))
    }

    @Test fun missingPlaceholderOrGarbageDatesAreUnknown() {
        assertNull(VideoMeta.parseDate(null, hanoi))
        assertNull(VideoMeta.parseDate("", hanoi))
        assertNull(VideoMeta.parseDate("19040101T000000.000Z", hanoi))
        assertNull(VideoMeta.parseDate("yesterday", hanoi))
    }
}

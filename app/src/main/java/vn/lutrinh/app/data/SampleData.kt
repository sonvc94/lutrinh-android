package vn.lutrinh.app.data

import java.time.LocalDate
import kotlin.math.sin
import kotlin.random.Random
import java.time.LocalDateTime
import java.time.ZoneId
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Track
import vn.lutrinh.app.domain.model.TrackPoint
import vn.lutrinh.app.domain.model.TrackStatus
import vn.lutrinh.app.domain.usecase.TrackMath
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.usecase.SaveResult
import vn.lutrinh.app.domain.usecase.StepInput
import vn.lutrinh.app.domain.usecase.TripInput

/**
 * Debug-only sample content (menu "Tạo dữ liệu mẫu" in debug builds) so screenshots show
 * real places. Photos are real pictures of those places from Wikimedia Commons, in the debug
 * assets (`app/src/debug/assets/sample-photos/`, source + author + licence per file in its
 * README): `<photo key>-<n>.jpg`. No generated pictures (docs/08-design.md "Imagery"); a build
 * without the assets (release) seeds the steps without photos.
 */
object SampleData {
    /** [key] = file prefix of the step's sample photos in assets/sample-photos ([photos] files). */
    /** [by] = transport of the leg that ends at this step (R12); null on the first step. */
    /** [photoMinutes] = capture time of each photo, minutes after [at] (R17 photos.takenAt); default 15 min apart. */
    private data class S(
        val title: String, val lat: Double, val lng: Double, val at: String, val note: String, val photos: Int,
        val by: TransportMode? = null, val photoMinutes: List<Int>? = null, val key: String = "",
    )

    suspend fun seed(c: AppContainer) {
        val year = LocalDate.now().year - 1
        val haGiang = createTrip(
            c,
            TripInput(
                "Vòng cung Hà Giang", "4 ngày xe máy qua cao nguyên đá Đồng Văn, mùa hoa tam giác mạch.",
                LocalDate.of(year, 10, 20), LocalDate.of(year, 10, 23),
            ),
            listOf(
                // Photo times line up with the sample track's stays (R17): one at Bắc Sum, one at Tam Sơn.
                S("TP Hà Giang", 22.8233, 104.9836, "$year-10-20T08:00", "Thuê xe máy, nhận giấy phép, ăn phở chua.", 1, photoMinutes = listOf(80), key = "ha-giang-city"),
                S(
                    "Cổng Trời Quản Bạ", 23.0688, 104.9930, "$year-10-20T11:30", "Núi đôi Cô Tiên nhìn từ trên đèo.", 2,
                    TransportMode.MOTORBIKE, photoMinutes = listOf(5, 55), key = "quan-ba",
                ),
                S("Yên Minh", 23.1203, 105.1406, "$year-10-20T16:00", "Rừng thông Yên Minh, nghỉ đêm ở homestay.", 0, TransportMode.MOTORBIKE),
                S("Cột cờ Lũng Cú", 23.3638, 105.3164, "$year-10-21T09:00", "Điểm cực Bắc, leo 389 bậc.", 2, TransportMode.MOTORBIKE, key = "lung-cu"),
                S("Làng Lô Lô Chải", 23.3557, 105.3266, "$year-10-21T11:00", "Đi bộ xuống làng, nhà trình tường vàng.", 1, TransportMode.WALK, key = "lo-lo-chai"),
                S("Phố cổ Đồng Văn", 23.2786, 105.3621, "$year-10-21T17:00", "Cà phê phố cổ, chợ phiên sáng Chủ nhật.", 1, TransportMode.MOTORBIKE, key = "dong-van"),
                S("Đèo Mã Pí Lèng", 23.2397, 105.4096, "$year-10-22T08:30", "Sông Nho Quế xanh ngọc dưới vực.", 3, TransportMode.MOTORBIKE, key = "ma-pi-leng"),
                S("Hẻm Tu Sản", 23.2128, 105.4366, "$year-10-22T10:30", "Đi thuyền trên sông Nho Quế qua hẻm vực.", 1, TransportMode.BOAT, key = "tu-san"),
                S("Mèo Vạc", 23.1597, 105.4086, "$year-10-22T14:00", "", 0, TransportMode.CAR),
                S("Du Già", 22.9333, 105.1667, "$year-10-23T10:00", "Thác Du Già, quay về TP Hà Giang chiều tối.", 1, TransportMode.MOTORBIKE, key = "du-gia"),
            ),
        )
        haGiang?.let { addHaGiangTrack(c, it, LocalDate.of(year, 10, 20)) }
        createTrip(
            c,
            TripInput("Ninh Bình thơ mộng", "Cuối tuần chèo đò Tràng An, leo Hang Múa.", LocalDate.of(year, 6, 7), LocalDate.of(year, 6, 8)),
            listOf(
                S("Cố đô Hoa Lư", 20.2862, 105.9063, "$year-06-07T09:00", "Đền vua Đinh, vua Lê.", 1, key = "hoa-lu"),
                // On the Tràng An road by the wharf approach: the park's own centre (20.2546, 105.8954) is
                // 714 m from any road, so both legs would fail the off-road guard and stay straight (R36).
                S("Tràng An", 20.2560, 105.9150, "$year-06-07T13:00", "Chèo đò 3 tiếng qua 9 hang.", 2, TransportMode.CAR, key = "trang-an"),
                S("Hang Múa", 20.2299, 105.9327, "$year-06-08T06:00", "Ngắm bình minh trên đỉnh, 500 bậc đá.", 1, TransportMode.MOTORBIKE, key = "hang-mua"),
                S("Tam Cốc", 20.2163, 105.9370, "$year-06-08T10:00", "Lúa chín vàng hai bên sông Ngô Đồng.", 0, TransportMode.WALK),
            ),
        )
    }

    private suspend fun createTrip(c: AppContainer, trip: TripInput, steps: List<S>): String? {
        val tripId = (c.saveTrip(null, trip) as? SaveResult.Saved)?.id ?: return null
        steps.forEach { s ->
            val photos = (1..s.photos).mapNotNull { n -> samplePhoto(c, "${s.key}-$n") }.map { c.photos.write(it) }
            val arrival = LocalDateTime.parse(s.at)
            val takenAt = photos.mapIndexed { k, f -> f to arrival.plusMinutes((s.photoMinutes?.getOrNull(k) ?: (k * 15)).toLong()) }.toMap()
            val saved = c.saveStep(
                tripId,
                null,
                StepInput(s.title, s.note, GeoPoint(s.lat, s.lng), arrival, photos, takenAt),
            )
            if (saved is SaveResult.Saved && s.by != null) c.setLegTransport(saved.id, s.by)
        }
        return tripId
    }

    /** Bytes of a debug asset photo, or null when the build has none (release). */
    private fun samplePhoto(c: AppContainer, name: String): ByteArray? =
        runCatching { c.assets.open("sample-photos/$name.jpg").use { it.readBytes() } }.getOrNull()

    /** A place the sample track goes to; it stays [stayMin] minutes ([pause] = recording paused meanwhile). */
    private data class W(val lat: Double, val lng: Double, val at: String, val stayMin: Int = 0, val pause: Boolean = false)

    /**
     * Adds a finished GPS track for day [day] of a trip: TP Hà Giang → Quản Bạ → Yên Minh by
     * motorbike with three stays (R17): Dốc Bắc Sum 35 min (no step → suggestion), Cổng Trời
     * Quản Bạ 30 min (the sample step is there → no suggestion), lunch in Tam Sơn 55 min with the
     * recording paused (pause gap → suggestion). Straight-ish lines between places, not real roads.
     * Also used by the UI test (TripFlowsTest) on a trip of its own.
     */
    suspend fun addHaGiangTrack(c: AppContainer, tripId: String, day: LocalDate) {
        val d = day.toString()
        val route = listOf(
            W(22.8233, 104.9836, "${d}T08:10"),
            W(22.9104, 104.9781, "${d}T09:05", stayMin = 35),
            W(23.0686, 104.9927, "${d}T11:20", stayMin = 30),
            W(23.0577, 104.9946, "${d}T12:05", stayMin = 55, pause = true),
            W(23.1203, 105.1406, "${d}T15:50"),
        )
        val zone = ZoneId.systemDefault()
        fun ms(at: LocalDateTime) = at.atZone(zone).toInstant().toEpochMilli()
        val rnd = Random(20)
        val points = mutableListOf<TrackPoint>()
        val trackId = c.ids.newId()
        var segment = 0
        fun add(lat: Double, lng: Double, t: Long) {
            points += TrackPoint(trackId, segment, t, GeoPoint(lat, lng), accuracyM = 8f)
        }
        route.forEachIndexed { i, w ->
            val arrive = ms(LocalDateTime.parse(w.at))
            if (i > 0) {
                // Moving: one point a minute from the previous place's departure, gently winding.
                val prev = route[i - 1]
                val from = ms(LocalDateTime.parse(prev.at).plusMinutes(prev.stayMin.toLong()))
                val n = ((arrive - from) / 60_000).toInt().coerceAtLeast(1)
                for (k in 1..n) {
                    val f = k.toDouble() / n
                    val wiggle = sin(f * Math.PI * 6) * 0.004 * sin(f * Math.PI)
                    add(prev.lat + (w.lat - prev.lat) * f + wiggle, prev.lng + (w.lng - prev.lng) * f - wiggle, from + (arrive - from) * k / n)
                }
            } else {
                add(w.lat, w.lng, arrive)
            }
            if (w.stayMin > 0) {
                val leave = arrive + w.stayMin * 60_000L
                if (w.pause) {
                    // Paused for lunch: no points; the next segment starts at the same place.
                    segment++
                    add(w.lat + 0.0002, w.lng, leave)
                } else {
                    // Standing around: a fix every ~4 min, within ~40 m (each ≥ 10 m from the last).
                    var t = arrive + 240_000L
                    while (t < leave) {
                        add(w.lat + (rnd.nextDouble() - 0.5) * 0.0007, w.lng + (rnd.nextDouble() - 0.5) * 0.0007, t)
                        t += 240_000L
                    }
                    add(w.lat, w.lng, leave)
                }
            }
        }
        val start = points.first().recordedAt
        val end = points.last().recordedAt
        val pausedMs = route.filter { it.pause }.sumOf { it.stayMin * 60_000L }
        val track = Track(
            id = trackId, tripId = tripId, status = TrackStatus.ENDED, startedAt = start, endedAt = end,
            activeMs = end - start - pausedMs, resumedAt = null, segment = segment, distanceM = TrackMath.lengthM(points),
            pointCount = points.size, interrupted = false, createdAt = end, updatedAt = end,
        )
        c.tracks.insertTrack(track)
        points.forEach { c.tracks.addPoint(track, it) }
    }
}

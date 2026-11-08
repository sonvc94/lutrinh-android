package vn.lutrinh.app.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.usecase.SaveResult
import vn.lutrinh.app.domain.usecase.StepInput
import vn.lutrinh.app.domain.usecase.TripInput

/**
 * Debug-only sample content (menu "Tạo dữ liệu mẫu" in debug builds) so screenshots show
 * real places. Photos are generated landscape drawings, not real pictures.
 */
object SampleData {
    private data class S(val title: String, val lat: Double, val lng: Double, val at: String, val note: String, val photos: Int)

    suspend fun seed(c: AppContainer) {
        val year = LocalDate.now().year - 1
        createTrip(
            c,
            TripInput(
                "Vòng cung Hà Giang", "4 ngày xe máy qua cao nguyên đá Đồng Văn, mùa hoa tam giác mạch.",
                LocalDate.of(year, 10, 20), LocalDate.of(year, 10, 23),
            ),
            listOf(
                S("TP Hà Giang", 22.8233, 104.9836, "$year-10-20T08:00", "Thuê xe máy, nhận giấy phép, ăn phở chua.", 1),
                S("Cổng Trời Quản Bạ", 23.0688, 104.9930, "$year-10-20T11:30", "Núi đôi Cô Tiên nhìn từ trên đèo.", 2),
                S("Yên Minh", 23.1203, 105.1406, "$year-10-20T16:00", "Rừng thông Yên Minh, nghỉ đêm ở homestay.", 0),
                S("Cột cờ Lũng Cú", 23.3638, 105.3164, "$year-10-21T09:00", "Điểm cực Bắc, leo 389 bậc.", 2),
                S("Phố cổ Đồng Văn", 23.2786, 105.3621, "$year-10-21T17:00", "Cà phê phố cổ, chợ phiên sáng Chủ nhật.", 1),
                S("Đèo Mã Pí Lèng", 23.2397, 105.4096, "$year-10-22T08:30", "Sông Nho Quế xanh ngọc dưới vực.", 3),
                S("Mèo Vạc", 23.1597, 105.4086, "$year-10-22T12:00", "", 0),
                S("Du Già", 22.9333, 105.1667, "$year-10-23T10:00", "Thác Du Già, quay về TP Hà Giang chiều tối.", 1),
            ),
        )
        createTrip(
            c,
            TripInput("Ninh Bình thơ mộng", "Cuối tuần chèo đò Tràng An, leo Hang Múa.", LocalDate.of(year, 6, 7), LocalDate.of(year, 6, 8)),
            listOf(
                S("Cố đô Hoa Lư", 20.2862, 105.9063, "$year-06-07T09:00", "Đền vua Đinh, vua Lê.", 1),
                S("Tràng An", 20.2546, 105.8954, "$year-06-07T13:00", "Chèo đò 3 tiếng qua 9 hang.", 2),
                S("Hang Múa", 20.2299, 105.9327, "$year-06-08T06:00", "Ngắm bình minh trên đỉnh, 500 bậc đá.", 1),
                S("Tam Cốc", 20.2163, 105.9370, "$year-06-08T10:00", "Lúa chín vàng hai bên sông Ngô Đồng.", 0),
            ),
        )
    }

    private suspend fun createTrip(c: AppContainer, trip: TripInput, steps: List<S>) {
        val tripId = (c.saveTrip(null, trip) as? SaveResult.Saved)?.id ?: return
        steps.forEachIndexed { i, s ->
            val photos = (0 until s.photos).map { k -> c.photos.write(landscape(seed = s.title.hashCode() + k * 31 + i)) }
            c.saveStep(
                tripId,
                null,
                StepInput(s.title, s.note, GeoPoint(s.lat, s.lng), LocalDateTime.parse(s.at), photos),
            )
        }
    }

    /** Simple generated landscape: sky gradient, sun, three layers of karst hills. */
    private fun landscape(seed: Int, w: Int = 1200, h: Int = 900): ByteArray {
        val rnd = Random(seed)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val skies = listOf(0xFF9FD3E8 to 0xFFFCE7C4, 0xFFF7B267 to 0xFFFDE2C8, 0xFF8EC5D6 to 0xFFE8F4EA)
        val (top, bottom) = skies[rnd.nextInt(skies.size)]
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top.toInt(), bottom.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = 0xFFFFF1B8.toInt()
        c.drawCircle(w * (0.2f + rnd.nextFloat() * 0.6f), h * 0.25f, 60f, p)
        val greens = listOf(0xFF9DBFA8, 0xFF5E8F6E, 0xFF1F5C45)
        greens.forEachIndexed { layer, color ->
            p.color = color.toInt()
            val base = h * (0.55f + layer * 0.15f)
            val path = Path().apply {
                moveTo(0f, h.toFloat())
                lineTo(0f, base)
                var x = 0f
                while (x < w) {
                    val peak = base - (80 + rnd.nextInt(200)) / (layer + 1f)
                    val width = 120f + rnd.nextInt(160)
                    quadTo(x + width / 2, peak - 60, x + width, base - rnd.nextInt(40))
                    x += width
                }
                lineTo(w.toFloat(), h.toFloat())
                close()
            }
            c.drawPath(path, p)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bmp.recycle()
        return out.toByteArray()
    }
}

package vn.lutrinh.app.routing.valhalla

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.routing.RouteProfile
import vn.lutrinh.app.domain.routing.RouteResult
import vn.lutrinh.app.domain.routing.RoutingCredit
import vn.lutrinh.app.domain.routing.RoutingProvider
import vn.lutrinh.app.domain.usecase.Polyline
import vn.lutrinh.app.routing.HttpAnswer
import vn.lutrinh.app.routing.RouterHttp

/**
 * Valhalla adapter (R36): the only code that knows Valhalla's `/route` JSON and its costing names.
 * Xe máy → `motor_scooter`, the only Valhalla profile that keeps off Vietnamese expressways
 * (motorbikes are not allowed there; `auto` and `motorcycle` use CT.01).
 */
class ValhallaRouter(private val baseUrl: String, private val http: RouterHttp) : RoutingProvider {
    override val id = ID
    override val credit = CREDIT

    override fun supports(profile: RouteProfile) = true

    override suspend fun route(from: GeoPoint, to: GeoPoint, profile: RouteProfile): RouteResult = try {
        parse(http.postJson("$baseUrl/route", request(from, to, profile)))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RouteResult.Unavailable(e.message ?: e.javaClass.simpleName)
    }

    companion object {
        const val ID = "valhalla"
        val CREDIT = RoutingCredit("Đường đi: Valhalla · FOSSGIS · © OpenStreetMap (ODbL)", "https://www.openstreetmap.org/fixthemap")

        /** The app's profile in Valhalla's words. */
        fun costing(profile: RouteProfile): String = when (profile) {
            RouteProfile.MOTORBIKE -> "motor_scooter"
            RouteProfile.CAR -> "auto"
            RouteProfile.BUS -> "bus"
            RouteProfile.WALK -> "pedestrian"
        }

        /** Request body: two locations, the costing, km units, no turn-by-turn text. */
        fun request(from: GeoPoint, to: GeoPoint, profile: RouteProfile): String = JSONObject()
            .put(
                "locations",
                JSONArray()
                    .put(JSONObject().put("lat", from.latitude).put("lon", from.longitude))
                    .put(JSONObject().put("lat", to.latitude).put("lon", to.longitude)),
            )
            .put("costing", costing(profile))
            .put("directions_type", "none")
            .put("units", "kilometers")
            .toString()

        /**
         * `trip.summary.length` (km) and `.time` (s), the line from `trip.legs[*].shape`
         * (polyline6). HTTP 400 = Valhalla could not route these places (e.g. error 442 "No path
         * could be found", 171 "No suitable edges near location"): no route. 429 = rate limited.
         */
        fun parse(answer: HttpAnswer): RouteResult {
            if (answer.code == 429) return RouteResult.Unavailable("HTTP 429", rateLimited = true)
            if (answer.code == 400) return RouteResult.NoRoute
            if (answer.code != 200) return RouteResult.Unavailable("HTTP ${answer.code}")
            val trip = JSONObject(answer.body).optJSONObject("trip") ?: return RouteResult.Unavailable("no trip")
            val summary = trip.getJSONObject("summary")
            val legs = trip.getJSONArray("legs")
            val path = ArrayList<GeoPoint>()
            for (i in 0 until legs.length()) {
                val pts = Polyline.decode6(legs.getJSONObject(i).getString("shape"))
                path += if (path.isEmpty()) pts else pts.drop(1)
            }
            if (path.size < 2) return RouteResult.NoRoute
            return RouteResult.Found(summary.getDouble("length") * 1000, summary.getDouble("time"), path)
        }
    }
}

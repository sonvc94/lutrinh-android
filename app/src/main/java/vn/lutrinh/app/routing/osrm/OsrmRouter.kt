package vn.lutrinh.app.routing.osrm

import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.routing.RouteProfile
import vn.lutrinh.app.domain.routing.RouteResult
import vn.lutrinh.app.domain.routing.RoutingCredit
import vn.lutrinh.app.domain.routing.RoutingProvider
import vn.lutrinh.app.domain.usecase.Polyline
import vn.lutrinh.app.routing.HttpAnswer
import vn.lutrinh.app.routing.RouterHttp
import java.util.Locale

/**
 * OSRM adapter (R36): the only code that knows OSRM's `route/v1` URL scheme and JSON. Car only:
 * OSRM has no motorbike profile, and its car route uses expressways motorbikes may not.
 */
class OsrmRouter(private val baseUrl: String, private val http: RouterHttp) : RoutingProvider {
    override val id = ID
    override val credit = CREDIT

    override fun supports(profile: RouteProfile) = profile == RouteProfile.CAR

    override suspend fun route(from: GeoPoint, to: GeoPoint, profile: RouteProfile): RouteResult {
        if (!supports(profile)) return RouteResult.Unavailable("profile ${profile.id}")
        return try {
            parse(http.get(url(baseUrl, from, to)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RouteResult.Unavailable(e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        const val ID = "osrm"
        val CREDIT = RoutingCredit("Đường đi: OSRM · FOSSGIS · © OpenStreetMap (ODbL)", "https://www.openstreetmap.org/fixthemap")

        /** `{base}/route/v1/driving/lng,lat;lng,lat` with the full line as polyline6. */
        fun url(baseUrl: String, from: GeoPoint, to: GeoPoint): String =
            String.format(
                Locale.US, "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=polyline6",
                baseUrl, from.longitude, from.latitude, to.longitude, to.latitude,
            )

        /** `routes[0].distance` (m), `.duration` (s), `.geometry`; code NoRoute/NoSegment = no route. */
        fun parse(answer: HttpAnswer): RouteResult {
            if (answer.code == 429) return RouteResult.Unavailable("HTTP 429", rateLimited = true)
            val json = try { JSONObject(answer.body) } catch (e: Exception) { null }
            val code = json?.optString("code").orEmpty()
            if (code == "NoRoute" || code == "NoSegment") return RouteResult.NoRoute
            if (answer.code != 200 || code != "Ok" || json == null) return RouteResult.Unavailable("HTTP ${answer.code} $code")
            val route = json.getJSONArray("routes").optJSONObject(0) ?: return RouteResult.NoRoute
            val path = Polyline.decode6(route.getString("geometry"))
            if (path.size < 2) return RouteResult.NoRoute
            return RouteResult.Found(route.getDouble("distance"), route.getDouble("duration"), path)
        }
    }
}

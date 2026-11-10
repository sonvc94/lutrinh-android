package vn.lutrinh.app.catalog

import vn.lutrinh.app.BuildConfig
import vn.lutrinh.app.catalog.server.LuTrinhServerCatalog
import vn.lutrinh.app.domain.catalog.RouteCatalog

/**
 * The route catalogs of the app (R40, docs/05 "Route catalog"): the ONLY place the route server's
 * address and User-Agent live. The address is `lutrinh.routeServerUrl` (local.properties or
 * `-P`) baked into BuildConfig: debug builds default to `http://10.0.2.2:8000` (the Mac's
 * localhost seen from the emulator; cleartext only to 10.0.2.2/localhost by the debug network
 * security config), release builds accept https only and are empty (= feature off) otherwise.
 * Nothing outside `catalog/` names the server (CatalogBoundaryTest).
 */
object Catalogs {
    val SERVER_URL: String = BuildConfig.ROUTE_SERVER_URL

    /** Our User-Agent, as the API asks (`LuTrinh/<version> (Android)`). */
    val USER_AGENT = "LuTrinh/${BuildConfig.VERSION_NAME} (Android)"

    /** False when the build has no server address: the "Nhập mã lộ trình" entry is hidden. */
    val serverConfigured: Boolean get() = SERVER_URL.isNotBlank()

    val server: RouteCatalog by lazy { LuTrinhServerCatalog(SERVER_URL, USER_AGENT) }
}

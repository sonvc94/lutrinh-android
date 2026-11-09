package vn.lutrinh.app.map.basemap

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the chosen basemap id is kept (`map_style` in the on-device prefs, R32). */
interface MapStyleStore {
    var mapStyle: String?
}

/**
 * The user's basemap choice (R32): one for every map of the app, kept across restarts. Unknown
 * stored ids (a removed provider) read as the default. What a map *shows* also depends on the
 * network: [BasemapRegistry.shown] (R33).
 */
class BasemapSettings(private val store: MapStyleStore, val registry: BasemapRegistry) {
    private val _chosen = MutableStateFlow(registry.byId(store.mapStyle))
    val chosen: StateFlow<BasemapProvider> = _chosen.asStateFlow()

    fun choose(id: String) {
        val provider = registry.byId(id)
        store.mapStyle = provider.id
        _chosen.value = provider
    }
}

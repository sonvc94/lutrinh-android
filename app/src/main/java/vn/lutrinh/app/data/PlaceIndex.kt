package vn.lutrinh.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.lutrinh.app.domain.usecase.Gazetteer

/**
 * The bundled offline gazetteer (R23/R24), read once from assets in the background at start-up.
 * Null until loaded (screens then behave as before: no search results, no auto name).
 */
class PlaceIndex(private val context: Context, scope: CoroutineScope) {
    private val _gazetteer = MutableStateFlow<Gazetteer?>(null)
    val gazetteer: StateFlow<Gazetteer?> = _gazetteer.asStateFlow()
    private val ready = CompletableDeferred<Gazetteer?>()

    /** Waits for the load; null when the asset could not be read. */
    suspend fun await(): Gazetteer? = ready.await()

    init {
        scope.launch(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            val g = runCatching { context.assets.open(ASSET).bufferedReader().useLines { Gazetteer.parse(it) } }
                .onFailure { Log.e(TAG, "cannot read $ASSET", it) }
                .getOrNull()
            ready.complete(g)
            if (g == null) return@launch
            Log.i(TAG, "${g.places.size} places in ${System.currentTimeMillis() - started} ms")
            _gazetteer.value = g
        }
    }

    companion object {
        const val ASSET = "places/vn_places.tsv"
        private const val TAG = "PlaceIndex"
    }
}

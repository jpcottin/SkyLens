package com.jpcottin.skylens.data

import com.jpcottin.skylens.astro.Tle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Celestrak element-set groups the app can show. */
enum class SatelliteGroup(val celestrakGroup: String, val displayName: String) {
    VISUAL("visual", "Brightest satellites"),
    STATIONS("stations", "Space stations"),
    STARLINK("starlink", "Starlink (large)"),
}

/** Where the current element sets came from. */
enum class TleSource { NONE, BUNDLED, CACHE, NETWORK }

data class TleState(
    val tles: List<Tle> = emptyList(),
    val source: TleSource = TleSource.NONE,
    /** Epoch millis the newest set was fetched (network) or written (cache); null for bundled data. */
    val fetchedAtMillis: Long? = null,
    val refreshing: Boolean = false,
    /** Last network failure, kept while the cached or bundled data continues to serve. */
    val error: String? = null,
)

/**
 * Element sets from Celestrak with a disk cache and a bundled fallback. Celestrak asks clients
 * not to fetch a group more than once every two hours, so a cache younger than [minRefreshMillis]
 * is never refreshed and a failed fetch keeps whatever was there.
 */
class TleRepository(
    private val cacheDir: File,
    private val bundled: (SatelliteGroup) -> String?,
    private val client: OkHttpClient = OkHttpClient(),
    private val clock: Clock = Clock.SYSTEM,
    private val baseUrl: String = "https://celestrak.org/NORAD/elements/gp.php",
    private val minRefreshMillis: Long = 2 * 60 * 60 * 1000L,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow(TleState())
    val state: StateFlow<TleState> = _state.asStateFlow()

    /** Loads the cache (or the bundled copy) for the groups without touching the network. */
    suspend fun loadLocal(groups: Set<SatelliteGroup>) = withContext(ioDispatcher) {
        var source = TleSource.NONE
        var newest: Long? = null
        val all = LinkedHashMap<String, Tle>()
        for (group in groups) {
            val file = cacheFile(group)
            val text = if (file.exists() && file.length() > 0) {
                source = maxOf(source, TleSource.CACHE)
                newest = maxOf(newest ?: 0L, file.lastModified())
                file.readText()
            } else {
                bundled(group)?.also { source = maxOf(source, TleSource.BUNDLED) }
            }
            text?.let { Tle.parseAll(it).forEach { tle -> all.putIfAbsent(tle.catalogNumber, tle) } }
        }
        _state.update { it.copy(tles = all.values.toList(), source = source, fetchedAtMillis = newest) }
    }

    /** Refreshes groups whose cache is older than the Celestrak courtesy interval, or all with [force]. */
    suspend fun refresh(groups: Set<SatelliteGroup>, force: Boolean = false) = withContext(ioDispatcher) {
        val now = clock.nowMillis()
        val stale = groups.filter { force || !cacheFile(it).exists() || now - cacheFile(it).lastModified() > minRefreshMillis }
        if (stale.isEmpty()) {
            loadLocal(groups)
            return@withContext
        }
        _state.update { it.copy(refreshing = true, error = null) }
        var error: String? = null
        try {
            for (group in stale) {
                try {
                    val text = fetch(group)
                    val parsed = Tle.parseAll(text)
                    if (parsed.isEmpty()) throw IOException("No element sets in the response for ${group.celestrakGroup}")
                    cacheFile(group).writeText(text)
                    cacheFile(group).setLastModified(now)
                } catch (e: IOException) {
                    error = e.message ?: e.javaClass.simpleName
                }
            }
            loadLocal(groups)
            _state.update { it.copy(error = error, source = if (error == null) TleSource.NETWORK else it.source) }
        } finally {
            // Also on cancellation (the group setting changed mid-fetch): a flag left on
            // would keep the Refresh button disabled and "Refreshing" shown for good.
            _state.update { it.copy(refreshing = false) }
        }
    }

    private fun fetch(group: SatelliteGroup): String {
        val request = Request.Builder().url("$baseUrl?GROUP=${group.celestrakGroup}&FORMAT=tle").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${group.celestrakGroup}")
            return response.body.string()
        }
    }

    fun cacheFile(group: SatelliteGroup): File = File(cacheDir, "tle-${group.celestrakGroup}.txt")
}

package com.jpcottin.skylens.domain

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.jpcottin.skylens.astro.StarCatalog
import com.jpcottin.skylens.data.CameraInfo
import com.jpcottin.skylens.data.Clock
import com.jpcottin.skylens.data.LocationSource
import com.jpcottin.skylens.data.OrientationSource
import com.jpcottin.skylens.data.SatelliteGroup
import com.jpcottin.skylens.data.SettingsRepository
import com.jpcottin.skylens.data.TleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Hand-wired dependency container; one per process, created by [com.jpcottin.skylens.SkyLensApplication]. */
class AppContainer(private val context: Context) {
    val isDebuggable: Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    val settings: SettingsRepository by lazy {
        SettingsRepository(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") })
    }

    val tles: TleRepository by lazy {
        TleRepository(
            cacheDir = File(context.filesDir, "tle").also { it.mkdirs() },
            bundled = { group -> readAsset("tle/${group.celestrakGroup}.tle") },
        )
    }

    val location: LocationSource by lazy { LocationSource(context) }
    val orientation: OrientationSource by lazy { OrientationSource(context) }

    val cameraFov: CameraInfo.SensorFov? by lazy { CameraInfo.backCameraFov(context) }

    private var catalog: StarCatalog? = null

    suspend fun starCatalog(): StarCatalog = catalog ?: withContext(Dispatchers.IO) {
        StarCatalog.loadBundled().also { catalog = it }
    }

    private fun readAsset(path: String): String? = try {
        context.assets.open(path).bufferedReader().use { it.readText() }
    } catch (e: java.io.IOException) {
        null
    }

    companion object {
        val defaultGroups = setOf(SatelliteGroup.VISUAL, SatelliteGroup.STATIONS)
    }
}

/** Dependency source for [Clock]: the frozen debug clock when set, else the system clock. */
fun DebugOverrides.clock(): Clock = timeMillis?.let(Clock::frozen) ?: Clock.SYSTEM

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
import android.os.Build
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import com.jpcottin.skylens.astro.Observer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

    /**
     * The object the user asked to be guided to, shared between the phone and the glasses
     * surfaces so "Point me" on the phone lights up the same target on the lens.
     */
    val sharedTarget = MutableStateFlow<String?>(null)

    /**
     * The latest observer any screen has seen, for callbacks that outlive a screen (the sensor
     * flow's declination). Kept here rather than in an activity: the view model factory runs
     * once, and a lambda reading an activity's field would keep the first instance alive and
     * read its stale value after a recreation.
     */
    val lastObserver = MutableStateFlow<Observer?>(null)

    /** Whether Display AI Glasses are connected to this phone (always false before Android 16). */
    @OptIn(ExperimentalProjectedApi::class)
    fun glassesConnected(): Flow<Boolean> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            runCatching { ProjectedContext.isProjectedDeviceConnected(context, Dispatchers.Main) }.getOrElse { flowOf(false) }
        } else {
            flowOf(false)
        }

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

package com.jpcottin.skylens.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jpcottin.skylens.astro.Observer
import com.jpcottin.skylens.astro.SkyLayers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Everything the user can change, persisted in Preferences DataStore. */
data class Settings(
    val showSun: Boolean = true,
    val showMoon: Boolean = true,
    val showPlanets: Boolean = true,
    val showStars: Boolean = true,
    val showSatellites: Boolean = true,
    val starMagnitudeLimit: Double = 4.5,
    val showLabels: Boolean = true,
    val showTrajectories: Boolean = true,
    /** Satellite trajectory span to draw, in seconds. */
    val trajectorySeconds: Int = 300,
    val satelliteGroups: Set<SatelliteGroup> = setOf(SatelliteGroup.VISUAL, SatelliteGroup.STATIONS),
    /** Red-on-black rendering that preserves night vision. */
    val nightMode: Boolean = false,
    /** Camera passthrough behind the overlay (off = drawn sky). */
    val cameraPassthrough: Boolean = true,
    /** Manual observer position, used when location is unavailable or the user prefers it. */
    val manualLocation: Observer? = null,
    val useManualLocation: Boolean = false,
    /** Minimum culmination elevation for a pass to be listed. */
    val minPassElevationDeg: Double = 10.0,
) {
    fun toLayers() = SkyLayers(
        sun = showSun, moon = showMoon, planets = showPlanets, stars = showStars, satellites = showSatellites,
        starMagnitudeLimit = starMagnitudeLimit,
    )
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val showSun = booleanPreferencesKey("show_sun")
        val showMoon = booleanPreferencesKey("show_moon")
        val showPlanets = booleanPreferencesKey("show_planets")
        val showStars = booleanPreferencesKey("show_stars")
        val showSatellites = booleanPreferencesKey("show_satellites")
        val starMagnitudeLimit = doublePreferencesKey("star_magnitude_limit")
        val showLabels = booleanPreferencesKey("show_labels")
        val showTrajectories = booleanPreferencesKey("show_trajectories")
        val trajectorySeconds = intPreferencesKey("trajectory_seconds")
        val satelliteGroups = stringSetPreferencesKey("satellite_groups")
        val nightMode = booleanPreferencesKey("night_mode")
        val cameraPassthrough = booleanPreferencesKey("camera_passthrough")
        val manualLat = doublePreferencesKey("manual_lat")
        val manualLon = doublePreferencesKey("manual_lon")
        val manualAlt = doublePreferencesKey("manual_alt")
        val useManualLocation = booleanPreferencesKey("use_manual_location")
        val minPassElevation = doublePreferencesKey("min_pass_elevation")
    }

    val settings: Flow<Settings> = dataStore.data
        // A corrupt preferences file must not crash-loop the app: fall back to defaults.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p -> fromPreferences(p) }

    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { p ->
            val next = transform(fromPreferences(p))
            p[Keys.showSun] = next.showSun
            p[Keys.showMoon] = next.showMoon
            p[Keys.showPlanets] = next.showPlanets
            p[Keys.showStars] = next.showStars
            p[Keys.showSatellites] = next.showSatellites
            p[Keys.starMagnitudeLimit] = next.starMagnitudeLimit
            p[Keys.showLabels] = next.showLabels
            p[Keys.showTrajectories] = next.showTrajectories
            p[Keys.trajectorySeconds] = next.trajectorySeconds
            p[Keys.satelliteGroups] = next.satelliteGroups.map { it.name }.toSet()
            p[Keys.nightMode] = next.nightMode
            p[Keys.cameraPassthrough] = next.cameraPassthrough
            p[Keys.useManualLocation] = next.useManualLocation
            p[Keys.minPassElevation] = next.minPassElevationDeg
            val manual = next.manualLocation
            if (manual == null) {
                p.remove(Keys.manualLat)
                p.remove(Keys.manualLon)
                p.remove(Keys.manualAlt)
            } else {
                p[Keys.manualLat] = manual.latitudeDeg
                p[Keys.manualLon] = manual.longitudeDeg
                p[Keys.manualAlt] = manual.altitudeM
            }
        }
    }

    private fun fromPreferences(p: Preferences): Settings {
        val defaults = Settings()
        val lat = p[Keys.manualLat]
        val lon = p[Keys.manualLon]
        return Settings(
            showSun = p[Keys.showSun] ?: defaults.showSun,
            showMoon = p[Keys.showMoon] ?: defaults.showMoon,
            showPlanets = p[Keys.showPlanets] ?: defaults.showPlanets,
            showStars = p[Keys.showStars] ?: defaults.showStars,
            showSatellites = p[Keys.showSatellites] ?: defaults.showSatellites,
            starMagnitudeLimit = p[Keys.starMagnitudeLimit] ?: defaults.starMagnitudeLimit,
            showLabels = p[Keys.showLabels] ?: defaults.showLabels,
            showTrajectories = p[Keys.showTrajectories] ?: defaults.showTrajectories,
            trajectorySeconds = p[Keys.trajectorySeconds] ?: defaults.trajectorySeconds,
            satelliteGroups = p[Keys.satelliteGroups]?.mapNotNull { name -> SatelliteGroup.entries.firstOrNull { it.name == name } }?.toSet()
                ?: defaults.satelliteGroups,
            nightMode = p[Keys.nightMode] ?: defaults.nightMode,
            cameraPassthrough = p[Keys.cameraPassthrough] ?: defaults.cameraPassthrough,
            manualLocation = if (lat != null && lon != null) Observer(lat, lon, p[Keys.manualAlt] ?: 0.0) else null,
            useManualLocation = p[Keys.useManualLocation] ?: defaults.useManualLocation,
            minPassElevationDeg = p[Keys.minPassElevation] ?: defaults.minPassElevationDeg,
        )
    }
}

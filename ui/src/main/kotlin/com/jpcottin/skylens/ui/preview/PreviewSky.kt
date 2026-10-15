package com.jpcottin.skylens.ui.preview

import com.jpcottin.skylens.astro.Observer
import com.jpcottin.skylens.astro.SkyEngine
import com.jpcottin.skylens.astro.SkyLayers
import com.jpcottin.skylens.astro.SkySnapshot
import com.jpcottin.skylens.astro.StarCatalog
import com.jpcottin.skylens.astro.Tle
import com.jpcottin.skylens.data.DevicePose
import com.jpcottin.skylens.data.Settings
import com.jpcottin.skylens.data.TleSource
import com.jpcottin.skylens.data.TleState
import com.jpcottin.skylens.domain.FieldOfView
import com.jpcottin.skylens.domain.LocationState
import com.jpcottin.skylens.domain.SkyUiState
import com.jpcottin.skylens.domain.ViewMode

/**
 * Deterministic sky for previews, screenshot and UI tests: Vancouver, 2026-10-13 20:00 UTC
 * (early afternoon) and 05:00 UTC the next day (night), with the ISS, Tianhe and Hubble.
 */
object PreviewSky {
    val vancouver = Observer(49.2827, -123.1207, 70.0)

    /** 2026-10-13T20:00:00Z */
    const val DAY_MILLIS = 1791921600000L

    /** 2026-10-14T05:00:00Z, 22:00 local: night. */
    const val NIGHT_MILLIS = 1791954000000L

    val tleText = """
        ISS (ZARYA)
        1 25544U 98067A   26286.82178372  .00006592  00000+0  12831-3 0  9991
        2 25544  51.6325  73.0089 0006710 259.3258 100.6975 15.48855783590140
        CSS (TIANHE)
        1 48274U 21035A   26286.58038026  .00022176  00000+0  26300-3 0  9993
        2 48274  41.4695 322.9239 0000414  91.3605 268.7281 15.60678231311658
        HST
        1 20580U 90037B   26286.62243497  .00006667  00000+0  20260-3 0  9992
        2 20580  28.4737 350.3749 0001899  43.5848 316.4898 15.31930979806749
    """.trimIndent()

    val tles: List<Tle> by lazy { Tle.parseAll(tleText) }

    val catalog: StarCatalog by lazy { StarCatalog.loadBundled() }

    val engine: SkyEngine by lazy { SkyEngine(catalog, tles) }

    val daySnapshot: SkySnapshot by lazy { engine.compute(vancouver, DAY_MILLIS, SkyLayers(starMagnitudeLimit = 4.5)) }

    val nightSnapshot: SkySnapshot by lazy { engine.compute(vancouver, NIGHT_MILLIS, SkyLayers(starMagnitudeLimit = 4.5)) }

    /** Looking south-east and up, where the Moon and several planets are on the day snapshot. */
    val dayPose = DevicePose.fromAngles(160.0, 25.0)

    /** Looking east at night: the Moon is low and Jupiter is up. */
    val nightPose = DevicePose.fromAngles(100.0, 35.0)

    fun state(night: Boolean = true, mode: ViewMode = ViewMode.MAP, settings: Settings = Settings(), selectedId: String? = null, targetId: String? = null) = SkyUiState(
        nowMillis = if (night) NIGHT_MILLIS else DAY_MILLIS,
        settings = settings,
        tle = TleState(tles, TleSource.BUNDLED),
        location = LocationState.Fix(vancouver, 12f),
        snapshot = if (night) nightSnapshot else daySnapshot,
        pose = if (night) nightPose else dayPose,
        fov = FieldOfView(66.0, 90.0),
        mode = mode,
        cameraPermissionGranted = mode == ViewMode.AR,
        selectedId = selectedId,
        targetId = targetId,
        satelliteCount = tles.size,
    )
}

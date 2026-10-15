package com.jpcottin.skylens.domain

import android.content.Intent
import com.jpcottin.skylens.astro.Observer
import com.jpcottin.skylens.data.DevicePose

/**
 * Deterministic inputs for screenshots and emulator CI, read from the launch intent of debuggable
 * builds only:
 *
 * ```
 * adb shell am start -n com.jpcottin.skylens/com.jpcottin.skylens.MainActivity \
 *   --el skylens.time 1791921600000 --es skylens.location 49.2827,-123.1207,70 \
 *   --es skylens.pose 180,45,0 --ez skylens.map true --es skylens.select sat:25544 --ez skylens.night false \
 *   --es skylens.tab objects
 * ```
 */
data class DebugOverrides(
    /** Frozen clock, epoch millis. */
    val timeMillis: Long? = null,
    val location: Observer? = null,
    /** Fixed device pose: azimuth, elevation, roll in degrees. */
    val pose: DevicePose? = null,
    /** Start in the drawn-sky (map) mode instead of the camera. */
    val mapMode: Boolean? = null,
    /** Object id to select at launch, e.g. `sat:25544` or `moon`. */
    val selectId: String? = null,
    /** Force the red night mode on or off. */
    val nightMode: Boolean? = null,
    /** Tab to open at launch: `sky` (default), `objects` or `settings`. */
    val startTab: String? = null,
) {
    val isActive: Boolean
        get() = timeMillis != null || location != null || pose != null || mapMode != null || selectId != null || nightMode != null || startTab != null

    companion object {
        const val EXTRA_TIME = "skylens.time"
        const val EXTRA_LOCATION = "skylens.location"
        const val EXTRA_POSE = "skylens.pose"
        const val EXTRA_MAP = "skylens.map"
        const val EXTRA_SELECT = "skylens.select"
        const val EXTRA_NIGHT = "skylens.night"
        const val EXTRA_TAB = "skylens.tab"
        val TABS = setOf("sky", "objects", "settings")

        fun fromIntent(intent: Intent?): DebugOverrides {
            if (intent == null) return DebugOverrides()
            val time = if (intent.hasExtra(EXTRA_TIME)) intent.getLongExtra(EXTRA_TIME, 0L).takeIf { it > 0 } else null
            val location = intent.getStringExtra(EXTRA_LOCATION)?.let(::parseLocation)
            val pose = intent.getStringExtra(EXTRA_POSE)?.let(::parsePose)
            val map = if (intent.hasExtra(EXTRA_MAP)) intent.getBooleanExtra(EXTRA_MAP, false) else null
            val select = intent.getStringExtra(EXTRA_SELECT)?.takeIf { it.isNotBlank() }
            val night = if (intent.hasExtra(EXTRA_NIGHT)) intent.getBooleanExtra(EXTRA_NIGHT, false) else null
            val tab = intent.getStringExtra(EXTRA_TAB)?.lowercase()?.takeIf { it in TABS }
            return DebugOverrides(time, location, pose, map, select, night, tab)
        }

        /** "lat,lon[,altitude]" */
        fun parseLocation(text: String): Observer? {
            val parts = text.split(',').map { it.trim().toDoubleOrNull() ?: return null }
            if (parts.size < 2) return null
            if (parts[0] !in -90.0..90.0 || parts[1] !in -180.0..180.0) return null
            return Observer(parts[0], parts[1], parts.getOrElse(2) { 0.0 })
        }

        /** "azimuth,elevation[,roll]" in degrees */
        fun parsePose(text: String): DevicePose? {
            val parts = text.split(',').map { it.trim().toDoubleOrNull() ?: return null }
            if (parts.size < 2) return null
            return DevicePose.fromAngles(parts[0], parts[1], parts.getOrElse(2) { 0.0 })
        }
    }
}

package com.jpcottin.skylens.ui.glasses

import com.jpcottin.skylens.astro.BodyKind
import com.jpcottin.skylens.astro.Horizontal
import com.jpcottin.skylens.astro.SatellitePass
import com.jpcottin.skylens.astro.SkyPosition
import com.jpcottin.skylens.astro.SkySnapshot
import com.jpcottin.skylens.astro.angularSeparationDegrees

/** What the lens shows: the sky window with one card, or the list of upcoming passes. */
enum class GlassesMode { SKY, PASSES }

/** Pure logic behind the glasses UI, kept out of Compose so it is unit-tested on the JVM. */
object GlassesModel {
    /**
     * The object worth a card: the target when one is set, else the nearest satellite, planet,
     * Moon or Sun within [bodyRadiusDeg] of where the wearer looks, else the nearest bright
     * named star within [starRadiusDeg]. Stars below the horizon never qualify.
     */
    fun focus(
        snapshot: SkySnapshot?,
        centre: Horizontal,
        targetId: String?,
        bodyRadiusDeg: Double = 10.0,
        starRadiusDeg: Double = 4.0,
        starMagnitudeLimit: Double = 2.0,
    ): SkyPosition? {
        if (snapshot == null) return null
        targetId?.let { id -> snapshot.find(id)?.let { return it } }
        val bodies = snapshot.positions.filter { it.obj.kind != BodyKind.STAR && it.horizontal.elevationDeg > -5.0 }
        bodies.minByOrNull { angularSeparationDegrees(it.horizontal, centre) }
            ?.takeIf { angularSeparationDegrees(it.horizontal, centre) <= bodyRadiusDeg }
            ?.let { return it }
        val stars = snapshot.ofKind(BodyKind.STAR).filter {
            it.isAboveHorizon && (it.magnitude ?: 9.0) <= starMagnitudeLimit && it.obj.name.isNotBlank() && !it.obj.name.startsWith("HIP")
        }
        return stars.minByOrNull { angularSeparationDegrees(it.horizontal, centre) }
            ?.takeIf { angularSeparationDegrees(it.horizontal, centre) <= starRadiusDeg }
    }

    /** "in 42 min", "in 3 h 05", "now" — short enough for a list item on the lens. */
    fun countdown(fromMillis: Long, toMillis: Long): String {
        val minutes = (toMillis - fromMillis) / 60_000L
        return when {
            minutes <= 0 -> "now"
            minutes < 60 -> "in $minutes min"
            else -> "in ${minutes / 60} h %02d".format(minutes % 60)
        }
    }

    /** Passes worth listing: visible ones first, then the rest, each soonest first. */
    fun orderForLens(passes: List<SatellitePass>): List<SatellitePass> =
        passes.sortedWith(compareByDescending<SatellitePass> { it.visible }.thenBy { it.riseMillis })
}

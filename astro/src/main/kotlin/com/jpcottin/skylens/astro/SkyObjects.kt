package com.jpcottin.skylens.astro

enum class BodyKind { SUN, MOON, PLANET, STAR, SATELLITE }

/** Something the engine can place on the sky. */
sealed interface SkyObject {
    val id: String
    val name: String
    val kind: BodyKind
}

data object SunObject : SkyObject {
    override val id = "sun"
    override val name = "Sun"
    override val kind = BodyKind.SUN
}

data object MoonObject : SkyObject {
    override val id = "moon"
    override val name = "Moon"
    override val kind = BodyKind.MOON
}

data class PlanetObject(val planet: Planet) : SkyObject {
    override val id = "planet:${planet.name.lowercase()}"
    override val name = planet.displayName
    override val kind = BodyKind.PLANET
}

data class StarObject(val star: Star) : SkyObject {
    // A dozen catalogue rows (Castor B, Alula Australis B, ...) have no Hipparcos number:
    // they must not all collapse onto "star:0", which is the list key and the selection id.
    override val id = if (star.hipparcos > 0) "star:${star.hipparcos}"
        else "star:${star.constellation}:${star.bayer}:${star.name}:${star.raDeg}:${star.decDeg}"
    override val name = star.label.ifBlank { "HIP ${star.hipparcos}" }
    override val kind = BodyKind.STAR
}

data class SatelliteObject(val tle: Tle) : SkyObject {
    override val id = "sat:${tle.catalogNumber}"
    override val name = tle.name
    override val kind = BodyKind.SATELLITE
}

/** Where an object is on the sky right now, plus what the UI needs to draw it. */
data class SkyPosition(
    val obj: SkyObject,
    val horizontal: Horizontal,
    /** Visual magnitude when meaningful (stars, planets, Sun, Moon); null for satellites. */
    val magnitude: Double? = null,
    /** Slant range in km for satellites, distance for solar-system bodies. */
    val distanceKm: Double? = null,
    /** Satellite only: whether it is lit by the Sun, which decides whether it can be seen at night. */
    val sunlit: Boolean? = null,
    /** Satellite only: the next minutes of its path as sky directions, for the trajectory line. */
    val track: List<Horizontal> = emptyList(),
    /** Moon only: illuminated fraction 0..1. */
    val illuminatedFraction: Double? = null,
    /** Moon only: true while the lit part grows (waxing), false while it shrinks (waning). */
    val waxing: Boolean? = null,
    /** Satellite only: ground speed in km/s. */
    val speedKmPerSec: Double? = null,
) {
    val isAboveHorizon: Boolean get() = horizontal.elevationDeg > 0.0
}

package com.jpcottin.skylens.astro

/** Which layers to compute; the UI maps its toggles onto this. */
data class SkyLayers(
    val sun: Boolean = true,
    val moon: Boolean = true,
    val planets: Boolean = true,
    val stars: Boolean = true,
    val satellites: Boolean = true,
    /** Faintest star to include. */
    val starMagnitudeLimit: Double = 4.5,
)

/** Everything on the sky at one instant for one observer. */
data class SkySnapshot(
    val epochMillis: Long,
    val observer: Observer,
    val positions: List<SkyPosition>,
    /** Elevation of the Sun, which decides day / twilight / night rendering. */
    val sunElevationDeg: Double,
) {
    val isNight: Boolean get() = sunElevationDeg < -6.0

    fun find(id: String): SkyPosition? = positions.firstOrNull { it.obj.id == id }

    fun ofKind(kind: BodyKind): List<SkyPosition> = positions.filter { it.obj.kind == kind }
}

/**
 * Computes the sky for an observer at an instant. Pure and deterministic: the same inputs give
 * the same snapshot, which is what the UI tests and the emulator CI rely on.
 */
class SkyEngine(
    private val catalog: StarCatalog,
    satellites: List<Tle> = emptyList(),
    /** Length of the satellite trajectory to draw, in seconds, centred on now. */
    private val trackSpanSeconds: Int = 300,
    private val trackStepSeconds: Int = 10,
) {
    /** Propagators for the satellites this engine knows; deep-space objects are left out. */
    val propagators: List<Sgp4> = satellites.mapNotNull { tle ->
        try {
            Sgp4(tle)
        } catch (e: Sgp4Exception) {
            null
        }
    }

    val satelliteCount: Int get() = propagators.size

    fun compute(observer: Observer, epochMillis: Long, layers: SkyLayers = SkyLayers()): SkySnapshot {
        val jd = JulianDate.fromEpochMillis(epochMillis)
        val positions = ArrayList<SkyPosition>(2000)

        val sunVector = Sun.geocentricKm(jd)
        val sunHorizontal = Frames.geocentricToHorizontal(sunVector, observer, jd)
        if (layers.sun) positions += SkyPosition(SunObject, sunHorizontal, magnitude = -26.7, distanceKm = sunVector.length)

        if (layers.moon) {
            val moonVector = Moon.geocentricKm(jd)
            positions += SkyPosition(
                MoonObject,
                Frames.geocentricToHorizontal(moonVector, observer, jd),
                magnitude = -12.0,
                distanceKm = moonVector.length,
                illuminatedFraction = Moon.illuminatedFraction(jd),
                waxing = Moon.isWaxing(jd),
            )
        }

        if (layers.planets) {
            for (planet in Planet.entries) {
                val v = Planets.geocentricKm(planet, jd)
                positions += SkyPosition(
                    PlanetObject(planet),
                    Frames.geocentricToHorizontal(v, observer, jd),
                    magnitude = Planets.approximateMagnitude(planet, jd),
                    distanceKm = v.length,
                )
            }
        }

        if (layers.stars) {
            for (star in catalog.stars) {
                if (star.magnitude > layers.starMagnitudeLimit) continue
                val ofDate = Precession.j2000ToDate(star.j2000, jd)
                positions += SkyPosition(StarObject(star), Frames.equatorialToHorizontal(ofDate, observer, jd), magnitude = star.magnitude)
            }
        }

        if (layers.satellites) {
            val gmst = SiderealTime.gmstRadians(jd)
            val observerEcef = Frames.observerEcef(observer)
            for (prop in propagators) {
                val state = try {
                    prop.propagateAt(epochMillis)
                } catch (e: Sgp4Exception) {
                    continue
                }
                val ecef = Frames.eciToEcef(state.position, gmst)
                val enu = Frames.ecefToEnu(ecef - observerEcef, observer)
                val horizontal = Horizontal.fromUnitVector(enu)
                val track = if (horizontal.elevationDeg > -10.0) satelliteTrack(prop, observer, epochMillis) else emptyList()
                positions += SkyPosition(
                    SatelliteObject(prop.tle),
                    horizontal,
                    distanceKm = enu.length,
                    sunlit = isSunlit(state.position, sunVector),
                    track = track,
                    speedKmPerSec = state.velocity.length,
                )
            }
        }

        return SkySnapshot(epochMillis, observer, positions, sunHorizontal.elevationDeg)
    }

    /** Sky path of a satellite from `-span/2` to `+span/2` around the instant. */
    fun satelliteTrack(prop: Sgp4, observer: Observer, epochMillis: Long): List<Horizontal> {
        val result = ArrayList<Horizontal>(trackSpanSeconds / trackStepSeconds + 1)
        val observerEcef = Frames.observerEcef(observer)
        var offset = -trackSpanSeconds / 2
        while (offset <= trackSpanSeconds / 2) {
            val t = epochMillis + offset * 1000L
            val jd = JulianDate.fromEpochMillis(t)
            val state = try {
                prop.propagateAt(t)
            } catch (e: Sgp4Exception) {
                break
            }
            val ecef = Frames.eciToEcef(state.position, SiderealTime.gmstRadians(jd))
            result += Horizontal.fromUnitVector(Frames.ecefToEnu(ecef - observerEcef, observer))
            offset += trackStepSeconds
        }
        return result
    }

    companion object {
        /**
         * Cylindrical Earth-shadow test: the satellite is dark when it is on the night side and
         * closer to the Earth-Sun axis than one Earth radius.
         */
        fun isSunlit(satelliteEci: Vector3, sunEci: Vector3): Boolean {
            val sunDir = sunEci.normalized()
            val along = satelliteEci dot sunDir
            if (along >= 0) return true
            val perpendicular = (satelliteEci - sunDir * along).length
            return perpendicular > Frames.EARTH_RADIUS_KM
        }
    }
}

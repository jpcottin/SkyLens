package com.jpcottin.skylens.astro

/** One pass of a satellite over the observer. */
data class SatellitePass(
    val satellite: Tle,
    val riseMillis: Long,
    val culminationMillis: Long,
    val setMillis: Long,
    val riseAzimuthDeg: Double,
    val maxElevationDeg: Double,
    val culminationAzimuthDeg: Double,
    val setAzimuthDeg: Double,
    /** True when the satellite is sunlit at culmination while the observer is in twilight or night. */
    val visible: Boolean,
) {
    val durationSeconds: Long get() = (setMillis - riseMillis) / 1000
}

/**
 * Finds satellite passes by stepping the orbit and refining the horizon crossings by bisection.
 * Pure Kotlin, unit-tested, and fast enough to run for a hundred satellites on the main thread
 * of a test (a day's search is ≈ 3000 propagations per satellite).
 */
class PassPredictor(
    private val coarseStepSeconds: Int = 30,
    private val refineToSeconds: Int = 1,
) {
    fun nextPasses(
        tle: Tle,
        observer: Observer,
        fromMillis: Long,
        hours: Int = 24,
        minElevationDeg: Double = 10.0,
        maxPasses: Int = 10,
    ): List<SatellitePass> {
        val prop = try {
            Sgp4(tle)
        } catch (e: Sgp4Exception) {
            return emptyList()
        }
        val passes = ArrayList<SatellitePass>()
        val endMillis = fromMillis + hours * 3_600_000L
        val step = coarseStepSeconds * 1000L
        var t = fromMillis
        var prevAbove = elevation(prop, observer, t)?.let { it > 0.0 } ?: return emptyList()
        var riseMillis: Long? = if (prevAbove) fromMillis else null
        while (t < endMillis && passes.size < maxPasses) {
            val next = t + step
            val el = elevation(prop, observer, next) ?: return passes
            val above = el > 0.0
            if (!prevAbove && above) {
                riseMillis = bisect(prop, observer, t, next, rising = true)
            } else if (prevAbove && !above && riseMillis != null) {
                val setMillis = bisect(prop, observer, t, next, rising = false)
                buildPass(prop, observer, riseMillis, setMillis, minElevationDeg)?.let(passes::add)
                riseMillis = null
            }
            prevAbove = above
            t = next
        }
        return passes
    }

    private fun buildPass(prop: Sgp4, observer: Observer, riseMillis: Long, setMillis: Long, minElevationDeg: Double): SatellitePass? {
        // Culmination: sample the pass finely and keep the highest point.
        var bestT = riseMillis
        var bestEl = -90.0
        var bestAz = 0.0
        val samples = 60
        for (i in 0..samples) {
            val t = riseMillis + (setMillis - riseMillis) * i / samples
            val h = horizontal(prop, observer, t) ?: continue
            if (h.elevationDeg > bestEl) {
                bestEl = h.elevationDeg
                bestT = t
                bestAz = h.azimuthDeg
            }
        }
        if (bestEl < minElevationDeg) return null
        val riseAz = horizontal(prop, observer, riseMillis)?.azimuthDeg ?: return null
        val setAz = horizontal(prop, observer, setMillis)?.azimuthDeg ?: return null
        val jd = JulianDate.fromEpochMillis(bestT)
        val sunVector = Sun.geocentricKm(jd)
        val state = prop.propagateAt(bestT)
        val sunElevation = Frames.geocentricToHorizontal(sunVector, observer, jd).elevationDeg
        val visible = SkyEngine.isSunlit(state.position, sunVector) && sunElevation < -6.0
        return SatellitePass(prop.tle, riseMillis, bestT, setMillis, riseAz, bestEl, bestAz, setAz, visible)
    }

    private fun bisect(prop: Sgp4, observer: Observer, belowMillis: Long, aboveMillis: Long, rising: Boolean): Long {
        var lo = belowMillis
        var hi = aboveMillis
        while (hi - lo > refineToSeconds * 1000L) {
            val mid = (lo + hi) / 2
            val el = elevation(prop, observer, mid) ?: return mid
            val midAbove = el > 0.0
            if (midAbove == rising) hi = mid else lo = mid
        }
        return hi
    }

    private fun elevation(prop: Sgp4, observer: Observer, millis: Long): Double? = horizontal(prop, observer, millis)?.elevationDeg

    private fun horizontal(prop: Sgp4, observer: Observer, millis: Long): Horizontal? {
        val state = try {
            prop.propagateAt(millis)
        } catch (e: Sgp4Exception) {
            return null
        }
        val jd = JulianDate.fromEpochMillis(millis)
        val ecef = Frames.eciToEcef(state.position, SiderealTime.gmstRadians(jd))
        return Frames.lookAngles(ecef, observer).first
    }
}

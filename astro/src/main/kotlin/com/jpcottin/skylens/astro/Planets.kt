package com.jpcottin.skylens.astro

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class Planet(val displayName: String) {
    MERCURY("Mercury"), VENUS("Venus"), MARS("Mars"), JUPITER("Jupiter"), SATURN("Saturn"), URANUS("Uranus"), NEPTUNE("Neptune");
}

/**
 * Planetary positions from the JPL approximate Keplerian elements (Standish, "Keplerian Elements
 * for Approximate Positions of the Major Planets", table valid 1800–2050). Accuracy is a few
 * arcminutes, far below what the eye can separate from the real planet on an AR overlay.
 */
object Planets {
    private class Elements(
        val a: Double, val e: Double, val i: Double, val l: Double, val lonPeri: Double, val lonNode: Double,
        val aDot: Double, val eDot: Double, val iDot: Double, val lDot: Double, val lonPeriDot: Double, val lonNodeDot: Double,
    )

    private val elements: Map<Planet, Elements> = mapOf(
        Planet.MERCURY to Elements(0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593,
            0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081),
        Planet.VENUS to Elements(0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255,
            0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418),
        Planet.MARS to Elements(1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891,
            0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343),
        Planet.JUPITER to Elements(5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909,
            -0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106),
        Planet.SATURN to Elements(9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448,
            -0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794),
        Planet.URANUS to Elements(19.18916464, 0.04725744, 0.77263783, 313.23810451, 170.95427630, 74.01692503,
            -0.00196176, -0.00004397, -0.00242939, 428.48202785, 0.40805281, 0.04240589),
        Planet.NEPTUNE to Elements(30.06992276, 0.00859048, 1.77004347, -55.12002969, 44.96476227, 131.78422574,
            0.00026291, 0.00005105, 0.00035372, 218.45945325, -0.32241464, -0.00508664),
    )

    private val earth = Elements(1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0,
        0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0)

    /** Obliquity of the J2000 ecliptic. */
    private val obliquityJ2000 = 23.43928 * DEG_TO_RAD

    /** Heliocentric J2000 ecliptic position in AU. */
    private fun heliocentric(el: Elements, t: Double): Vector3 {
        val a = el.a + el.aDot * t
        val e = el.e + el.eDot * t
        val i = (el.i + el.iDot * t) * DEG_TO_RAD
        val l = el.l + el.lDot * t
        val lonPeri = el.lonPeri + el.lonPeriDot * t
        val lonNode = (el.lonNode + el.lonNodeDot * t) * DEG_TO_RAD
        val argPeri = lonPeri * DEG_TO_RAD - lonNode
        val meanAnomaly = normalizeDegrees(l - lonPeri).let { if (it > 180) it - 360 else it } * DEG_TO_RAD

        var ecc = meanAnomaly + e * sin(meanAnomaly)
        for (iter in 0 until 30) {
            val dm = meanAnomaly - (ecc - e * sin(ecc))
            val de = dm / (1 - e * cos(ecc))
            ecc += de
            if (abs(de) < 1e-12) break
        }
        val xp = a * (cos(ecc) - e)
        val yp = a * sqrt(1 - e * e) * sin(ecc)
        val cw = cos(argPeri)
        val sw = sin(argPeri)
        val co = cos(lonNode)
        val so = sin(lonNode)
        val ci = cos(i)
        val si = sin(i)
        return Vector3(
            (cw * co - sw * so * ci) * xp + (-sw * co - cw * so * ci) * yp,
            (cw * so + sw * co * ci) * xp + (-sw * so + cw * co * ci) * yp,
            (sw * si) * xp + (cw * si) * yp,
        )
    }

    /** Geocentric position of date in km (equatorial frame). */
    fun geocentricKm(planet: Planet, jd: Double): Vector3 {
        val t = JulianDate.centuriesSinceJ2000(jd)
        val p = heliocentric(elements.getValue(planet), t)
        val e = heliocentric(earth, t)
        val g = p - e
        // J2000 ecliptic → J2000 equatorial.
        val eq = Vector3(g.x, g.y * cos(obliquityJ2000) - g.z * sin(obliquityJ2000), g.y * sin(obliquityJ2000) + g.z * cos(obliquityJ2000))
        val dir = Precession.j2000ToDate(Frames.vectorToEquatorial(eq), jd)
        return Frames.equatorialToVector(dir, eq.length * Sun.AU_KM)
    }

    /** Geocentric equatorial coordinates of date. */
    fun equatorial(planet: Planet, jd: Double): Equatorial = Frames.vectorToEquatorial(geocentricKm(planet, jd))

    /** Rough visual magnitude, enough to size the marker: phase effects ignored except for Venus/Mercury via distance. */
    fun approximateMagnitude(planet: Planet, jd: Double): Double {
        val t = JulianDate.centuriesSinceJ2000(jd)
        val p = heliocentric(elements.getValue(planet), t)
        val e = heliocentric(earth, t)
        val r = p.length
        val delta = (p - e).length
        val base = when (planet) {
            Planet.MERCURY -> -0.6
            Planet.VENUS -> -4.4
            Planet.MARS -> -1.5
            Planet.JUPITER -> -9.4
            Planet.SATURN -> -8.9
            Planet.URANUS -> -7.2
            Planet.NEPTUNE -> -6.9
        }
        return base + 5 * kotlin.math.log10(r * delta)
    }
}

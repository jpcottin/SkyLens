package com.jpcottin.skylens.astro

import kotlin.math.cos
import kotlin.math.sin

/** Geocentric position of the Sun (Meeus, Astronomical Algorithms ch. 25, low-precision ≈ 0.01°). */
object Sun {
    const val AU_KM = 149_597_870.7

    /** Apparent geocentric ecliptic longitude (radians) and distance (AU) for a Julian date. */
    data class EclipticState(val longitudeRad: Double, val distanceAu: Double, val obliquityRad: Double)

    fun ecliptic(jd: Double): EclipticState {
        val t = JulianDate.centuriesSinceJ2000(jd)
        val l0 = normalizeDegrees(280.46646 + 36000.76983 * t + 0.0003032 * t * t)
        val m = normalizeDegrees(357.52911 + 35999.05029 * t - 0.0001537 * t * t) * DEG_TO_RAD
        val e = 0.016708634 - 0.000042037 * t - 0.0000001267 * t * t
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
            (0.019993 - 0.000101 * t) * sin(2 * m) +
            0.000289 * sin(3 * m)
        val trueLongitude = l0 + c
        val trueAnomaly = m + c * DEG_TO_RAD
        val r = 1.000001018 * (1 - e * e) / (1 + e * cos(trueAnomaly))
        val omega = (125.04 - 1934.136 * t) * DEG_TO_RAD
        val apparentLongitude = trueLongitude - 0.00569 - 0.00478 * sin(omega)
        val obliquity = Precession.meanObliquityRad(jd) + 0.00256 * DEG_TO_RAD * cos(omega)
        return EclipticState(normalizeDegrees(apparentLongitude) * DEG_TO_RAD, r, obliquity)
    }

    /** Apparent geocentric equatorial coordinates of date. */
    fun equatorial(jd: Double): Equatorial {
        val s = ecliptic(jd)
        return Precession.eclipticToEquatorial(s.longitudeRad, 0.0, s.obliquityRad)
    }

    /** Geocentric position vector of date in km. */
    fun geocentricKm(jd: Double): Vector3 {
        val s = ecliptic(jd)
        return Frames.equatorialToVector(Precession.eclipticToEquatorial(s.longitudeRad, 0.0, s.obliquityRad), s.distanceAu * AU_KM)
    }
}

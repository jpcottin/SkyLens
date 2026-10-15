package com.jpcottin.skylens.astro

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Precession of equatorial coordinates between J2000.0 and the equinox of date (Meeus ch. 21). */
object Precession {
    fun j2000ToDate(eq: Equatorial, jd: Double): Equatorial {
        val t = JulianDate.centuriesSinceJ2000(jd)
        val arcsec = 1.0 / 3600.0 * DEG_TO_RAD
        val zeta = (2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t) * arcsec
        val z = (2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t) * arcsec
        val theta = (2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t) * arcsec
        val ra0 = eq.raDeg * DEG_TO_RAD
        val dec0 = eq.decDeg * DEG_TO_RAD
        val a = cos(dec0) * sin(ra0 + zeta)
        val b = cos(theta) * cos(dec0) * cos(ra0 + zeta) - sin(theta) * sin(dec0)
        val c = sin(theta) * cos(dec0) * cos(ra0 + zeta) + cos(theta) * sin(dec0)
        val ra = atan2(a, b) + z
        val dec = asin(c.coerceIn(-1.0, 1.0))
        return Equatorial(normalizeDegrees(ra * RAD_TO_DEG), dec * RAD_TO_DEG)
    }

    /** Mean obliquity of the ecliptic in radians (Meeus 22.2). */
    fun meanObliquityRad(jd: Double): Double {
        val t = JulianDate.centuriesSinceJ2000(jd)
        val arcsec = 23.0 * 3600 + 26.0 * 60 + 21.448 - 46.8150 * t - 0.00059 * t * t + 0.001813 * t * t * t
        return arcsec / 3600.0 * DEG_TO_RAD
    }

    /** Converts ecliptic longitude/latitude (radians) to equatorial coordinates for an obliquity. */
    fun eclipticToEquatorial(lonRad: Double, latRad: Double, obliquityRad: Double): Equatorial {
        val ra = atan2(sin(lonRad) * cos(obliquityRad) - kotlin.math.tan(latRad) * sin(obliquityRad), cos(lonRad))
        val dec = asin((sin(latRad) * cos(obliquityRad) + cos(latRad) * sin(obliquityRad) * sin(lonRad)).coerceIn(-1.0, 1.0))
        return Equatorial(normalizeDegrees(ra * RAD_TO_DEG), dec * RAD_TO_DEG)
    }
}

package com.jpcottin.skylens.astro

import kotlin.math.cos
import kotlin.math.sin

/**
 * Geocentric position of the Moon from the ELP-2000/82 truncation in Meeus ch. 47 (≈ 10″ in
 * longitude, 4″ in latitude), with the main nutation term for the apparent place.
 */
object Moon {
    private class Term(val d: Int, val m: Int, val mp: Int, val f: Int, val sl: Double, val sr: Double)
    private class LatTerm(val d: Int, val m: Int, val mp: Int, val f: Int, val sb: Double)

    private val longitudeTerms = listOf(
        Term(0, 0, 1, 0, 6288774.0, -20905355.0), Term(2, 0, -1, 0, 1274027.0, -3699111.0),
        Term(2, 0, 0, 0, 658314.0, -2955968.0), Term(0, 0, 2, 0, 213618.0, -569925.0),
        Term(0, 1, 0, 0, -185116.0, 48888.0), Term(0, 0, 0, 2, -114332.0, -3149.0),
        Term(2, 0, -2, 0, 58793.0, 246158.0), Term(2, -1, -1, 0, 57066.0, -152138.0),
        Term(2, 0, 1, 0, 53322.0, -170733.0), Term(2, -1, 0, 0, 45758.0, -204586.0),
        Term(0, 1, -1, 0, -40923.0, -129620.0), Term(1, 0, 0, 0, -34720.0, 108743.0),
        Term(0, 1, 1, 0, -30383.0, 104755.0), Term(2, 0, 0, -2, 15327.0, 10321.0),
        Term(0, 0, 1, 2, -12528.0, 0.0), Term(0, 0, 1, -2, 10980.0, 79661.0),
        Term(4, 0, -1, 0, 10675.0, -34782.0), Term(0, 0, 3, 0, 10034.0, -23210.0),
        Term(4, 0, -2, 0, 8548.0, -21636.0), Term(2, 1, -1, 0, -7888.0, 24208.0),
        Term(2, 1, 0, 0, -6766.0, 30824.0), Term(1, 0, -1, 0, -5163.0, -8379.0),
        Term(1, 1, 0, 0, 4987.0, -16675.0), Term(2, -1, 1, 0, 4036.0, -12831.0),
        Term(2, 0, 2, 0, 3994.0, -10445.0), Term(4, 0, 0, 0, 3861.0, -11650.0),
        Term(2, 0, -3, 0, 3665.0, 14403.0), Term(0, 1, -2, 0, -2689.0, -7003.0),
        Term(2, 0, -1, 2, -2602.0, 0.0), Term(2, -1, -2, 0, 2390.0, 10056.0),
        Term(1, 0, 1, 0, -2348.0, 6322.0), Term(2, -2, 0, 0, 2236.0, -9884.0),
        Term(0, 1, 2, 0, -2120.0, 5751.0), Term(0, 2, 0, 0, -2069.0, 0.0),
        Term(2, -2, -1, 0, 2048.0, -4950.0), Term(2, 0, 1, -2, -1773.0, 4130.0),
        Term(2, 0, 0, 2, -1595.0, 0.0), Term(4, -1, -1, 0, 1215.0, -3958.0),
        Term(0, 0, 2, 2, -1110.0, 0.0), Term(3, 0, -1, 0, -892.0, 3258.0),
        Term(2, 1, 1, 0, -810.0, 2616.0), Term(4, -1, -2, 0, 759.0, -1897.0),
        Term(0, 2, -1, 0, -713.0, -2117.0), Term(2, 2, -1, 0, -700.0, 2354.0),
        Term(2, 1, -2, 0, 691.0, 0.0), Term(2, -1, 0, -2, 596.0, 0.0),
        Term(4, 0, 1, 0, 549.0, -1423.0), Term(0, 0, 4, 0, 537.0, -1117.0),
        Term(4, -1, 0, 0, 520.0, -1571.0), Term(1, 0, -2, 0, -487.0, -1739.0),
        Term(2, 1, 0, -2, -399.0, 0.0), Term(0, 0, 2, -2, -381.0, -4421.0),
        Term(1, 1, 1, 0, 351.0, 0.0), Term(3, 0, -2, 0, -340.0, 0.0),
        Term(4, 0, -3, 0, 330.0, 0.0), Term(2, -1, 2, 0, 327.0, 0.0),
        Term(0, 2, 1, 0, -323.0, 1165.0), Term(1, 1, -1, 0, 299.0, 0.0),
        Term(2, 0, 3, 0, 294.0, 0.0), Term(2, 0, -1, -2, 0.0, 8752.0),
    )

    private val latitudeTerms = listOf(
        LatTerm(0, 0, 0, 1, 5128122.0), LatTerm(0, 0, 1, 1, 280602.0), LatTerm(0, 0, 1, -1, 277693.0),
        LatTerm(2, 0, 0, -1, 173237.0), LatTerm(2, 0, -1, 1, 55413.0), LatTerm(2, 0, -1, -1, 46271.0),
        LatTerm(2, 0, 0, 1, 32573.0), LatTerm(0, 0, 2, 1, 17198.0), LatTerm(2, 0, 1, -1, 9266.0),
        LatTerm(0, 0, 2, -1, 8822.0), LatTerm(2, -1, 0, -1, 8216.0), LatTerm(2, 0, -2, -1, 4324.0),
        LatTerm(2, 0, 1, 1, 4200.0), LatTerm(2, 1, 0, -1, -3359.0), LatTerm(2, -1, -1, 1, 2463.0),
        LatTerm(2, -1, 0, 1, 2211.0), LatTerm(2, -1, -1, -1, 2065.0), LatTerm(0, 1, -1, -1, -1870.0),
        LatTerm(4, 0, -1, -1, 1828.0), LatTerm(0, 1, 0, 1, -1794.0), LatTerm(0, 0, 0, 3, -1749.0),
        LatTerm(0, 1, -1, 1, -1565.0), LatTerm(1, 0, 0, 1, -1491.0), LatTerm(0, 1, 1, 1, -1475.0),
        LatTerm(0, 1, 1, -1, -1410.0), LatTerm(0, 1, 0, -1, -1344.0), LatTerm(1, 0, 0, -1, -1335.0),
        LatTerm(0, 0, 3, 1, 1107.0), LatTerm(4, 0, 0, -1, 1021.0), LatTerm(4, 0, -1, 1, 833.0),
        LatTerm(0, 0, 1, -3, 777.0), LatTerm(4, 0, -2, 1, 671.0), LatTerm(2, 0, 0, -3, 607.0),
        LatTerm(2, 0, 2, -1, 596.0), LatTerm(2, -1, 1, -1, 491.0), LatTerm(2, 0, -2, 1, -451.0),
        LatTerm(0, 0, 3, -1, 439.0), LatTerm(2, 0, 2, 1, 422.0), LatTerm(2, 0, -3, -1, 421.0),
        LatTerm(2, 1, -1, 1, -366.0), LatTerm(2, 1, 0, 1, -351.0), LatTerm(4, 0, 0, 1, 331.0),
        LatTerm(2, -1, 1, 1, 315.0), LatTerm(2, -2, 0, -1, 302.0), LatTerm(0, 0, 1, 3, -283.0),
        LatTerm(2, 1, 1, -1, -229.0), LatTerm(1, 1, 0, -1, 223.0), LatTerm(1, 1, 0, 1, 223.0),
        LatTerm(0, 1, -2, -1, -220.0), LatTerm(2, 1, -1, -1, -220.0), LatTerm(1, 0, 1, 1, -185.0),
        LatTerm(2, -1, -2, -1, 181.0), LatTerm(0, 1, 2, 1, -177.0), LatTerm(4, 0, -2, -1, 176.0),
        LatTerm(4, -1, -1, -1, 166.0), LatTerm(1, 0, 1, -1, -164.0), LatTerm(4, 0, 1, -1, 132.0),
        LatTerm(1, 0, -1, -1, -119.0), LatTerm(4, -1, 0, -1, 115.0), LatTerm(2, -2, 0, 1, 107.0),
    )

    /** Geocentric ecliptic longitude/latitude (radians, apparent) and distance (km). */
    data class EclipticState(val longitudeRad: Double, val latitudeRad: Double, val distanceKm: Double, val obliquityRad: Double)

    fun ecliptic(jd: Double): EclipticState {
        val t = JulianDate.centuriesSinceJ2000(jd)
        val t2 = t * t
        val t3 = t2 * t
        val t4 = t3 * t
        val lp = normalizeDegrees(218.3164477 + 481267.88123421 * t - 0.0015786 * t2 + t3 / 538841.0 - t4 / 65194000.0)
        val d = normalizeDegrees(297.8501921 + 445267.1114034 * t - 0.0018819 * t2 + t3 / 545868.0 - t4 / 113065000.0)
        val m = normalizeDegrees(357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000.0)
        val mp = normalizeDegrees(134.9633964 + 477198.8675055 * t + 0.0087414 * t2 + t3 / 69699.0 - t4 / 14712000.0)
        val f = normalizeDegrees(93.2720950 + 483202.0175233 * t - 0.0036539 * t2 - t3 / 3526000.0 + t4 / 863310000.0)
        val a1 = normalizeDegrees(119.75 + 131.849 * t)
        val a2 = normalizeDegrees(53.09 + 479264.290 * t)
        val a3 = normalizeDegrees(313.45 + 481266.484 * t)
        val e = 1 - 0.002516 * t - 0.0000074 * t2
        val e2 = e * e

        var sumL = 0.0
        var sumR = 0.0
        for (term in longitudeTerms) {
            val arg = (term.d * d + term.m * m + term.mp * mp + term.f * f) * DEG_TO_RAD
            val eFactor = when (kotlin.math.abs(term.m)) { 1 -> e; 2 -> e2; else -> 1.0 }
            sumL += term.sl * eFactor * sin(arg)
            sumR += term.sr * eFactor * cos(arg)
        }
        var sumB = 0.0
        for (term in latitudeTerms) {
            val arg = (term.d * d + term.m * m + term.mp * mp + term.f * f) * DEG_TO_RAD
            val eFactor = when (kotlin.math.abs(term.m)) { 1 -> e; 2 -> e2; else -> 1.0 }
            sumB += term.sb * eFactor * sin(arg)
        }
        sumL += 3958.0 * sin(a1 * DEG_TO_RAD) + 1962.0 * sin((lp - f) * DEG_TO_RAD) + 318.0 * sin(a2 * DEG_TO_RAD)
        sumB += -2235.0 * sin(lp * DEG_TO_RAD) + 382.0 * sin(a3 * DEG_TO_RAD) + 175.0 * sin((a1 - f) * DEG_TO_RAD) +
            175.0 * sin((a1 + f) * DEG_TO_RAD) + 127.0 * sin((lp - mp) * DEG_TO_RAD) - 115.0 * sin((lp + mp) * DEG_TO_RAD)

        // Nutation in longitude (leading terms, Meeus ch. 22) for the apparent place.
        val omega = (125.04452 - 1934.136261 * t) * DEG_TO_RAD
        val sunL = (280.4665 + 36000.7698 * t) * DEG_TO_RAD
        val moonL = (218.3165 + 481267.8813 * t) * DEG_TO_RAD
        val dPsiArcsec = -17.20 * sin(omega) - 1.32 * sin(2 * sunL) - 0.23 * sin(2 * moonL) + 0.21 * sin(2 * omega)
        val dEpsArcsec = 9.20 * cos(omega) + 0.57 * cos(2 * sunL) + 0.10 * cos(2 * moonL) - 0.09 * cos(2 * omega)

        val lambda = normalizeDegrees(lp + sumL / 1_000_000.0 + dPsiArcsec / 3600.0) * DEG_TO_RAD
        val beta = (sumB / 1_000_000.0) * DEG_TO_RAD
        val distance = 385000.56 + sumR / 1000.0
        val obliquity = Precession.meanObliquityRad(jd) + dEpsArcsec / 3600.0 * DEG_TO_RAD
        return EclipticState(lambda, beta, distance, obliquity)
    }

    /** Apparent geocentric equatorial coordinates of date. */
    fun equatorial(jd: Double): Equatorial {
        val s = ecliptic(jd)
        return Precession.eclipticToEquatorial(s.longitudeRad, s.latitudeRad, s.obliquityRad)
    }

    /** Geocentric position vector of date in km. */
    fun geocentricKm(jd: Double): Vector3 {
        val s = ecliptic(jd)
        return Frames.equatorialToVector(Precession.eclipticToEquatorial(s.longitudeRad, s.latitudeRad, s.obliquityRad), s.distanceKm)
    }

    /** Illuminated fraction of the disc, 0 (new) to 1 (full). */
    fun illuminatedFraction(jd: Double): Double {
        val moon = ecliptic(jd)
        val sun = Sun.ecliptic(jd)
        val elongation = kotlin.math.acos(
            (cos(moon.latitudeRad) * cos(moon.longitudeRad - sun.longitudeRad)).coerceIn(-1.0, 1.0),
        )
        val sunDistanceKm = sun.distanceAu * Sun.AU_KM
        val phaseAngle = kotlin.math.atan2(sunDistanceKm * sin(elongation), moon.distanceKm - sunDistanceKm * cos(elongation))
        return (1 + cos(phaseAngle)) / 2
    }

    /** True when the Moon is waxing (its longitude is less than 180° ahead of the Sun's). */
    fun isWaxing(jd: Double): Boolean {
        val moon = ecliptic(jd)
        val sun = Sun.ecliptic(jd)
        return normalizeRadians(moon.longitudeRad - sun.longitudeRad) < Math.PI
    }
}

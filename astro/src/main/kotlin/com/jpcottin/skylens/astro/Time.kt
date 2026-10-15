package com.jpcottin.skylens.astro

/**
 * Time scales. Everything is driven by UTC epoch milliseconds (what Android clocks give) and
 * converted to Julian dates; UT1 ≈ UTC and TT ≈ UTC are accepted (sub-arcsecond effects here).
 */
object JulianDate {
    /** J2000.0 epoch, 2000-01-01 12:00 TT. */
    const val J2000 = 2451545.0

    /** Julian date of the Unix epoch, 1970-01-01 00:00 UTC. */
    const val UNIX_EPOCH = 2440587.5

    const val MILLIS_PER_DAY = 86_400_000.0

    fun fromEpochMillis(epochMillis: Long): Double = epochMillis / MILLIS_PER_DAY + UNIX_EPOCH

    fun toEpochMillis(jd: Double): Long = Math.round((jd - UNIX_EPOCH) * MILLIS_PER_DAY)

    /** Julian centuries since J2000.0. */
    fun centuriesSinceJ2000(jd: Double): Double = (jd - J2000) / 36525.0

    /** Julian date of a civil (Gregorian) calendar date at 0h UT; `day` may be fractional. */
    fun fromCalendar(year: Int, month: Int, day: Double): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = Math.floorDiv(y, 100)
        val b = 2 - a + Math.floorDiv(a, 4)
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + day + b - 1524.5
    }
}

object SiderealTime {
    /**
     * Greenwich mean sidereal time in hours (IAU 1982 expression, as used by Vallado), for a
     * Julian date on the UT1 scale.
     */
    fun gmstHours(jdUt1: Double): Double {
        val t = JulianDate.centuriesSinceJ2000(jdUt1)
        var seconds = 67310.54841 + (876600.0 * 3600.0 + 8640184.812866) * t + 0.093104 * t * t - 6.2e-6 * t * t * t
        seconds %= 86400.0
        if (seconds < 0) seconds += 86400.0
        return seconds / 3600.0
    }

    fun gmstRadians(jdUt1: Double): Double = gmstHours(jdUt1) * 15.0 * DEG_TO_RAD

    /** Local mean sidereal time in degrees for an east-positive longitude. */
    fun lmstDegrees(jdUt1: Double, longitudeDeg: Double): Double =
        normalizeDegrees(gmstHours(jdUt1) * 15.0 + longitudeDeg)
}

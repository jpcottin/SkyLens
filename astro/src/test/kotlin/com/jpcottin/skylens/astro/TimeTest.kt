package com.jpcottin.skylens.astro

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeTest {
    @Test
    fun unixEpoch_isKnownJulianDate() {
        assertEquals(2440587.5, JulianDate.fromEpochMillis(0L), 1e-9)
    }

    @Test
    fun j2000_roundTripsThroughMillis() {
        val millis = JulianDate.toEpochMillis(JulianDate.J2000)
        assertEquals(946_728_000_000L, millis)
        assertEquals(JulianDate.J2000, JulianDate.fromEpochMillis(millis), 1e-9)
    }

    @Test
    fun calendar_matchesMeeusExamples() {
        // Meeus 7.a: 1957 October 4.81 → JD 2436116.31
        assertEquals(2436116.31, JulianDate.fromCalendar(1957, 10, 4.81), 1e-6)
        // 2000 January 1.5 → J2000
        assertEquals(JulianDate.J2000, JulianDate.fromCalendar(2000, 1, 1.5), 1e-9)
    }

    @Test
    fun gmst_matchesSkyfieldAtEveryFixtureSite() {
        for (site in BodyFixtures.sites) {
            val jd = JulianDate.fromEpochMillis(site.epochMillis)
            // Skyfield's GMST includes UT1-UTC (≈ 0.1 s), hence the half-second tolerance.
            assertEquals(site.utc, site.gmstHours, SiderealTime.gmstHours(jd), 0.5 / 3600.0)
        }
    }

    @Test
    fun lmst_addsLongitude() {
        val jd = JulianDate.J2000
        val g = SiderealTime.gmstHours(jd) * 15.0
        assertEquals(normalizeDegrees(g + 45.0), SiderealTime.lmstDegrees(jd, 45.0), 1e-9)
        assertEquals(normalizeDegrees(g - 123.0), SiderealTime.lmstDegrees(jd, -123.0), 1e-9)
    }
}

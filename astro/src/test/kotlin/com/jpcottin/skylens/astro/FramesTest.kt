package com.jpcottin.skylens.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FramesTest {
    @Test
    fun observerEcef_equatorAndPole() {
        val equator = Frames.observerEcef(Observer(0.0, 0.0, 0.0))
        assertEquals(6378.137, equator.x, 1e-6)
        assertEquals(0.0, equator.y, 1e-6)
        assertEquals(0.0, equator.z, 1e-6)
        val pole = Frames.observerEcef(Observer(90.0, 0.0, 0.0))
        assertEquals(6356.752, pole.z, 1e-2)
        assertEquals(0.0, pole.x, 1e-6)
    }

    @Test
    fun eciEcef_roundTrip() {
        val v = Vector3(4000.0, -3000.0, 2500.0)
        val back = Frames.ecefToEci(Frames.eciToEcef(v, 1.234), 1.234)
        assertEquals(v.x, back.x, 1e-9)
        assertEquals(v.y, back.y, 1e-9)
        assertEquals(v.z, back.z, 1e-9)
    }

    @Test
    fun enu_pointStraightUpFromEquator() {
        val o = Observer(0.0, 0.0, 0.0)
        val enu = Frames.ecefToEnu(Vector3(100.0, 0.0, 0.0), o)
        assertEquals(0.0, enu.x, 1e-9)
        assertEquals(0.0, enu.y, 1e-9)
        assertEquals(100.0, enu.z, 1e-9)
        val east = Frames.ecefToEnu(Vector3(0.0, 100.0, 0.0), o)
        assertEquals(100.0, east.x, 1e-9)
        val north = Frames.ecefToEnu(Vector3(0.0, 0.0, 100.0), o)
        assertEquals(100.0, north.y, 1e-9)
    }

    @Test
    fun horizontal_unitVectorRoundTrip() {
        for (az in listOf(0.0, 45.0, 123.4, 270.0, 359.0)) {
            for (el in listOf(-30.0, 0.0, 15.5, 89.0)) {
                val h = Horizontal.fromUnitVector(Horizontal(az, el).toUnitVector())
                assertEquals(az, h.azimuthDeg, 1e-9)
                assertEquals(el, h.elevationDeg, 1e-9)
            }
        }
    }

    @Test
    fun equatorialToHorizontal_polarisIsNearTheNorthCelestialPole() {
        val o = Observer(49.2827, -123.1207, 70.0)
        val jd = JulianDate.fromEpochMillis(1791921600000L)
        val polaris = Frames.equatorialToHorizontal(Precession.j2000ToDate(Equatorial(2.52975 * 15, 89.2641), jd), o, jd)
        val expected = BodyFixtures.starsVancouver.getValue("Polaris")
        assertEquals(expected.elevationDeg, polaris.elevationDeg, 0.02)
        assertTrue(angularSeparationDegrees(expected, polaris) < 0.02)
    }

    @Test
    fun equatorialToHorizontal_matchesSkyfieldForBrightStars() {
        val o = Observer(49.2827, -123.1207, 70.0)
        val jd = JulianDate.fromEpochMillis(1791921600000L)
        val catalog = mapOf(
            "Sirius" to Equatorial(6.75248 * 15, -16.7161),
            "Vega" to Equatorial(18.61560 * 15, 38.7837),
            "Betelgeuse" to Equatorial(5.91953 * 15, 7.4071),
            "Arcturus" to Equatorial(14.26103 * 15, 19.1824),
        )
        for ((name, j2000) in catalog) {
            val h = Frames.equatorialToHorizontal(Precession.j2000ToDate(j2000, jd), o, jd)
            val expected = BodyFixtures.starsVancouver.getValue(name)
            // Nutation and aberration are ignored (≈ 20″ each); allow a tenth of a degree.
            assertTrue("$name off by ${angularSeparationDegrees(expected, h)}°", angularSeparationDegrees(expected, h) < 0.1)
        }
    }

    @Test
    fun compassPoint_covers16Points() {
        assertEquals("N", compassPoint(0.0))
        assertEquals("N", compassPoint(359.0))
        assertEquals("NE", compassPoint(45.0))
        assertEquals("E", compassPoint(90.0))
        assertEquals("SSW", compassPoint(202.0))
        assertEquals("W", compassPoint(270.0))
        assertEquals("NNW", compassPoint(337.5))
    }

    @Test
    fun angularDifference_wraps() {
        assertEquals(-20.0, angularDifferenceDegrees(350.0, 10.0), 1e-9)
        assertEquals(20.0, angularDifferenceDegrees(10.0, 350.0), 1e-9)
        assertEquals(180.0, angularDifferenceDegrees(180.0, 0.0), 1e-9)
    }
}

package com.jpcottin.skylens.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyEngineTest {
    private val catalog = StarCatalog.loadBundled()
    private val satellites = Sgp4Fixtures.cases.map { Tle.parse(it.name, it.line1, it.line2)!! }
    private val engine = SkyEngine(catalog, satellites)
    private val vancouver = Observer(49.2827, -123.1207, 70.0)
    private val t0 = 1791921600000L // 2026-10-13T20:00Z, local midday

    @Test
    fun compute_isDeterministic() {
        val a = engine.compute(vancouver, t0)
        val b = engine.compute(vancouver, t0)
        assertEquals(a, b)
    }

    @Test
    fun compute_containsEveryLayer() {
        val snap = engine.compute(vancouver, t0, SkyLayers(starMagnitudeLimit = 5.0))
        assertEquals(1, snap.ofKind(BodyKind.SUN).size)
        assertEquals(1, snap.ofKind(BodyKind.MOON).size)
        assertEquals(7, snap.ofKind(BodyKind.PLANET).size)
        assertEquals(1637, snap.ofKind(BodyKind.STAR).size)
        assertEquals(3, snap.ofKind(BodyKind.SATELLITE).size)
        assertTrue(snap.sunElevationDeg > 30.0)
        assertFalse(snap.isNight)
    }

    @Test
    fun layers_canBeSwitchedOff() {
        val snap = engine.compute(vancouver, t0, SkyLayers(sun = false, moon = false, planets = false, stars = false, satellites = true))
        assertEquals(3, snap.positions.size)
        assertTrue(snap.positions.all { it.obj.kind == BodyKind.SATELLITE })
    }

    @Test
    fun starMagnitudeLimit_filters() {
        val bright = engine.compute(vancouver, t0, SkyLayers(starMagnitudeLimit = 2.0)).ofKind(BodyKind.STAR)
        assertTrue(bright.size in 40..60)
        assertTrue(bright.all { it.magnitude!! <= 2.0 })
    }

    @Test
    fun sunAndMoon_matchTheReferenceFixtures() {
        val site = BodyFixtures.sites.first()
        val snap = engine.compute(site.observer, site.epochMillis)
        val sun = snap.find("sun")!!.horizontal
        val expectedSun = site.bodies.getValue("Sun")
        assertTrue(angularSeparationDegrees(Horizontal(expectedSun.azDeg, expectedSun.elDeg), sun) < 0.05)
        val moon = snap.find("moon")!!
        val expectedMoon = site.bodies.getValue("Moon")
        assertTrue(angularSeparationDegrees(Horizontal(expectedMoon.azDeg, expectedMoon.elDeg), moon.horizontal) < 0.1)
        assertNotNull(moon.illuminatedFraction)
    }

    @Test
    fun moon_carriesThePhaseDirection() {
        // Every reference instant: the snapshot says waxing exactly when the Moon model does,
        // so the overlay can put the dark limb on the right side.
        for (site in BodyFixtures.sites) {
            val jd = JulianDate.fromEpochMillis(site.epochMillis)
            val moon = engine.compute(site.observer, site.epochMillis).find("moon")!!
            assertEquals(Moon.isWaxing(jd), moon.waxing)
        }
    }

    @Test
    fun satellites_haveRangeSpeedAndTrack() {
        val snap = engine.compute(vancouver, t0)
        val iss = snap.find("sat:25544")!!
        assertTrue(iss.distanceKm!! > 400.0)
        assertEquals(7.66, iss.speedKmPerSec!!, 0.2)
        assertNotNull(iss.sunlit)
        // Track is only produced near or above the horizon.
        if (iss.horizontal.elevationDeg > -10.0) {
            assertEquals(31, iss.track.size)
        } else {
            assertTrue(iss.track.isEmpty())
        }
    }

    @Test
    fun satelliteTrack_isContinuous() {
        val prop = engine.propagators.first { it.tle.catalogNumber == "25544" }
        // Find an instant where the ISS is up, then check consecutive samples are close together.
        var t = t0
        var up: Horizontal? = null
        while (up == null && t < t0 + 86_400_000L) {
            val s = prop.propagateAt(t)
            val jd = JulianDate.fromEpochMillis(t)
            val h = Frames.lookAngles(Frames.eciToEcef(s.position, SiderealTime.gmstRadians(jd)), vancouver).first
            if (h.elevationDeg > 20.0) up = h else t += 60_000L
        }
        assertNotNull("ISS never above 20° in a day", up)
        val track = engine.satelliteTrack(prop, vancouver, t)
        assertEquals(31, track.size)
        for (i in 1 until track.size) {
            // ≈ 1°/s at zenith for the ISS, 10 s steps → never more than ~12° between samples.
            assertTrue(angularSeparationDegrees(track[i - 1], track[i]) < 12.0)
        }
    }

    @Test
    fun isSunlit_cylindricalShadow() {
        val sun = Vector3(Sun.AU_KM, 0.0, 0.0)
        assertTrue(SkyEngine.isSunlit(Vector3(7000.0, 0.0, 0.0), sun))
        assertFalse(SkyEngine.isSunlit(Vector3(-7000.0, 0.0, 0.0), sun))
        assertTrue(SkyEngine.isSunlit(Vector3(-7000.0, 7000.0, 0.0), sun))
        assertFalse(SkyEngine.isSunlit(Vector3(-7000.0, 6000.0, 0.0), sun))
    }

    @Test
    fun deepSpaceSatellites_areDroppedNotFatal() {
        val line1 = "1 41866U 16071A   26286.50000000  .00000100  00000+0  00000+0 0  9995"
        val line2 = "2 41866   0.0200  90.0000 0001000 180.0000 180.0000  1.00270000 36000"
        fun fix(line: String): String {
            var sum = 0
            for (c in line.substring(0, 68)) if (c.isDigit()) sum += c - '0' else if (c == '-') sum += 1
            return line.substring(0, 68) + (sum % 10)
        }
        val geo = Tle.parse("GOES", fix(line1), fix(line2))!!
        val e = SkyEngine(catalog, satellites + geo)
        assertEquals(3, e.satelliteCount)
    }
}

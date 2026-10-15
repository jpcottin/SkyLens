package com.jpcottin.skylens.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sun, Moon and planets against skyfield/DE421 at six instants and three sites. */
class BodiesTest {
    private fun check(name: String, expected: BodyFixtures.Body, actual: Horizontal, toleranceDeg: Double, site: BodyFixtures.Site) {
        val sep = angularSeparationDegrees(Horizontal(expected.azDeg, expected.elDeg), actual)
        assertTrue("$name at ${site.utc} ${site.observer}: off by %.4f° (expected az %.3f el %.3f, got az %.3f el %.3f)".format(
            sep, expected.azDeg, expected.elDeg, actual.azimuthDeg, actual.elevationDeg), sep < toleranceDeg)
    }

    @Test
    fun sun_withinTwoArcminutes() {
        for (site in BodyFixtures.sites) {
            val jd = JulianDate.fromEpochMillis(site.epochMillis)
            val h = Frames.geocentricToHorizontal(Sun.geocentricKm(jd), site.observer, jd)
            check("Sun", site.bodies.getValue("Sun"), h, 0.035, site)
            val eq = Sun.equatorial(jd)
            assertEquals(site.bodies.getValue("Sun").decDeg, eq.decDeg, 0.02)
            assertEquals(site.bodies.getValue("Sun").distAu, Sun.ecliptic(jd).distanceAu, 1e-4)
        }
    }

    @Test
    fun moon_withinATenthOfADegreeIncludingParallax() {
        for (site in BodyFixtures.sites) {
            val jd = JulianDate.fromEpochMillis(site.epochMillis)
            val h = Frames.geocentricToHorizontal(Moon.geocentricKm(jd), site.observer, jd)
            check("Moon", site.bodies.getValue("Moon"), h, 0.1, site)
            // Distance within 100 km of DE421.
            assertEquals(site.bodies.getValue("Moon").distAu * Sun.AU_KM, Moon.ecliptic(jd).distanceKm, 100.0)
        }
    }

    @Test
    fun moon_geocentricWithoutParallaxIsOffByUpToADegree() {
        // Documents why the engine subtracts the observer position: the geocentric place misses.
        val site = BodyFixtures.sites.first()
        val jd = JulianDate.fromEpochMillis(site.epochMillis)
        val geocentric = Frames.equatorialToHorizontal(Moon.equatorial(jd), site.observer, jd)
        val expected = site.bodies.getValue("Moon")
        val sep = angularSeparationDegrees(Horizontal(expected.azDeg, expected.elDeg), geocentric)
        assertTrue("parallax should be visible: $sep", sep > 0.3)
    }

    @Test
    fun planets_withinAFewArcminutes() {
        // Saturn's approximate elements are the loosest of the set: ≈ 0.16° at J2000 against DE421.
        val tolerances = mapOf(
            Planet.MERCURY to 0.15, Planet.VENUS to 0.1, Planet.MARS to 0.15,
            Planet.JUPITER to 0.1, Planet.SATURN to 0.2, Planet.URANUS to 0.1, Planet.NEPTUNE to 0.1,
        )
        for (site in BodyFixtures.sites) {
            val jd = JulianDate.fromEpochMillis(site.epochMillis)
            for (planet in Planet.entries) {
                val h = Frames.geocentricToHorizontal(Planets.geocentricKm(planet, jd), site.observer, jd)
                check(planet.displayName, site.bodies.getValue(planet.displayName), h, tolerances.getValue(planet), site)
            }
        }
    }

    @Test
    fun planetDistances_matchEphemeris() {
        for (site in BodyFixtures.sites) {
            val jd = JulianDate.fromEpochMillis(site.epochMillis)
            for (planet in Planet.entries) {
                val au = Planets.geocentricKm(planet, jd).length / Sun.AU_KM
                assertEquals(planet.displayName, site.bodies.getValue(planet.displayName).distAu, au, 0.01 * site.bodies.getValue(planet.displayName).distAu)
            }
        }
    }

    @Test
    fun moonPhase_isFullWhenOppositeTheSun() {
        // 2026-03-03 11:38 UTC is a full moon.
        val full = JulianDate.fromCalendar(2026, 3, 3.485)
        assertTrue(Moon.illuminatedFraction(full) > 0.99)
        // 2026-02-17 12:01 UTC is a new moon.
        val new = JulianDate.fromCalendar(2026, 2, 17.5)
        assertTrue(Moon.illuminatedFraction(new) < 0.01)
        assertTrue(Moon.isWaxing(JulianDate.fromCalendar(2026, 2, 24.0)))
        assertTrue(!Moon.isWaxing(JulianDate.fromCalendar(2026, 3, 10.0)))
    }

    @Test
    fun planetMagnitudes_areInTheRightBallpark() {
        val jd = JulianDate.fromEpochMillis(BodyFixtures.sites.first().epochMillis)
        assertTrue(Planets.approximateMagnitude(Planet.VENUS, jd) < -3.0)
        assertTrue(Planets.approximateMagnitude(Planet.JUPITER, jd) < -1.5)
        assertTrue(Planets.approximateMagnitude(Planet.NEPTUNE, jd) > 7.0)
    }
}

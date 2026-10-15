package com.jpcottin.skylens.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassPredictorTest {
    private val iss = Sgp4Fixtures.cases.first { it.name == "ISS (ZARYA)" }.let { Tle.parse(it.name, it.line1, it.line2)!! }
    private val vancouver = Observer(49.2827, -123.1207, 70.0)
    private val t0 = JulianDate.toEpochMillis(iss.epochJd)

    @Test
    fun iss_hasSeveralPassesADay() {
        val passes = PassPredictor().nextPasses(iss, vancouver, t0, hours = 24, minElevationDeg = 10.0)
        assertTrue("passes: ${passes.size}", passes.size in 3..8)
        for (p in passes) {
            assertTrue(p.riseMillis < p.culminationMillis)
            assertTrue(p.culminationMillis < p.setMillis)
            assertTrue(p.maxElevationDeg >= 10.0)
            assertTrue("duration ${p.durationSeconds}", p.durationSeconds in 120..720)
        }
        // Chronological and non-overlapping.
        for (i in 1 until passes.size) assertTrue(passes[i].riseMillis > passes[i - 1].setMillis)
    }

    @Test
    fun riseAndSet_areOnTheHorizonToWithinAStep() {
        val prop = Sgp4(iss)
        val pass = PassPredictor(refineToSeconds = 1).nextPasses(iss, vancouver, t0, hours = 24).first()
        fun el(t: Long): Double {
            val jd = JulianDate.fromEpochMillis(t)
            return Frames.lookAngles(Frames.eciToEcef(prop.propagateAt(t).position, SiderealTime.gmstRadians(jd)), vancouver).first.elevationDeg
        }
        assertTrue(el(pass.riseMillis) >= 0.0)
        assertTrue(el(pass.riseMillis - 2000) < 0.0)
        assertTrue(el(pass.setMillis) < 0.0)
        assertTrue(el(pass.setMillis - 2000) >= 0.0)
        assertTrue(el(pass.culminationMillis) > el(pass.riseMillis))
    }

    @Test
    fun minElevation_filtersLowPasses() {
        val all = PassPredictor().nextPasses(iss, vancouver, t0, hours = 24, minElevationDeg = 0.0)
        val high = PassPredictor().nextPasses(iss, vancouver, t0, hours = 24, minElevationDeg = 40.0)
        assertTrue(all.size >= high.size)
        assertTrue(high.all { it.maxElevationDeg >= 40.0 })
    }

    @Test
    fun maxPasses_capsTheResult() {
        val passes = PassPredictor().nextPasses(iss, vancouver, t0, hours = 48, minElevationDeg = 0.0, maxPasses = 2)
        assertEquals(2, passes.size)
    }

    @Test
    fun visibility_requiresSunlitSatelliteAndDarkObserver() {
        val passes = PassPredictor().nextPasses(iss, vancouver, t0, hours = 48, minElevationDeg = 0.0, maxPasses = 20)
        // Over two days there is at least one daytime pass (invisible) for a mid-latitude site.
        assertTrue(passes.any { !it.visible })
        for (p in passes) {
            val jd = JulianDate.fromEpochMillis(p.culminationMillis)
            val sunEl = Frames.geocentricToHorizontal(Sun.geocentricKm(jd), vancouver, jd).elevationDeg
            if (sunEl > -6.0) assertTrue("daytime pass flagged visible", !p.visible)
        }
    }
}

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
    fun durationMinutes_isTheNearestMinuteAndNeverZero() {
        fun pass(seconds: Long) = SatellitePass(iss, 0L, seconds * 500, seconds * 1000, 0.0, 45.0, 0.0, 0.0, visible = false)
        assertEquals(6L, pass(359).durationMinutes)   // 5 min 59 s reads "6 min", not "5 min"
        assertEquals(5L, pass(329).durationMinutes)
        assertEquals(1L, pass(50).durationMinutes)    // a sliver of a pass is still a minute, not "0 min"
        assertEquals(5L, pass(300).durationSeconds / 60)
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
    fun aPassThatBeginsInsideTheWindow_isFollowedToItsSet() {
        val whole = PassPredictor().nextPasses(iss, vancouver, t0, hours = 24, minElevationDeg = 0.0).first()
        // A one-hour window that closes at this pass's culmination: the pass has risen
        // inside it and sets after it.
        val from = whole.culminationMillis - 3_600_000L
        val cut = PassPredictor().nextPasses(iss, vancouver, from, hours = 1, minElevationDeg = 0.0)
        val same = cut.firstOrNull { kotlin.math.abs(it.riseMillis - whole.riseMillis) <= 2_000L }
        assertTrue("the pass rising at ${whole.riseMillis} is missing from $cut", same != null)
        assertTrue(kotlin.math.abs(same!!.setMillis - whole.setMillis) <= 2_000L)
        assertEquals(whole.maxElevationDeg, same.maxElevationDeg, 0.5)
    }

    @Test
    fun aPassUnderWayAtTheStart_isReportedWhole() {
        val whole = PassPredictor().nextPasses(iss, vancouver, t0, hours = 24, minElevationDeg = 10.0).first()
        // Start the search past the peak, the satellite still up and going down.
        val from = (whole.culminationMillis + whole.setMillis) / 2
        val joined = PassPredictor().nextPasses(iss, vancouver, from, hours = 1, minElevationDeg = 10.0).firstOrNull()
        assertTrue("the pass under way at $from is missing", joined != null)
        assertEquals(whole.riseMillis.toDouble(), joined!!.riseMillis.toDouble(), 2_000.0)
        assertEquals(whole.setMillis.toDouble(), joined.setMillis.toDouble(), 2_000.0)
        assertEquals(whole.maxElevationDeg, joined.maxElevationDeg, 0.5)
        assertEquals(whole.riseAzimuthDeg, joined.riseAzimuthDeg, 0.5)
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

package com.jpcottin.skylens.ui.sky

import com.jpcottin.skylens.astro.compassPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/** The printed azimuth runs 0 to 359 and agrees with the compass point beside it. */
class SkyFormatTest {
    @Test
    fun azimuth_neverPrints360() {
        assertEquals(0, SkyFormat.azimuth(359.6))
        assertEquals("N", compassPoint(359.6))
        assertEquals(360, SkyFormat.degrees(359.6))   // the plain rounding this replaces
    }

    @Test
    fun azimuth_roundsLikeAnyOtherAngleBelowTheWrap() {
        assertEquals(359, SkyFormat.azimuth(359.4))
        assertEquals(0, SkyFormat.azimuth(0.4))
        assertEquals(180, SkyFormat.azimuth(179.5))
    }

    @Test
    fun azimuth_wrapsAnUnnormalisedAngle() {
        assertEquals(350, SkyFormat.azimuth(-10.0))
        assertEquals(10, SkyFormat.azimuth(370.0))
    }
}

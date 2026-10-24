package com.jpcottin.skylens.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AnglesTest {
    @Test
    fun normalizeDegrees_wrapsNegativeAndLarge() {
        assertEquals(350.0, normalizeDegrees(-10.0), 1e-12)
        assertEquals(10.0, normalizeDegrees(370.0), 1e-12)
        assertEquals(0.0, normalizeDegrees(720.0), 1e-12)
    }

    @Test
    fun observerRoundedTo_snapsToTheGrid_soNearbyFixesShareAKey() {
        val a = Observer(49.2827, -123.1207, 71.0).roundedTo(0.05)
        val b = Observer(49.28271, -123.12069, 72.0).roundedTo(0.05)   // a few metres away
        assertEquals(49.30, a.latitudeDeg, 1e-9)
        assertEquals(-123.10, a.longitudeDeg, 1e-9)
        assertEquals(100.0, a.altitudeM, 1e-9)
        assertEquals(a, b)
        assertNotEquals(a, Observer(49.35, -123.1207).roundedTo(0.05))
    }
}

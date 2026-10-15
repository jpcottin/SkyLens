package com.jpcottin.skylens.astro

import org.junit.Assert.assertEquals
import org.junit.Test

class AnglesTest {
    @Test
    fun normalizeDegrees_wrapsNegativeAndLarge() {
        assertEquals(350.0, normalizeDegrees(-10.0), 1e-12)
        assertEquals(10.0, normalizeDegrees(370.0), 1e-12)
        assertEquals(0.0, normalizeDegrees(720.0), 1e-12)
    }
}

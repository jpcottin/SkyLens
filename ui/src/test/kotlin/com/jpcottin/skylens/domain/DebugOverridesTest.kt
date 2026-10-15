package com.jpcottin.skylens.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugOverridesTest {
    @Test
    fun parseLocation_acceptsTwoOrThreeFields() {
        val two = DebugOverrides.parseLocation("49.28,-123.12")!!
        assertEquals(49.28, two.latitudeDeg, 1e-9)
        assertEquals(0.0, two.altitudeM, 1e-9)
        val three = DebugOverrides.parseLocation(" 48.85 , 2.35 , 35 ")!!
        assertEquals(35.0, three.altitudeM, 1e-9)
    }

    @Test
    fun parseLocation_rejectsGarbageAndOutOfRange() {
        assertNull(DebugOverrides.parseLocation("north,west"))
        assertNull(DebugOverrides.parseLocation("95,0"))
        assertNull(DebugOverrides.parseLocation("0,181"))
        assertNull(DebugOverrides.parseLocation("49"))
    }

    @Test
    fun parsePose_defaultsRollToZero() {
        val pose = DebugOverrides.parsePose("180,45")!!
        assertEquals(180.0, pose.centre.azimuthDeg, 1e-6)
        assertEquals(45.0, pose.centre.elevationDeg, 1e-6)
        assertNotNull(DebugOverrides.parsePose("0,0,90"))
        assertNull(DebugOverrides.parsePose("abc"))
    }

    @Test
    fun empty_isInactive() {
        assertFalse(DebugOverrides().isActive)
        assertFalse(DebugOverrides.fromIntent(null).isActive)
        assertTrue(DebugOverrides(selectId = "moon").isActive)
        assertTrue(DebugOverrides(nightMode = false).isActive)
        assertTrue(DebugOverrides(startTab = "objects").isActive)
    }

    @Test
    fun tabs_areTheThreeKnownNames() {
        assertEquals(setOf("sky", "objects", "settings"), DebugOverrides.TABS)
    }

    @Test
    fun clock_isFrozenOnlyWithATime() {
        val frozen = DebugOverrides(timeMillis = 123L).clock()
        assertEquals(123L, frozen.nowMillis())
        val live = DebugOverrides().clock()
        assertEquals(System.currentTimeMillis().toDouble(), live.nowMillis().toDouble(), 5_000.0)
    }
}

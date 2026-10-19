package com.jpcottin.skylens.data

import android.hardware.SensorManager
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

/** The axis remap that keeps the pose attached to the screen as the display rotates. */
class OrientationRemapTest {
    @Test
    fun naturalOrientationKeepsTheSensorAxes() {
        assertEquals(SensorManager.AXIS_X to SensorManager.AXIS_Y, remapAxesFor(Surface.ROTATION_0))
    }

    @Test
    fun aQuarterTurnSwapsTheAxes() {
        assertEquals(SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X, remapAxesFor(Surface.ROTATION_90))
        assertEquals(SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X, remapAxesFor(Surface.ROTATION_270))
    }

    @Test
    fun upsideDownNegatesBothAxes() {
        assertEquals(SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y, remapAxesFor(Surface.ROTATION_180))
    }

    @Test
    fun anUnknownRotationFallsBackToTheNaturalOne() {
        assertEquals(remapAxesFor(Surface.ROTATION_0), remapAxesFor(42))
    }

    @Test
    fun everyRotationMapsOntoTwoDistinctAxes() {
        for (rotation in listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)) {
            val (x, y) = remapAxesFor(rotation)
            // AXIS_X/AXIS_Y are 1 and 2, their negatives 129 and 130: the pair must span two different axes.
            assertEquals("rotation $rotation", 2, setOf(x and 0x7F, y and 0x7F).size)
        }
    }
}

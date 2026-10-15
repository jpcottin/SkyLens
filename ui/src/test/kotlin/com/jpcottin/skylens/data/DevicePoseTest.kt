package com.jpcottin.skylens.data

import com.jpcottin.skylens.astro.Horizontal
import com.jpcottin.skylens.astro.angularSeparationDegrees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePoseTest {
    /** Identity rotation matrix: phone flat on a table, screen up, top edge north. */
    private val flat = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    @Test
    fun flatOnTable_cameraLooksDown_topEdgeNorth() {
        val pose = DevicePose.fromRotationMatrix(flat, 0.0)
        assertEquals(-90.0, pose.centre.elevationDeg, 1e-6)
        assertEquals(0.0, pose.up.x, 1e-6)
        assertEquals(1.0, pose.up.y, 1e-6)
    }

    @Test
    fun uprightFacingNorth() {
        // Rotate the flat phone 90° about the device X axis so the screen faces south and the
        // back camera looks north: device Y → world Z (up), device Z → world -Y.
        // Row-major R (device → world): world = R * device.
        val r = floatArrayOf(
            1f, 0f, 0f,
            0f, 0f, -1f,
            0f, 1f, 0f,
        )
        val pose = DevicePose.fromRotationMatrix(r, 0.0)
        assertEquals(0.0, pose.centre.azimuthDeg, 1e-6)
        assertEquals(0.0, pose.centre.elevationDeg, 1e-6)
        assertEquals(1.0, pose.up.z, 1e-6)
    }

    @Test
    fun declination_rotatesTowardTrueNorth() {
        val r = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        // Magnetic declination +15° (east): a sensor reading "magnetic north" is really 15° east of true north.
        val pose = DevicePose.fromRotationMatrix(r, 15.0)
        assertEquals(15.0, pose.centre.azimuthDeg, 1e-6)
        val west = DevicePose.fromRotationMatrix(r, -15.0)
        assertEquals(345.0, west.centre.azimuthDeg, 1e-6)
    }

    @Test
    fun fromAngles_roundTrips() {
        val pose = DevicePose.fromAngles(123.0, 45.0, 0.0)
        assertEquals(123.0, pose.centre.azimuthDeg, 1e-9)
        assertEquals(45.0, pose.centre.elevationDeg, 1e-9)
        val cam = pose.toCamera(60.0, 45.0)
        assertEquals(0.0, cam.rollDeg, 1e-9)
    }

    @Test
    fun smooth_movesPartWayAndStaysNormalised() {
        val a = DevicePose.fromAngles(0.0, 0.0)
        val b = DevicePose.fromAngles(40.0, 0.0)
        val mid = DevicePose.smooth(a, b, 0.5)
        assertEquals(20.0, mid.centre.azimuthDeg, 1e-6)
        assertEquals(1.0, mid.forward.length, 1e-9)
        assertEquals(1.0, mid.up.length, 1e-9)
        assertEquals(b, DevicePose.smooth(null, b, 0.5))
        assertTrue(angularSeparationDegrees(DevicePose.smooth(a, b, 1.0).centre, Horizontal(40.0, 0.0)) < 1e-6)
    }
}

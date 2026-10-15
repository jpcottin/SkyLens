package com.jpcottin.skylens.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyCameraTest {
    private val cam = SkyCamera.fromAngles(azimuthDeg = 90.0, elevationDeg = 30.0, rollDeg = 0.0, horizontalFovDeg = 60.0, verticalFovDeg = 45.0)

    @Test
    fun centre_projectsToOrigin() {
        val p = cam.project(Horizontal(90.0, 30.0))!!
        assertEquals(0.0, p.x, 1e-9)
        assertEquals(0.0, p.y, 1e-9)
        assertEquals(90.0, cam.centre.azimuthDeg, 1e-9)
        assertEquals(30.0, cam.centre.elevationDeg, 1e-9)
        assertEquals(0.0, cam.rollDeg, 1e-9)
    }

    @Test
    fun higherObjects_areUp_andEasternObjectsToTheRightWhenFacingNorth() {
        val north = SkyCamera.fromAngles(0.0, 20.0, 0.0, 60.0, 45.0)
        val higher = north.project(Horizontal(0.0, 30.0))!!
        assertTrue(higher.y > 0.0)
        assertEquals(0.0, higher.x, 1e-9)
        val east = north.project(Horizontal(15.0, 20.0))!!
        assertTrue(east.x > 0.0)
        val west = north.project(Horizontal(345.0, 20.0))!!
        assertTrue(west.x < 0.0)
    }

    @Test
    fun fovEdges_landOnPlusMinusOne() {
        val north = SkyCamera.fromAngles(0.0, 0.0, 0.0, 60.0, 45.0)
        val rightEdge = north.project(Horizontal(30.0, 0.0))!!
        assertEquals(1.0, rightEdge.x, 1e-9)
        val topEdge = north.project(Horizontal(0.0, 22.5))!!
        assertEquals(1.0, topEdge.y, 1e-9)
        assertTrue(north.project(Horizontal(40.0, 0.0))!!.x > 1.0)
        assertTrue(north.project(Horizontal(40.0, 0.0))!!.isOnScreen.not())
    }

    @Test
    fun behindTheCamera_isNull() {
        assertNull(cam.project(Horizontal(270.0, -30.0)))
        assertNotNull(cam.project(Horizontal(90.0, 85.0)))
    }

    @Test
    fun roll_rotatesTheImage() {
        val rolled = SkyCamera.fromAngles(0.0, 0.0, 90.0, 60.0, 60.0)
        // Top edge rolled 90° clockwise (pointing east): the screen's left edge now points at the
        // sky, so a higher object lands on the left, level with the centre.
        val higher = rolled.project(Horizontal(0.0, 10.0))!!
        assertTrue(higher.x < -0.1)
        assertEquals(0.0, higher.y, 1e-6)
        assertEquals(90.0, rolled.rollDeg, 1e-6)
    }

    @Test
    fun unproject_invertsProject() {
        for (az in listOf(70.0, 90.0, 110.0)) for (el in listOf(15.0, 30.0, 45.0)) {
            val h = Horizontal(az, el)
            val back = cam.unproject(cam.project(h)!!)
            assertTrue(angularSeparationDegrees(h, back) < 1e-6)
        }
    }

    @Test
    fun basisConstructor_matchesAndroidAxes() {
        // Phone held upright facing north: back camera looks north (+Y world), top of screen is up (+Z).
        val c = SkyCamera(forward = Vector3(0.0, 1.0, 0.0), up = Vector3(0.0, 0.0, 1.0), horizontalFovDeg = 60.0, verticalFovDeg = 45.0)
        assertEquals(0.0, c.centre.azimuthDeg, 1e-9)
        assertEquals(1.0, c.right.x, 1e-9) // right is east
        // A non-orthogonal "up" is re-orthogonalised.
        val skewed = SkyCamera(Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.5, 1.0), 60.0, 45.0)
        assertEquals(0.0, skewed.up dot skewed.forward, 1e-9)
    }

    @Test
    fun fov_fromSensorAndFocalLength() {
        // 4.8 mm wide sensor, 4.3 mm focal length → ≈ 58°.
        assertEquals(58.3, SkyCamera.fovDegrees(4.8, 4.3), 0.2)
    }

    @Test
    fun zenith_hasAStableUp() {
        val up = SkyCamera.fromAngles(45.0, 90.0, 0.0, 60.0, 45.0)
        assertNotNull(up.project(Horizontal(0.0, 80.0)))
        assertEquals(90.0, up.centre.elevationDeg, 1e-6)
    }
}

package com.jpcottin.skylens.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FovFitTest {
    @Test
    fun sameAspect_keepsSensorFov() {
        // 4:3 sensor (long 70°, short = 2·atan(0.75·tan 35°) ≈ 55.4°) on a 4:3 landscape view keeps both angles.
        val fov = FovFit.fit(70.0, 55.41, 800, 600)
        assertEquals(70.0, fov.horizontalDeg, 0.05)
        assertEquals(55.41, fov.verticalDeg, 0.05)
    }

    @Test
    fun tallPortraitView_cropsTheShortAxis() {
        // 20:9 phone in portrait: the sensor's long side maps to the height, the width is cropped.
        val fov = FovFit.fit(70.0, 55.9, 1080, 2400)
        assertEquals(70.0, fov.verticalDeg, 0.3)
        assertTrue("horizontal ${fov.horizontalDeg}", fov.horizontalDeg < 40.0)
    }

    @Test
    fun squareView_cropsTheLongAxis() {
        val fov = FovFit.fit(70.0, 55.9, 1000, 1000)
        assertEquals(55.9, fov.verticalDeg, 0.3)
        assertEquals(55.9, fov.horizontalDeg, 0.3)
    }

    @Test
    fun landscapeAndPortrait_areTransposed() {
        val landscape = FovFit.fit(70.0, 55.9, 2400, 1080)
        val portrait = FovFit.fit(70.0, 55.9, 1080, 2400)
        assertEquals(landscape.horizontalDeg, portrait.verticalDeg, 1e-9)
        assertEquals(landscape.verticalDeg, portrait.horizontalDeg, 1e-9)
    }

    @Test
    fun zeroSize_fallsBackToSensor() {
        assertEquals(FieldOfView(70.0, 55.9), FovFit.fit(70.0, 55.9, 0, 0))
    }

    @Test
    fun forView_keepsSquarePixels() {
        val fov = FovFit.forView(60.0, 1000, 2000)
        assertEquals(60.0, fov.horizontalDeg, 1e-9)
        // tan(v/2) = 2 tan(30°) → v ≈ 98.2°
        assertEquals(98.2, fov.verticalDeg, 0.1)
    }
}

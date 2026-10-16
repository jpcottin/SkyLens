package com.jpcottin.skylens.ui.glasses

import com.jpcottin.skylens.astro.BodyKind
import com.jpcottin.skylens.astro.Horizontal
import com.jpcottin.skylens.astro.PassPredictor
import com.jpcottin.skylens.ui.preview.PreviewSky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesModelTest {
    private val snapshot = PreviewSky.nightSnapshot

    @Test
    fun focus_prefersTheTarget() {
        val moon = snapshot.find("moon")!!
        val far = Horizontal(moon.horizontal.azimuthDeg + 120.0, 40.0)
        assertEquals("sat:25544", GlassesModel.focus(snapshot, far, "sat:25544")!!.obj.id)
    }

    @Test
    fun focus_picksTheNearestBodyWithinTenDegrees() {
        val saturn = snapshot.find("planet:saturn")!!
        val near = Horizontal(saturn.horizontal.azimuthDeg + 3.0, saturn.horizontal.elevationDeg - 2.0)
        assertEquals("planet:saturn", GlassesModel.focus(snapshot, near, null)!!.obj.id)
    }

    @Test
    fun focus_fallsBackToABrightNamedStar() {
        val aldebaran = snapshot.positions.first { it.obj.name == "Aldebaran" }
        // Make sure no planet, Moon or satellite sits within 10° of it in this snapshot.
        val bodiesNearby = snapshot.positions.filter { it.obj.kind != BodyKind.STAR && it.horizontal.elevationDeg > -5.0 }
            .any { com.jpcottin.skylens.astro.angularSeparationDegrees(it.horizontal, aldebaran.horizontal) <= 10.0 }
        if (!bodiesNearby) {
            val focus = GlassesModel.focus(snapshot, Horizontal(aldebaran.horizontal.azimuthDeg + 1.0, aldebaran.horizontal.elevationDeg), null)
            assertEquals("Aldebaran", focus!!.obj.name)
        }
    }

    @Test
    fun focus_isNullWhenNothingIsClose_orWithoutASnapshot() {
        // Straight down: nothing is drawn below the horizon except satellites more than 5° under it.
        assertNull(GlassesModel.focus(snapshot, Horizontal(0.0, -89.0), null))
        assertNull(GlassesModel.focus(null, Horizontal(0.0, 45.0), "moon"))
    }

    @Test
    fun countdown_formats() {
        val t = PreviewSky.NIGHT_MILLIS
        assertEquals("now", GlassesModel.countdown(t, t - 1_000))
        assertEquals("in 5 min", GlassesModel.countdown(t, t + 5 * 60_000L))
        assertEquals("in 2 h 07", GlassesModel.countdown(t, t + (127) * 60_000L))
    }

    @Test
    fun orderForLens_visibleFirstThenSoonest() {
        val passes = PreviewSky.tles.flatMap { PassPredictor().nextPasses(it, PreviewSky.vancouver, PreviewSky.NIGHT_MILLIS, hours = 48, minElevationDeg = 0.0, maxPasses = 4) }
        val ordered = GlassesModel.orderForLens(passes)
        assertEquals(passes.size, ordered.size)
        val firstInvisible = ordered.indexOfFirst { !it.visible }
        if (firstInvisible > 0) assertTrue(ordered.take(firstInvisible).all { it.visible })
        ordered.zipWithNext().forEach { (a, b) -> if (a.visible == b.visible) assertTrue(a.riseMillis <= b.riseMillis) }
    }
}

package com.jpcottin.skylens.domain

import com.jpcottin.skylens.astro.DEG_TO_RAD
import com.jpcottin.skylens.astro.RAD_TO_DEG
import kotlin.math.atan
import kotlin.math.tan

/** Horizontal and vertical field of view of what is actually visible on screen, in degrees. */
data class FieldOfView(val horizontalDeg: Double, val verticalDeg: Double) {
    companion object {
        /** A plausible phone wide camera, used until the real characteristics are known. */
        val DEFAULT = FieldOfView(horizontalDeg = 66.0, verticalDeg = 50.0)
    }
}

/**
 * Pure geometry for matching the camera's field of view to the viewfinder. The preview is
 * scaled to fill the view and centre-cropped, so the axis that gets cropped shows less sky than
 * the sensor sees.
 */
object FovFit {
    /**
     * @param sensorLongFovDeg field of view along the sensor's long side.
     * @param sensorShortFovDeg field of view along the sensor's short side.
     * @param viewWidthPx viewfinder width; the sensor's long side is aligned with the longer view side.
     */
    fun fit(sensorLongFovDeg: Double, sensorShortFovDeg: Double, viewWidthPx: Int, viewHeightPx: Int): FieldOfView {
        if (viewWidthPx <= 0 || viewHeightPx <= 0) return FieldOfView(sensorLongFovDeg, sensorShortFovDeg)
        val portrait = viewHeightPx >= viewWidthPx
        val longPx = if (portrait) viewHeightPx.toDouble() else viewWidthPx.toDouble()
        val shortPx = if (portrait) viewWidthPx.toDouble() else viewHeightPx.toDouble()
        val tanLong = tan(sensorLongFovDeg / 2 * DEG_TO_RAD)
        val tanShort = tan(sensorShortFovDeg / 2 * DEG_TO_RAD)
        // Fill-centre: the scale is set by the axis that needs the larger magnification; the other
        // axis is cropped, so its visible half-angle shrinks by the pixel ratio.
        val viewRatio = longPx / shortPx
        val sensorRatio = tanLong / tanShort
        val (visibleLong, visibleShort) = if (viewRatio >= sensorRatio) {
            // The view is more elongated than the sensor: the short axis is cropped.
            tanLong to tanLong / viewRatio
        } else {
            tanShort * viewRatio to tanShort
        }
        val longFov = 2 * atan(visibleLong) * RAD_TO_DEG
        val shortFov = 2 * atan(visibleShort) * RAD_TO_DEG
        return if (portrait) FieldOfView(shortFov, longFov) else FieldOfView(longFov, shortFov)
    }

    /** Field of view for a drawn (non-camera) sky that keeps square pixels on the given view. */
    fun forView(horizontalDeg: Double, viewWidthPx: Int, viewHeightPx: Int): FieldOfView {
        if (viewWidthPx <= 0 || viewHeightPx <= 0) return FieldOfView(horizontalDeg, horizontalDeg)
        val tanH = tan(horizontalDeg / 2 * DEG_TO_RAD)
        val tanV = tanH * viewHeightPx / viewWidthPx
        return FieldOfView(horizontalDeg, 2 * atan(tanV) * RAD_TO_DEG)
    }
}

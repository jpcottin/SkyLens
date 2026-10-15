package com.jpcottin.skylens.astro

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** A point on the viewfinder in normalised coordinates: x and y in [-1, 1] when on screen. */
data class ScreenPoint(val x: Double, val y: Double) {
    val isOnScreen: Boolean get() = x >= -1.0 && x <= 1.0 && y >= -1.0 && y <= 1.0
}

/**
 * Pinhole camera looking into the sky. The basis vectors are in the East-North-Up world frame,
 * which is exactly what Android's rotation-vector sensor delivers once the device axes are
 * mapped (forward = where the back camera points, up = the top edge of the screen).
 */
class SkyCamera(
    forward: Vector3,
    up: Vector3,
    val horizontalFovDeg: Double,
    val verticalFovDeg: Double,
) {
    val forward: Vector3 = forward.normalized()
    val right: Vector3 = (this.forward cross up).normalized()
    val up: Vector3 = (right cross this.forward).normalized()

    private val tanHalfH = tan(horizontalFovDeg / 2 * DEG_TO_RAD)
    private val tanHalfV = tan(verticalFovDeg / 2 * DEG_TO_RAD)

    /** Sky direction at the centre of the viewfinder. */
    val centre: Horizontal get() = Horizontal.fromUnitVector(forward)

    /** Roll of the screen's top edge relative to the sky's up, in degrees, clockwise positive. */
    val rollDeg: Double
        get() {
            val worldUp = Vector3(0.0, 0.0, 1.0)
            val skyUp = (worldUp - forward * (worldUp dot forward)).normalized()
            if (skyUp.length == 0.0) return 0.0
            val cosRoll = (up dot skyUp).coerceIn(-1.0, 1.0)
            val sinRoll = (skyUp cross up) dot forward
            return kotlin.math.atan2(sinRoll, cosRoll) * RAD_TO_DEG
        }

    /**
     * Projects a sky direction. Returns null when the direction is behind the camera; points
     * outside [-1, 1] are in front but off screen (useful for edge arrows).
     */
    fun project(direction: Horizontal): ScreenPoint? = project(direction.toUnitVector())

    fun project(d: Vector3): ScreenPoint? {
        val depth = d dot forward
        if (depth <= 1e-9) return null
        val x = (d dot right) / depth / tanHalfH
        val y = (d dot up) / depth / tanHalfV
        return ScreenPoint(x, y)
    }

    /** Inverse of [project]: the sky direction under a normalised screen point. */
    fun unproject(point: ScreenPoint): Horizontal {
        val d = forward + right * (point.x * tanHalfH) + up * (point.y * tanHalfV)
        return Horizontal.fromUnitVector(d)
    }

    companion object {
        /** Camera from a look direction and a roll angle (clockwise positive), for previews and tests. */
        fun fromAngles(azimuthDeg: Double, elevationDeg: Double, rollDeg: Double, horizontalFovDeg: Double, verticalFovDeg: Double): SkyCamera {
            val forward = Horizontal(azimuthDeg, elevationDeg).toUnitVector()
            val worldUp = Vector3(0.0, 0.0, 1.0)
            val skyUp = if (kotlin.math.abs(elevationDeg) >= 89.999) {
                Horizontal(azimuthDeg, 0.0).toUnitVector().let { if (elevationDeg > 0) -it else it }
            } else {
                (worldUp - forward * (worldUp dot forward)).normalized()
            }
            val right = (forward cross skyUp).normalized()
            val roll = rollDeg * DEG_TO_RAD
            // Positive roll turns the top of the screen clockwise as seen by the user.
            val up = skyUp * cos(roll) + right * sin(roll)
            return SkyCamera(forward, up, horizontalFovDeg, verticalFovDeg)
        }

        /**
         * Field of view from a sensor size and focal length, both in the same unit, as read from
         * Camera2 characteristics.
         */
        fun fovDegrees(sensorSize: Double, focalLength: Double): Double =
            2 * kotlin.math.atan(sensorSize / (2 * focalLength)) * RAD_TO_DEG
    }
}

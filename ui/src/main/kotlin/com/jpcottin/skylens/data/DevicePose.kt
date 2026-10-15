package com.jpcottin.skylens.data

import com.jpcottin.skylens.astro.DEG_TO_RAD
import com.jpcottin.skylens.astro.Horizontal
import com.jpcottin.skylens.astro.SkyCamera
import com.jpcottin.skylens.astro.Vector3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the device points, in the East-North-Up world frame: [forward] is the back camera's
 * optical axis, [up] the direction of the top edge of the screen.
 */
data class DevicePose(val forward: Vector3, val up: Vector3) {
    val centre: Horizontal get() = Horizontal.fromUnitVector(forward)

    fun toCamera(horizontalFovDeg: Double, verticalFovDeg: Double): SkyCamera = SkyCamera(forward, up, horizontalFovDeg, verticalFovDeg)

    companion object {
        /** Pose from a look direction and roll, for the map mode, previews and the debug extras. */
        fun fromAngles(azimuthDeg: Double, elevationDeg: Double, rollDeg: Double = 0.0): DevicePose {
            val cam = SkyCamera.fromAngles(azimuthDeg, elevationDeg, rollDeg, 60.0, 45.0)
            return DevicePose(cam.forward, cam.up)
        }

        /**
         * Pose from an Android rotation matrix (row-major 3×3, device → world, already remapped
         * for the display rotation) and the magnetic declination (east positive) that turns the
         * sensor's magnetic north into true north.
         */
        fun fromRotationMatrix(r: FloatArray, declinationDeg: Double): DevicePose {
            // Device -Z (out of the back of the phone) and +Y (top edge) in world coordinates.
            val forward = Vector3(-r[2].toDouble(), -r[5].toDouble(), -r[8].toDouble())
            val up = Vector3(r[1].toDouble(), r[4].toDouble(), r[7].toDouble())
            val d = declinationDeg * DEG_TO_RAD
            val c = cos(d)
            val s = sin(d)
            fun toTrueNorth(v: Vector3) = Vector3(v.x * c + v.y * s, -v.x * s + v.y * c, v.z)
            return DevicePose(toTrueNorth(forward).normalized(), toTrueNorth(up).normalized())
        }

        /** Exponential smoothing between two poses; `alpha` = 1 keeps only the new one. */
        fun smooth(previous: DevicePose?, next: DevicePose, alpha: Double): DevicePose {
            if (previous == null) return next
            val f = (previous.forward * (1 - alpha) + next.forward * alpha).normalized()
            val u = (previous.up * (1 - alpha) + next.up * alpha).normalized()
            return DevicePose(f, u)
        }
    }
}

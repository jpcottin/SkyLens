package com.jpcottin.skylens.astro

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

const val DEG_TO_RAD: Double = PI / 180.0
const val RAD_TO_DEG: Double = 180.0 / PI
const val TWO_PI: Double = 2.0 * PI

/** Normalises an angle in degrees to [0, 360). */
fun normalizeDegrees(deg: Double): Double {
    val r = deg % 360.0
    return if (r < 0) r + 360.0 else r
}

/** Normalises an angle in radians to [0, 2π). */
fun normalizeRadians(rad: Double): Double {
    val r = rad % TWO_PI
    return if (r < 0) r + TWO_PI else r
}

/** Signed difference `a - b` in degrees, wrapped to (-180, 180]. */
fun angularDifferenceDegrees(a: Double, b: Double): Double {
    var d = (a - b) % 360.0
    if (d <= -180.0) d += 360.0
    if (d > 180.0) d -= 360.0
    return d
}

/** Angle between two sky directions (degrees), the proper "distance on the sky". */
fun angularSeparationDegrees(a: Horizontal, b: Horizontal): Double {
    val va = a.toUnitVector()
    val vb = b.toUnitVector()
    val dot = (va dot vb).coerceIn(-1.0, 1.0)
    return kotlin.math.acos(dot) * RAD_TO_DEG
}

/** Immutable 3-vector with the few operations the engine needs. */
data class Vector3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vector3) = Vector3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vector3) = Vector3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vector3(x * k, y * k, z * k)
    operator fun unaryMinus() = Vector3(-x, -y, -z)
    infix fun dot(o: Vector3): Double = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vector3) = Vector3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Double get() = sqrt(x * x + y * y + z * z)
    fun normalized(): Vector3 {
        val l = length
        return if (l == 0.0) this else Vector3(x / l, y / l, z / l)
    }

    companion object {
        val ZERO = Vector3(0.0, 0.0, 0.0)
    }
}

/** A direction in the observer's sky: azimuth from north through east, elevation above the horizon. */
data class Horizontal(val azimuthDeg: Double, val elevationDeg: Double) {
    /** East-North-Up unit vector (the same world frame Android's rotation vector uses). */
    fun toUnitVector(): Vector3 {
        val az = azimuthDeg * DEG_TO_RAD
        val el = elevationDeg * DEG_TO_RAD
        return Vector3(cos(el) * sin(az), cos(el) * cos(az), sin(el))
    }

    companion object {
        fun fromUnitVector(v: Vector3): Horizontal {
            val n = v.normalized()
            val az = normalizeDegrees(atan2(n.x, n.y) * RAD_TO_DEG)
            val el = kotlin.math.asin(n.z.coerceIn(-1.0, 1.0)) * RAD_TO_DEG
            return Horizontal(az, el)
        }
    }
}

/** Equatorial coordinates, right ascension and declination in degrees. */
data class Equatorial(val raDeg: Double, val decDeg: Double) {
    val raHours: Double get() = raDeg / 15.0
}

/** Observer on the WGS84 ellipsoid. */
data class Observer(val latitudeDeg: Double, val longitudeDeg: Double, val altitudeM: Double = 0.0) {
    /**
     * This observer snapped to a grid of [stepDeg] degrees (and 100 m in altitude): a key for
     * work that depends on the location only coarsely, such as pass predictions, so a GPS fix
     * that moves by a few metres every ten seconds does not redo it.
     */
    fun roundedTo(stepDeg: Double): Observer = Observer(
        Math.round(latitudeDeg / stepDeg) * stepDeg,
        Math.round(longitudeDeg / stepDeg) * stepDeg,
        Math.round(altitudeM / 100.0) * 100.0,
    )
}

/** Compass point for an azimuth, e.g. "NNE". */
fun compassPoint(azimuthDeg: Double): String {
    val points = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    val idx = ((normalizeDegrees(azimuthDeg) + 11.25) / 22.5).toInt() % 16
    return points[idx]
}

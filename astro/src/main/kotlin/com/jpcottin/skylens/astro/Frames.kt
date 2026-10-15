package com.jpcottin.skylens.astro

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Reference-frame conversions: TEME/ECI ↔ ECEF ↔ local East-North-Up ↔ azimuth/elevation. */
object Frames {
    /** WGS84 semi-major axis in km. */
    const val EARTH_RADIUS_KM = 6378.137
    private const val WGS84_F = 1.0 / 298.257223563
    private const val WGS84_E2 = WGS84_F * (2 - WGS84_F)

    /** Observer position in the Earth-fixed frame (km). */
    fun observerEcef(o: Observer): Vector3 {
        val lat = o.latitudeDeg * DEG_TO_RAD
        val lon = o.longitudeDeg * DEG_TO_RAD
        val h = o.altitudeM / 1000.0
        val n = EARTH_RADIUS_KM / sqrt(1 - WGS84_E2 * sin(lat) * sin(lat))
        return Vector3(
            (n + h) * cos(lat) * cos(lon),
            (n + h) * cos(lat) * sin(lon),
            (n * (1 - WGS84_E2) + h) * sin(lat),
        )
    }

    /** Rotates an Earth-centred inertial vector (TEME or equatorial of date) into the Earth-fixed frame. */
    fun eciToEcef(eci: Vector3, gmstRad: Double): Vector3 {
        val c = cos(gmstRad)
        val s = sin(gmstRad)
        return Vector3(eci.x * c + eci.y * s, -eci.x * s + eci.y * c, eci.z)
    }

    /** Rotates an Earth-fixed vector into the inertial frame. */
    fun ecefToEci(ecef: Vector3, gmstRad: Double): Vector3 {
        val c = cos(gmstRad)
        val s = sin(gmstRad)
        return Vector3(ecef.x * c - ecef.y * s, ecef.x * s + ecef.y * c, ecef.z)
    }

    /** Observer position in the inertial frame at the given sidereal time (km). */
    fun observerEci(o: Observer, gmstRad: Double): Vector3 = ecefToEci(observerEcef(o), gmstRad)

    /** Expresses an ECEF displacement from the observer in the local East-North-Up frame. */
    fun ecefToEnu(delta: Vector3, o: Observer): Vector3 {
        val lat = o.latitudeDeg * DEG_TO_RAD
        val lon = o.longitudeDeg * DEG_TO_RAD
        val sLat = sin(lat)
        val cLat = cos(lat)
        val sLon = sin(lon)
        val cLon = cos(lon)
        return Vector3(
            -sLon * delta.x + cLon * delta.y,
            -sLat * cLon * delta.x - sLat * sLon * delta.y + cLat * delta.z,
            cLat * cLon * delta.x + cLat * sLon * delta.y + sLat * delta.z,
        )
    }

    /** Azimuth/elevation and range of an Earth-fixed target position as seen by the observer. */
    fun lookAngles(targetEcef: Vector3, o: Observer): Pair<Horizontal, Double> {
        val enu = ecefToEnu(targetEcef - observerEcef(o), o)
        return Horizontal.fromUnitVector(enu) to enu.length
    }

    /** Azimuth/elevation of a direction given in equatorial coordinates of date (no parallax). */
    fun equatorialToHorizontal(eq: Equatorial, o: Observer, jdUt1: Double): Horizontal {
        val lst = SiderealTime.lmstDegrees(jdUt1, o.longitudeDeg)
        val h = (lst - eq.raDeg) * DEG_TO_RAD
        val lat = o.latitudeDeg * DEG_TO_RAD
        val dec = eq.decDeg * DEG_TO_RAD
        val sinEl = sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(h)
        val el = asin(sinEl.coerceIn(-1.0, 1.0))
        val az = atan2(-sin(h) * cos(dec), sin(dec) * cos(lat) - cos(dec) * sin(lat) * cos(h))
        return Horizontal(normalizeDegrees(az * RAD_TO_DEG), el * RAD_TO_DEG)
    }

    /** Equatorial direction of an inertial position vector (equinox of the vector's frame). */
    fun vectorToEquatorial(v: Vector3): Equatorial {
        val ra = normalizeDegrees(atan2(v.y, v.x) * RAD_TO_DEG)
        val dec = asin((v.z / v.length).coerceIn(-1.0, 1.0)) * RAD_TO_DEG
        return Equatorial(ra, dec)
    }

    /** Unit vector for an equatorial direction. */
    fun equatorialToVector(eq: Equatorial, length: Double = 1.0): Vector3 {
        val ra = eq.raDeg * DEG_TO_RAD
        val dec = eq.decDeg * DEG_TO_RAD
        return Vector3(cos(dec) * cos(ra), cos(dec) * sin(ra), sin(dec)) * length
    }

    /**
     * Topocentric azimuth/elevation of a body given its geocentric inertial position (km, equatorial
     * of date): the observer's own position is subtracted, which is what makes the Moon's parallax
     * (up to a degree) come out right.
     */
    fun geocentricToHorizontal(geocentricEci: Vector3, o: Observer, jdUt1: Double): Horizontal {
        val gmst = SiderealTime.gmstRadians(jdUt1)
        val targetEcef = eciToEcef(geocentricEci, gmst)
        return lookAngles(targetEcef, o).first
    }
}

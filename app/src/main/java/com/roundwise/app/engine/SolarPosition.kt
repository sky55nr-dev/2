package com.roundwise.app.engine

import kotlin.math.*

/** Sun position as seen from the ground. Azimuth is clockwise from north. */
data class SunPosition(val azimuthDeg: Double, val elevationDeg: Double)

/**
 * Low-precision solar position (Astronomical Almanac algorithm, ~0.01° accuracy for 1950–2050),
 * accurate enough to decide whether a driver is facing a low sun.
 */
object SolarPosition {
    fun at(epochMillis: Long, where: LatLng): SunPosition {
        val jd = epochMillis / 86_400_000.0 + 2440587.5
        val n = jd - 2451545.0

        val meanLon = norm(280.460 + 0.9856474 * n)
        val meanAnomaly = Math.toRadians(norm(357.528 + 0.9856003 * n))
        val eclipticLon = Math.toRadians(
            meanLon + 1.915 * sin(meanAnomaly) + 0.020 * sin(2 * meanAnomaly)
        )
        val obliquity = Math.toRadians(23.439 - 0.0000004 * n)

        val rightAscension = atan2(cos(obliquity) * sin(eclipticLon), cos(eclipticLon))
        val declination = asin(sin(obliquity) * sin(eclipticLon))

        val gmstDeg = norm(280.46061837 + 360.98564736629 * n)
        val hourAngle = Math.toRadians(norm(gmstDeg + where.lon - Math.toDegrees(rightAscension)))

        val phi = Math.toRadians(where.lat)
        val elevation = asin(
            sin(phi) * sin(declination) + cos(phi) * cos(declination) * cos(hourAngle)
        )
        val azimuth = atan2(
            -sin(hourAngle) * cos(declination),
            sin(declination) * cos(phi) - cos(declination) * sin(phi) * cos(hourAngle)
        )
        return SunPosition(
            azimuthDeg = (Math.toDegrees(azimuth) + 360.0) % 360.0,
            elevationDeg = Math.toDegrees(elevation)
        )
    }

    private fun norm(deg: Double) = ((deg % 360.0) + 360.0) % 360.0
}

package com.roundwise.app.engine

import kotlin.math.*

data class LatLng(val lat: Double, val lon: Double)

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0088

    fun distanceKm(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
    }

    /** Initial bearing from [a] to [b], degrees clockwise from north in [0, 360). */
    fun bearingDeg(a: LatLng, b: LatLng): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Smallest absolute difference between two angles, in [0, 180]. */
    fun angleDiff(a: Double, b: Double): Double {
        val d = abs(((a - b) % 360.0 + 540.0) % 360.0 - 180.0)
        return d
    }

    fun lerp(a: LatLng, b: LatLng, t: Double) =
        LatLng(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)

    /** Splits a polyline into pieces no longer than [maxKm] so time-varying costs can be integrated. */
    fun densify(points: List<LatLng>, maxKm: Double = 0.8): List<LatLng> {
        if (points.size < 2) return points
        val out = ArrayList<LatLng>()
        out += points.first()
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val n = max(1, ceil(distanceKm(a, b) / maxKm).toInt())
            for (k in 1..n) out += lerp(a, b, k.toDouble() / n)
        }
        return out
    }

    fun polylineKm(points: List<LatLng>): Double =
        points.zipWithNext().sumOf { (a, b) -> distanceKm(a, b) }
}

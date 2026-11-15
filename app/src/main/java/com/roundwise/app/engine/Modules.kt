package com.roundwise.app.engine

import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

fun minutesBetween(from: ZonedDateTime, to: ZonedDateTime): Double =
    Duration.between(from, to).seconds / 60.0

fun ZonedDateTime.plusMinutesD(m: Double): ZonedDateTime = plusSeconds((m * 60).toLong())

private fun bump(x: Double, center: Double, width: Double) = max(0.0, 1.0 - abs(x - center) / width)

/**
 * Time-dependent traffic model: city-wide hourly profile × event surges around each hotspot.
 * Returns a travel-time multiplier (1.0 = free flow).
 */
class TrafficModel(private val weekend: Boolean, private val hotspots: List<CrowdHotspot>) {
    private val weekendProfile = doubleArrayOf(
        1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.05, 1.10, 1.15, 1.25, 1.35,
        1.40, 1.45, 1.45, 1.40, 1.45, 1.55, 1.55, 1.40, 1.30, 1.20, 1.10, 1.05,
    )
    private val weekdayProfile = doubleArrayOf(
        1.00, 1.00, 1.00, 1.00, 1.00, 1.05, 1.20, 1.55, 1.80, 1.50, 1.30, 1.30,
        1.30, 1.30, 1.30, 1.35, 1.50, 1.75, 1.90, 1.60, 1.35, 1.20, 1.10, 1.05,
    )

    fun multiplier(at: ZonedDateTime, where: LatLng, sensitivity: Double, endOverride: ZonedDateTime? = null): Double {
        val profile = if (weekend) weekendProfile else weekdayProfile
        val h = at.hour + at.minute / 60.0
        val i = h.toInt() % 24
        val base = profile[i] + (profile[(i + 1) % 24] - profile[i]) * (h - i)
        var surge = 0.0
        hotspots.forEachIndexed { idx, hs ->
            val end = if (idx == 0 && endOverride != null) endOverride else hs.end
            val proximity = (1.0 - Geo.distanceKm(where, hs.location) / 4.0).coerceIn(0.0, 1.0)
            if (proximity <= 0.0) return@forEachIndexed
            val scale = (hs.attendance / 23000.0).coerceIn(0.2, 2.0)
            val pre = bump(minutesBetween(hs.start, at), center = -25.0, width = 50.0) * 0.8
            val post = bump(minutesBetween(end, at), center = 25.0, width = 65.0) * 1.6
            surge += proximity * scale * (pre + post)
        }
        val raw = base * (1.0 + surge)
        return 1.0 + (raw - 1.0) * sensitivity
    }

    /**
     * CROWD AVOID — pedestrian & drop-off crowd intensity near event hotspots.
     * Peaks while people arrive (−60…0 min of start) and pour out (0…+45 min of end).
     */
    fun crowd(at: ZonedDateTime, where: LatLng, endOverride: ZonedDateTime? = null): Double {
        var c = 0.0
        hotspots.forEachIndexed { idx, hs ->
            val end = if (idx == 0 && endOverride != null) endOverride else hs.end
            val d = Geo.distanceKm(where, hs.location)
            val proximity = (1.0 - d / (hs.radiusKm * 1.6)).coerceIn(0.0, 1.0)
            if (proximity <= 0.0) return@forEachIndexed
            val scale = (hs.attendance / 23000.0).coerceIn(0.2, 1.5)
            val arriving = bump(minutesBetween(hs.start, at), center = -25.0, width = 40.0) * 0.7
            val leaving = bump(minutesBetween(end, at), center = 15.0, width = 40.0)
            c += proximity * scale * max(arriving, leaving)
        }
        return c
    }
}

/**
 * SUNBLIND — glare risk from driving heading vs. sun azimuth at the time each segment is driven.
 * Glare is considered when the sun is low (0° < elevation < 35°) and within ±40° of the heading.
 */
object SunBlind {
    const val MAX_ELEVATION = 35.0
    const val MAX_ANGLE = 40.0

    fun intensity(headingDeg: Double, sun: SunPosition): Double {
        val e = sun.elevationDeg
        if (e <= 0.0 || e >= MAX_ELEVATION) return 0.0
        val angle = Geo.angleDiff(headingDeg, sun.azimuthDeg)
        if (angle >= MAX_ANGLE) return 0.0
        val angular = 1.0 - angle / MAX_ANGLE
        // Very low sun near the horizon is often blocked by terrain/buildings; peak at 4–15°.
        val elevationFactor = when {
            e < 4.0 -> e / 4.0
            e <= 15.0 -> 1.0
            else -> 1.0 - (e - 15.0) / (MAX_ELEVATION - 15.0)
        }
        return (angular * elevationFactor).coerceIn(0.0, 1.0)
    }
}

/** Integrates drive time, traffic, glare and crowd exposure along a corridor for a given departure time. */
class LegSimulator(private val traffic: TrafficModel) {
    private val densified = HashMap<String, List<LatLng>>()

    /**
     * @param forward true = corridor order (home→venue / origin→destination), false = reversed.
     * @param snapshotAt if set, traffic is frozen at this moment (what a conventional navi "sees" at departure).
     */
    fun simulate(
        corridor: Corridor,
        depart: ZonedDateTime,
        forward: Boolean,
        endOverride: ZonedDateTime? = null,
        originOverride: LatLng? = null,
        snapshotAt: ZonedDateTime? = null,
    ): LegResult {
        val base = densified.getOrPut(corridor.id) { Geo.densify(corridor.points) }
        // Each step: (from, to, detour factor). The connector from the user's real location to the
        // corridor start is a straight line, so a 1.3 detour factor approximates the road distance.
        val steps = ArrayList<Triple<LatLng, LatLng, Double>>()
        if (originOverride != null && Geo.distanceKm(originOverride, base.first()) > 0.3) {
            Geo.densify(listOf(originOverride, base.first())).zipWithNext()
                .forEach { (a, b) -> steps += Triple(a, b, 1.3) }
        }
        base.zipWithNext().forEach { (a, b) -> steps += Triple(a, b, 1.0) }
        val ordered = if (forward) steps else steps.reversed().map { Triple(it.second, it.first, it.third) }

        var clock = depart
        var minutes = 0.0
        var km = 0.0
        var glareW = 0.0
        var glareExp = 0.0
        var crowdW = 0.0
        var crowdExp = 0.0
        val glareSegs = ArrayList<GlareSegment>()
        val crowdSegs = ArrayList<GlareSegment>()
        for ((a, b, detour) in ordered) {
            val segKm = Geo.distanceKm(a, b) * detour
            val mid = Geo.lerp(a, b, 0.5)
            val mult = traffic.multiplier(snapshotAt ?: clock, mid, corridor.congestionSensitivity, endOverride)
            val segMin = segKm / corridor.freeFlowKmh * 60.0 * mult
            val sun = SolarPosition.at(clock.toInstant().toEpochMilli(), mid)
            val g = SunBlind.intensity(Geo.bearingDeg(a, b), sun)
            glareW += segMin * g
            if (g >= 0.25) {
                glareExp += segMin
                glareSegs += GlareSegment(a, b, g)
            }
            val c = traffic.crowd(clock, mid, endOverride)
            crowdW += segMin * c
            if (c >= 0.15) {
                crowdExp += segMin
                crowdSegs += GlareSegment(a, b, c.coerceAtMost(1.0))
            }
            minutes += segMin
            km += segKm
            clock = clock.plusMinutesD(segMin)
        }
        return LegResult(
            corridor = corridor,
            depart = depart,
            driveMinutes = minutes,
            distanceKm = km,
            glareWeightedMin = glareW,
            glareExposedMin = glareExp,
            glareSegments = glareSegs,
            sunAtDepart = SolarPosition.at(depart.toInstant().toEpochMilli(), ordered.first().first),
            crowdWeightedMin = crowdW,
            crowdExposedMin = crowdExp,
            crowdSegments = crowdSegs,
        )
    }
}

/**
 * EXIT FIRST — parking entry/exit queue model.
 *
 * After the event ends, a share of the lot's cars head to the exits within ~15 minutes.
 * Each exit drains at lanes × 5 cars/min. The user reaches the car mid-way through that rush.
 * Waiting time = cars already queued at that moment / exit throughput (fluid queue).
 */
object ExitFirst {
    private const val OCCUPANCY = 0.88
    private const val PROMPT_SHARE = 0.65
    private const val RUSH_MINUTES = 15.0
    private const val CARS_PER_LANE_MIN = 5.0
    private const val USER_PERCENTILE = 0.5

    fun exitWaitMinutes(lot: ParkingLot, exit: ParkingExit): Double {
        val crowd = lot.capacity * OCCUPANCY * PROMPT_SHARE * exit.crowdShare
        val mu = exit.lanes * CARS_PER_LANE_MIN
        val t = USER_PERCENTILE * RUSH_MINUTES
        val arrived = crowd * (t / RUSH_MINUTES)
        val queue = max(0.0, arrived - mu * t)
        return queue / mu
    }

    fun entryWaitMinutes(lot: ParkingLot, arriveAtLot: ZonedDateTime, eventStart: ZonedDateTime): Double {
        val before = minutesBetween(arriveAtLot, eventStart)
        // Entry queue peaks ~20 min before start, fades out 70 min before.
        val f = when {
            before <= 0 -> 0.6
            before <= 20 -> 1.0
            before <= 70 -> 1.0 - (before - 20) / 50.0
            else -> 0.0
        }
        return lot.peakEntryWaitMin * f
    }

    fun isFullRisk(lot: ParkingLot, arriveAtLot: ZonedDateTime, eventStart: ZonedDateTime): Boolean {
        val full = lot.fullMinutesBeforeStart ?: return false
        return minutesBetween(arriveAtLot, eventStart) < full
    }

    fun feeKrw(lot: ParkingLot, parkedMinutes: Double): Int {
        val hours = ceil(max(0.0, parkedMinutes) / 60.0)
        return (hours * lot.feePerHourKrw).toInt()
    }
}

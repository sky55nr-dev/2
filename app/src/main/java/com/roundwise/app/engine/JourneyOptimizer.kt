package com.roundwise.app.engine

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * REVERSE ETA — the core engine. Treats the whole journey
 * (depart → drive → park → event → exit → drive home) as one optimisation problem:
 *
 *   J = T_drive(out+back) + T_walk·w + T_parkingQueue + λ1·R_glare + λ2·C_fee + λ_idle·T_idle (+ late penalty)
 *
 * and searches every combination of departure time × outbound corridor × parking lot × exit × return corridor.
 */
class JourneyOptimizer(
    private val scenario: Scenario,
    private val origin: LatLng? = null,
) {
    private val traffic = TrafficModel(scenario.weekend, scenario.hotspots)
    private val legs = LegSimulator(traffic)
    private val outCache = HashMap<String, LegResult>()
    private val inCache = HashMap<String, LegResult>()

    private data class Weights(
        val glare: Double, val fee: Double, val walk: Double, val idle: Double,
        val crowd: Double = 0.0, val distanceMode: Boolean = false,
    )

    fun analyze(prefs: Preferences, eventEnd: ZonedDateTime = scenario.eventEnd): AnalysisResult {
        val departures = if (prefs.flexibleDeparture) {
            (-60..30 step 10).map { scenario.plannedDeparture.plusMinutes(it.toLong()) }
        } else listOf(scenario.plannedDeparture)

        val candidates = enumerate(departures, eventEnd, prefs)
        val plannedOnly = if (prefs.flexibleDeparture) enumerate(listOf(scenario.plannedDeparture), eventEnd, prefs) else candidates

        val user = Weights(prefs.glareWeight, prefs.feeWeight, prefs.walkWeight, prefs.idleWeight, prefs.crowdWeight)
        val fast = Weights(glare = 0.0, fee = 0.1, walk = 1.0, idle = prefs.idleWeight)
        val sun = Weights(glare = 6.0, fee = 0.1, walk = 1.0, idle = prefs.idleWeight)
        val crowd = Weights(glare = 0.3, fee = 0.1, walk = 1.0, idle = prefs.idleWeight, crowd = 6.0)
        val shortest = Weights(0.0, 0.0, 0.0, 0.0, distanceMode = true)

        fun best(w: Weights, pool: List<JourneyPlan>) =
            pool.map { it.copy(cost = cost(it, w)) }.minBy { it.cost }

        val plans = linkedMapOf(
            Strategy.BASELINE to baseline(eventEnd, prefs),
            Strategy.SHORTEST to best(shortest, plannedOnly),
            Strategy.FAST_RETURN to best(fast, candidates),
            Strategy.SUN_AVOID to best(sun, candidates),
            Strategy.CROWD_AVOID to best(crowd, candidates),
            Strategy.ROUNDWISE to best(user, candidates),
        )
        return AnalysisResult(
            scenario = scenario,
            eventEnd = eventEnd,
            plans = plans,
            modules = moduleStatus(plans, candidates),
            evaluatedCombinations = candidates.size,
        )
    }

    /** Re-plans only the return part (car already parked) after the event end time changes. */
    fun replanReturn(current: JourneyPlan, newEventEnd: ZonedDateTime, prefs: Preferences): ReturnReplan {
        val w = Weights(prefs.glareWeight, prefs.feeWeight, prefs.walkWeight, prefs.idleWeight, prefs.crowdWeight)
        val options = scenario.corridors.flatMap { c ->
            current.lot.exits.map { exit -> completeReturn(current, newEventEnd, exit, c) }
        }
        val after = options.map { it.copy(cost = cost(it, w)) }.minBy { it.cost }
        val before = completeReturn(current, newEventEnd, current.exit, current.inbound.corridor)
        return ReturnReplan(
            newEventEnd = newEventEnd,
            before = before,
            after = after,
            changedExit = after.exit.id != current.exit.id,
            changedCorridor = after.inbound.corridor.id != current.inbound.corridor.id,
        )
    }

    // ---------------------------------------------------------------------------------------------

    private fun enumerate(departures: List<ZonedDateTime>, eventEnd: ZonedDateTime, prefs: Preferences): List<JourneyPlan> {
        val out = ArrayList<JourneyPlan>()
        for (dep in departures) for (oc in scenario.corridors) {
            val leg = outbound(oc, dep, eventEnd)
            for (lot in scenario.lots) {
                val arrival = arrivalPart(leg, lot, prefs)
                for (exit in lot.exits) for (ic in scenario.corridors) {
                    out += assemble(dep, leg, lot, arrival, eventEnd, exit, ic)
                }
            }
        }
        return out
    }

    private data class Arrival(val entryWait: Double, val fullRisk: Boolean, val arriveVenue: ZonedDateTime, val idle: Double, val late: Double)

    private fun arrivalPart(leg: LegResult, lot: ParkingLot, prefs: Preferences): Arrival {
        val atLot = leg.arrive
        val fullRisk = ExitFirst.isFullRisk(lot, atLot, scenario.eventStart)
        // A full lot means circling for another space (~12 min).
        val entry = ExitFirst.entryWaitMinutes(lot, atLot, scenario.eventStart) + if (fullRisk) 12.0 else 0.0
        val atVenue = atLot.plusMinutesD(entry + lot.walkMinutesToVenue)
        val target = scenario.eventStart.minusMinutes(prefs.arrivalBufferMin.toLong())
        val slack = minutesBetween(atVenue, target)
        return Arrival(entry, fullRisk, atVenue, idle = max(0.0, slack), late = max(0.0, -slack))
    }

    private fun assemble(
        dep: ZonedDateTime, leg: LegResult, lot: ParkingLot, a: Arrival,
        eventEnd: ZonedDateTime, exit: ParkingExit, ic: Corridor,
    ): JourneyPlan {
        val exitWait = ExitFirst.exitWaitMinutes(lot, exit)
        val access = exit.accessTo(ic.id)
        val walkOut = lot.walkMinutesToVenue
        val leaveLot = eventEnd.plusMinutesD(walkOut + exitWait)
        val back = inbound(ic, leaveLot.plusMinutesD(access), eventEnd)
        val parked = minutesBetween(leg.arrive.plusMinutesD(a.entryWait), leaveLot)
        return JourneyPlan(
            departure = dep, outbound = leg, lot = lot,
            entryWaitMin = a.entryWait, lotFullRisk = a.fullRisk, walkInMin = lot.walkMinutesToVenue,
            arriveAtVenue = a.arriveVenue, idleBeforeStartMin = a.idle, lateMin = a.late,
            eventEnd = eventEnd, walkOutMin = walkOut, exit = exit, exitWaitMin = exitWait, accessMin = access,
            inbound = back, feeKrw = ExitFirst.feeKrw(lot, parked), cost = 0.0,
        )
    }

    private fun completeReturn(p: JourneyPlan, newEnd: ZonedDateTime, exit: ParkingExit, ic: Corridor): JourneyPlan {
        val exitWait = ExitFirst.exitWaitMinutes(p.lot, exit)
        val access = exit.accessTo(ic.id)
        val leaveLot = newEnd.plusMinutesD(p.walkOutMin + exitWait)
        val back = inbound(ic, leaveLot.plusMinutesD(access), newEnd)
        val parked = minutesBetween(p.outbound.arrive.plusMinutesD(p.entryWaitMin), leaveLot)
        return p.copy(
            eventEnd = newEnd, exit = exit, exitWaitMin = exitWait, accessMin = access,
            inbound = back, feeKrw = ExitFirst.feeKrw(p.lot, parked),
        )
    }

    /** What a conventional navigation app does: fastest one-way route, closest lot, main gate, fastest way home. */
    private fun baseline(eventEnd: ZonedDateTime, prefs: Preferences): JourneyPlan {
        val dep = scenario.plannedDeparture
        val leg = scenario.corridors.map { outbound(it, dep, eventEnd) }.minBy { it.driveMinutes }
        val lot = scenario.lots.minBy { it.walkMinutesToVenue }
        val exit = lot.exits.maxBy { it.crowdShare }
        val arrival = arrivalPart(leg, lot, prefs)
        val options = scenario.corridors.map { assemble(dep, leg, lot, arrival, eventEnd, exit, it) }
        val chosen = options.minBy { it.inbound.driveMinutes + it.accessMin }
        return chosen.copy(cost = chosen.totalTravelMin)
    }

    private fun cost(p: JourneyPlan, w: Weights): Double {
        val latePenalty = if (p.lateMin > 0) 40.0 + p.lateMin * 8.0 else 0.0
        if (w.distanceMode) return p.totalDistanceKm * 100.0 + p.totalTravelMin * 0.01 + latePenalty
        return p.roundTripDriveMin + p.parkingQueueMin + p.walkTotalMin * w.walk +
            w.glare * p.glareWeightedMin + w.fee * p.feeKrw / 1000.0 +
            w.idle * p.idleBeforeStartMin + w.crowd * p.crowdWeightedMin + latePenalty
    }

    private fun outbound(c: Corridor, dep: ZonedDateTime, eventEnd: ZonedDateTime): LegResult =
        outCache.getOrPut("${c.id}@${dep.toEpochSecond()}@${eventEnd.toEpochSecond()}") {
            legs.simulate(c, dep, forward = true, endOverride = eventEnd, originOverride = origin)
        }

    private fun inbound(c: Corridor, dep: ZonedDateTime, eventEnd: ZonedDateTime): LegResult {
        val t = dep.truncatedTo(ChronoUnit.MINUTES)
        return inCache.getOrPut("${c.id}@${t.toEpochSecond()}@${eventEnd.toEpochSecond()}") {
            legs.simulate(c, t, forward = false, endOverride = eventEnd, originOverride = origin)
        }
    }

    private fun moduleStatus(plans: Map<Strategy, JourneyPlan>, all: List<JourneyPlan>): ModuleStatus {
        val rec = plans.getValue(Strategy.ROUNDWISE)
        val base = plans.getValue(Strategy.BASELINE)
        val saved = base.totalTravelMin - rec.totalTravelMin
        val reverseNote = "귀가 예정 ${hhmm(rec.homeArrival)} · 기존 내비 대비 " +
            if (saved >= 1) "${saved.toInt()}분 단축" else "차이 미미"

        val exitActive = rec.lot.id != base.lot.id || rec.exit.id != base.exit.id
        val exitNote = if (exitActive) {
            "${base.lot.name} 출차 대기 ${base.exitWaitMin.toInt()}분 → ${rec.lot.name}(${rec.exit.name}) ${rec.exitWaitMin.toInt()}분"
        } else "출차 혼잡 차이가 작아 가장 가까운 주차장 유지"

        val sunOut = rec.outbound.sunAtDepart.elevationDeg
        val sunBack = rec.inbound.sunAtDepart.elevationDeg
        val sunActive = rec.glareWeightedMin >= 0.5 || base.glareWeightedMin >= 0.5
        val minGlare = all.minBy { it.glareWeightedMin }
        val sunNote = when {
            sunActive && rec.glareWeightedMin + 0.5 < base.glareWeightedMin ->
                "역광 노출 ${base.glareLevel.label}(${base.glareExposedMin.toInt()}분) → ${rec.glareLevel.label}(${rec.glareExposedMin.toInt()}분)"
            sunActive -> "귀가 방향 역광 ${rec.glareLevel.label}(${rec.glareExposedMin.toInt()}분) · 최소 역광 경로(${minGlare.inbound.corridor.name})는 " +
                "+${(minGlare.totalTravelMin - rec.totalTravelMin).toInt()}분 → λ1을 올리면 선택"
            sunOut <= 0 && sunBack <= 0 -> "야간 이동 — 역광 영향 없음 (자동 비활성)"
            sunBack <= 0 -> "귀가 시 일몰 후 · 가는 길 역광 없음 (자동 비활성)"
            else -> "태양 고도·진행 방향상 역광 영향 미미 (자동 비활성)"
        }
        val crowdActive = rec.crowdWeightedMin + 0.5 < base.crowdWeightedMin
        val crowdNote = if (crowdActive) {
            "인파 밀집 구간 주행 ${base.crowdExposedMin.toInt()}분 → ${rec.crowdExposedMin.toInt()}분 (경기 전후 행사장 주변 회피)"
        } else "인파 밀집 구간 ${rec.crowdExposedMin.toInt()}분 · 출차 단축 효과가 더 커서 시간 우선 (λ3로 조절)"
        return ModuleStatus(true, reverseNote, exitActive, exitNote, sunActive, sunNote, crowdActive, crowdNote)
    }

    companion object {
        fun hhmm(t: ZonedDateTime): String = "%02d:%02d".format(t.hour, t.minute)
    }
}

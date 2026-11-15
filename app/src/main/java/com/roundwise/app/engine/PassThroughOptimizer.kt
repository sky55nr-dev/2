package com.roundwise.app.engine

import java.time.ZonedDateTime

/**
 * "야구 안 봐요, 그냥 지나가요" mode.
 *
 * A conventional navigation app picks the route that is fastest *with the traffic it sees at departure*.
 * If a game ends while you are on the way, the stadium area jams up just as you get there.
 * ROUNDWISE forecasts the event's arrival/exit waves along the whole drive and avoids them,
 * by re-routing or by shifting the departure time.
 */
class PassThroughOptimizer(private val scenario: PassScenario, private val origin: LatLng? = null) {
    private val traffic = TrafficModel(scenario.weekend, scenario.hotspots)
    private val legs = LegSimulator(traffic)

    fun analyze(prefs: Preferences): PassAnalysis {
        val planned = scenario.plannedDeparture
        val departures = if (prefs.flexibleDeparture) (-30..40 step 10).map { planned.plusMinutes(it.toLong()) } else listOf(planned)

        fun sim(c: Corridor, dep: ZonedDateTime) = legs.simulate(c, dep, forward = true, originOverride = origin)

        // What a conventional navi does: compare corridors using a traffic snapshot at departure time.
        val snapshotPick = scenario.corridors.minBy {
            legs.simulate(it, planned, forward = true, originOverride = origin, snapshotAt = planned).driveMinutes
        }
        val baselineLeg = sim(snapshotPick, planned)

        fun cost(l: LegResult, glare: Double, crowd: Double, shiftMin: Double) =
            l.driveMinutes + glare * l.glareWeightedMin + crowd * l.crowdWeightedMin + 0.15 * kotlin.math.abs(shiftMin)

        val plannedLegs = scenario.corridors.map { sim(it, planned) }
        val all = departures.flatMap { d -> scenario.corridors.map { c -> d to sim(c, d) } }

        val fastest = plannedLegs.minBy { it.driveMinutes }
        val crowdAvoid = plannedLegs.minBy { cost(it, 0.3, 6.0, 0.0) }
        val rec = all.minBy { (d, l) -> cost(l, prefs.glareWeight, prefs.crowdWeight * 2.0, minutesBetween(planned, d)) }

        val plans = linkedMapOf(
            PassStrategy.BASELINE to PassPlan(planned, baselineLeg, baselineLeg.driveMinutes),
            PassStrategy.FASTEST to PassPlan(planned, fastest, fastest.driveMinutes),
            PassStrategy.CROWD_AVOID to PassPlan(planned, crowdAvoid, cost(crowdAvoid, 0.3, 6.0, 0.0)),
            PassStrategy.ROUNDWISE to PassPlan(rec.first, rec.second, cost(rec.second, prefs.glareWeight, prefs.crowdWeight * 2.0, minutesBetween(planned, rec.first))),
        )

        val r = plans.getValue(PassStrategy.ROUNDWISE)
        val crowdActive = baselineLeg.crowdWeightedMin >= 1.0 || plannedLegs.any { it.crowdWeightedMin >= 1.0 }
        val hs = scenario.hotspots.first()
        val shift = minutesBetween(planned, r.departure).toInt()
        val note = buildString {
            if (crowdActive) {
                append("${hs.name} 행사(${JourneyOptimizer.hhmm(hs.start)}~${JourneyOptimizer.hhmm(hs.end)}) 인파·차량 집중 예측. ")
                append("기존 내비는 ${baselineLeg.corridor.name}(인파 구간 ${baselineLeg.crowdExposedMin.toInt()}분)을 선택하지만, ")
                append("ROUNDWISE는 ${r.leg.corridor.name}")
                if (shift != 0) append(" + ${if (shift > 0) "${shift}분 늦게" else "${-shift}분 일찍"} 출발")
                append("로 인파 구간 ${r.leg.crowdExposedMin.toInt()}분.")
            } else {
                append("경로 주변에 영향을 주는 행사가 없어 일반 최단경로와 동일합니다.")
            }
        }
        return PassAnalysis(scenario, plans, note, crowdActive)
    }
}

package com.roundwise.app.engine

import java.time.ZonedDateTime

/** Which Pleos NaviHelper route option best matches a corridor (mapped in the Pleos bridge). */
enum class NaviRouteHint { RECOMMENDED, SHORT_TIME, SHORT_DISTANCE, HIGHWAY, COMFORTABLE }

/**
 * A driving corridor between home and venue. [points] are ordered home -> venue;
 * the return trip uses the same corridor reversed.
 */
data class Corridor(
    val id: String,
    val name: String,
    val description: String,
    val points: List<LatLng>,
    /** Free-flow average speed on the corridor (km/h). */
    val freeFlowKmh: Double,
    /** How strongly the corridor reacts to congestion (1.0 = average). */
    val congestionSensitivity: Double,
    /** A point on the corridor sent to NaviHelper as a waypoint so the car follows this corridor. */
    val viaPoint: LatLng,
    val viaName: String,
    val naviHint: NaviRouteHint = NaviRouteHint.RECOMMENDED,
)

data class ParkingExit(
    val id: String,
    val name: String,
    val lanes: Int,
    /** Share of the lot's cars that will try this exit (sums to 1 per lot). */
    val crowdShare: Double,
    /** Minutes from the exit gate onto each corridor, keyed by corridor id. */
    val accessMinutes: Map<String, Double>,
    val defaultAccessMinutes: Double = 5.0,
) {
    fun accessTo(corridorId: String) = accessMinutes[corridorId] ?: defaultAccessMinutes
}

data class ParkingLot(
    val id: String,
    val name: String,
    val location: LatLng,
    val address: String,
    val walkMinutesToVenue: Double,
    val capacity: Int,
    val feePerHourKrw: Int,
    val exits: List<ParkingExit>,
    /** Entry queue minutes when arriving at the peak before the event starts. */
    val peakEntryWaitMin: Double,
    /** Minutes before event start after which the lot is likely full (null = never fills). */
    val fullMinutesBeforeStart: Int? = null,
)

data class Venue(val name: String, val location: LatLng)

/**
 * A place where people and cars gather around a scheduled event (stadium, concert hall, festival).
 * Feeds both the traffic surge model and the crowd exposure score (CROWD AVOID).
 * In production this would come from an event calendar + a real-time crowd source
 * (e.g. Seoul real-time city data), see [CrowdSource].
 */
data class CrowdHotspot(
    val name: String,
    val location: LatLng,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val attendance: Int,
    /** Radius (km) of the dense pedestrian/drop-off zone. */
    val radiusKm: Double = 1.2,
)

/** Pluggable crowd data provider. The demo uses scheduled-event estimates. */
fun interface CrowdSource {
    /** Crowd intensity 0..1+ at [where] and [at]. */
    fun intensity(at: ZonedDateTime, where: LatLng): Double
}

data class Place(val name: String, val location: LatLng, val address: String = "")

data class Scenario(
    val id: String,
    val title: String,
    val subtitle: String,
    val home: Place,
    val venue: Venue,
    val eventStart: ZonedDateTime,
    val eventEnd: ZonedDateTime,
    /** When the user would normally leave home. */
    val plannedDeparture: ZonedDateTime,
    val corridors: List<Corridor>,
    val lots: List<ParkingLot>,
    val weekend: Boolean,
    /** Other events happening at the same time (the scenario's own event is added automatically). */
    val otherHotspots: List<CrowdHotspot> = emptyList(),
    val attendance: Int = 23000,
) {
    /** The scenario's own event first, so delays can be applied to it. */
    val hotspots: List<CrowdHotspot>
        get() = listOf(CrowdHotspot(venue.name, venue.location, eventStart, eventEnd, attendance)) + otherHotspots
}

/** "I'm not going to the game — just get me past it": a trip whose route may cross an event area. */
data class PassScenario(
    val id: String,
    val title: String,
    val subtitle: String,
    val origin: Place,
    val destination: Place,
    val plannedDeparture: ZonedDateTime,
    /** Corridors ordered origin -> destination. */
    val corridors: List<Corridor>,
    val hotspots: List<CrowdHotspot>,
    val weekend: Boolean,
)

/** User preferences (λ weights in the journey cost J). */
data class Preferences(
    /** λ1: cost minutes per weighted minute of glare exposure. */
    val glareWeight: Double = 1.5,
    /** λ2: cost minutes per 1,000 KRW of parking fee. */
    val feeWeight: Double = 0.5,
    /** Multiplier on walking minutes (higher = walking feels worse). */
    val walkWeight: Double = 1.0,
    /** Cost per minute of waiting at the venue before the event starts. */
    val idleWeight: Double = 0.25,
    /** Let ROUNDWISE move the departure time. */
    val flexibleDeparture: Boolean = true,
    /** Minutes of buffer the user wants before the event starts. */
    val arrivalBufferMin: Int = 15,
    /** λ3: cost minutes per weighted minute driven through event crowds. */
    val crowdWeight: Double = 1.0,
)

data class GlareSegment(val from: LatLng, val to: LatLng, val intensity: Double)

data class LegResult(
    val corridor: Corridor,
    val depart: ZonedDateTime,
    val driveMinutes: Double,
    val distanceKm: Double,
    /** Σ (segment minutes × glare intensity). */
    val glareWeightedMin: Double,
    /** Minutes driven with noticeable glare (intensity ≥ 0.25). */
    val glareExposedMin: Double,
    val glareSegments: List<GlareSegment>,
    val sunAtDepart: SunPosition,
    /** Σ (segment minutes × crowd intensity). */
    val crowdWeightedMin: Double = 0.0,
    /** Minutes driven through dense event crowds (intensity ≥ 0.15). */
    val crowdExposedMin: Double = 0.0,
    val crowdSegments: List<GlareSegment> = emptyList(),
) {
    val arrive: ZonedDateTime get() = depart.plusSeconds((driveMinutes * 60).toLong())
}

enum class GlareLevel(val label: String) { NONE("없음"), LOW("낮음"), MEDIUM("보통"), HIGH("높음") }

fun glareLevelOf(weightedMin: Double) = when {
    weightedMin < 0.5 -> GlareLevel.NONE
    weightedMin < 2.5 -> GlareLevel.LOW
    weightedMin < 6.0 -> GlareLevel.MEDIUM
    else -> GlareLevel.HIGH
}

data class JourneyPlan(
    val departure: ZonedDateTime,
    val outbound: LegResult,
    val lot: ParkingLot,
    val entryWaitMin: Double,
    val lotFullRisk: Boolean,
    val walkInMin: Double,
    val arriveAtVenue: ZonedDateTime,
    val idleBeforeStartMin: Double,
    val lateMin: Double,
    val eventEnd: ZonedDateTime,
    val walkOutMin: Double,
    val exit: ParkingExit,
    val exitWaitMin: Double,
    val accessMin: Double,
    val inbound: LegResult,
    val feeKrw: Int,
    val cost: Double,
) {
    val roundTripDriveMin get() = outbound.driveMinutes + inbound.driveMinutes + accessMin
    val walkTotalMin get() = walkInMin + walkOutMin
    val parkingQueueMin get() = entryWaitMin + exitWaitMin
    /** Total travel time of the whole journey excluding the event itself and voluntary idle time. */
    val totalTravelMin get() = roundTripDriveMin + walkTotalMin + parkingQueueMin
    val homeArrival: ZonedDateTime get() = inbound.arrive
    val glareWeightedMin get() = outbound.glareWeightedMin + inbound.glareWeightedMin
    val glareExposedMin get() = outbound.glareExposedMin + inbound.glareExposedMin
    val glareLevel get() = glareLevelOf(glareWeightedMin)
    val totalDistanceKm get() = outbound.distanceKm + inbound.distanceKm
    val crowdWeightedMin get() = outbound.crowdWeightedMin + inbound.crowdWeightedMin
    val crowdExposedMin get() = outbound.crowdExposedMin + inbound.crowdExposedMin
    val crowdLevel get() = glareLevelOf(crowdWeightedMin)
}

/** Which optimisation modules actually influenced the plan (context-adaptive activation). */
data class ModuleStatus(
    val reverseEtaActive: Boolean,
    val reverseEtaNote: String,
    val exitFirstActive: Boolean,
    val exitFirstNote: String,
    val sunblindActive: Boolean,
    val sunblindNote: String,
    val crowdActive: Boolean = false,
    val crowdNote: String = "",
)

enum class Strategy(val label: String, val description: String) {
    BASELINE("기존 내비", "편도 최단시간 경로 + 가장 가까운 주차장"),
    SHORTEST("최단거리 중심", "왕복 주행거리를 최소화"),
    FAST_RETURN("빠른 귀가 중심", "왕복 전체 소요시간을 최소화"),
    SUN_AVOID("역광 회피 중심", "왕복 역광 노출을 최소화"),
    CROWD_AVOID("인파 회피 중심", "행사 인파·차량이 몰리는 구간을 최대한 회피"),
    ROUNDWISE("ROUNDWISE 추천", "사용자 선호(λ)를 반영한 전체 여정 비용 J 최소화"),
}

data class AnalysisResult(
    val scenario: Scenario,
    val eventEnd: ZonedDateTime,
    val plans: Map<Strategy, JourneyPlan>,
    val modules: ModuleStatus,
    val evaluatedCombinations: Int,
) {
    val recommended get() = plans.getValue(Strategy.ROUNDWISE)
    val baseline get() = plans.getValue(Strategy.BASELINE)
}

enum class PassStrategy(val label: String, val description: String) {
    BASELINE("기존 내비", "출발 시점 교통 상황 기준 최단시간 경로"),
    FASTEST("예측 최단시간", "도착 시점까지의 교통 변화를 예측한 최단시간"),
    CROWD_AVOID("인파 회피 중심", "행사장 주변 인파·차량 집중 구간 최대 회피"),
    ROUNDWISE("ROUNDWISE 추천", "출발 시각 조정 + 인파·역광·시간 종합 최적"),
}

data class PassPlan(val departure: ZonedDateTime, val leg: LegResult, val cost: Double) {
    val arrival: ZonedDateTime get() = leg.arrive
}

data class PassAnalysis(
    val scenario: PassScenario,
    val plans: Map<PassStrategy, PassPlan>,
    val note: String,
    val crowdActive: Boolean,
) {
    val recommended get() = plans.getValue(PassStrategy.ROUNDWISE)
    val baseline get() = plans.getValue(PassStrategy.BASELINE)
}

/** Result of re-planning the return trip once the car is already parked. */
data class ReturnReplan(
    val newEventEnd: ZonedDateTime,
    val before: JourneyPlan,
    val after: JourneyPlan,
    val changedExit: Boolean,
    val changedCorridor: Boolean,
)

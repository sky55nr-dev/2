package com.roundwise.app.engine

import com.roundwise.app.data.Scenarios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class EngineTest {
    private val seoul = LatLng(37.5665, 126.9780)

    @Test
    fun solarNoonInSummerIsHighAndSouth() {
        val t = ZonedDateTime.of(2026, 6, 21, 12, 30, 0, 0, Scenarios.SEOUL)
        val s = SolarPosition.at(t.toInstant().toEpochMilli(), seoul)
        assertTrue("elevation ${s.elevationDeg}", s.elevationDeg in 74.0..78.0)
        assertTrue("azimuth ${s.azimuthDeg}", s.azimuthDeg in 165.0..200.0)
    }

    @Test
    fun summerEveningSunIsLowInTheWest() {
        val t = ZonedDateTime.of(2026, 7, 18, 18, 30, 0, 0, Scenarios.SEOUL)
        val s = SolarPosition.at(t.toInstant().toEpochMilli(), seoul)
        assertTrue("elevation ${s.elevationDeg}", s.elevationDeg in 10.0..30.0)
        assertTrue("azimuth ${s.azimuthDeg}", s.azimuthDeg in 280.0..300.0)
    }

    @Test
    fun sunIsBelowHorizonOnWinterNight() {
        val t = ZonedDateTime.of(2026, 12, 19, 21, 0, 0, 0, Scenarios.SEOUL)
        assertTrue(SolarPosition.at(t.toInstant().toEpochMilli(), seoul).elevationDeg < 0)
    }

    @Test
    fun glareOnlyWhenFacingLowSun() {
        val lowWest = SunPosition(azimuthDeg = 285.0, elevationDeg = 12.0)
        assertTrue(SunBlind.intensity(280.0, lowWest) > 0.8)
        assertEquals(0.0, SunBlind.intensity(100.0, lowWest), 1e-9)
        assertEquals(0.0, SunBlind.intensity(285.0, SunPosition(285.0, 60.0)), 1e-9)
    }

    @Test
    fun bearingAndAngleHelpers() {
        assertEquals(90.0, Geo.bearingDeg(LatLng(37.0, 127.0), LatLng(37.0, 127.1)), 0.5)
        assertEquals(20.0, Geo.angleDiff(350.0, 10.0), 1e-9)
    }

    @Test
    fun mainLotExitQueueIsLongerThanSatelliteLots() {
        val lots = Scenarios.summerBaseball.lots.associateBy { it.id }
        val main = ExitFirst.exitWaitMinutes(lots.getValue("main"), lots.getValue("main").exits.first())
        val tan = ExitFirst.exitWaitMinutes(lots.getValue("tancheon"), lots.getValue("tancheon").exits.first())
        assertTrue("main $main tancheon $tan", main > tan + 10)
    }

    @Test
    fun roundwiseBeatsBaselineOnEveryScenario() {
        for (s in Scenarios.all) {
            val r = JourneyOptimizer(s).analyze(Preferences())
            println(describe(r))
            assertTrue(r.recommended.totalTravelMin <= r.baseline.totalTravelMin + 0.01)
        }
    }

    @Test
    fun sunblindDeactivatesAtNight() {
        val r = JourneyOptimizer(Scenarios.winterConcert).analyze(Preferences())
        assertTrue(!r.modules.sunblindActive)
    }

    @Test
    fun replanAfterDelayMovesHomeArrival() {
        val opt = JourneyOptimizer(Scenarios.summerBaseball)
        val prefs = Preferences()
        val r = opt.analyze(prefs)
        val re = opt.replanReturn(r.recommended, r.eventEnd.plusMinutes(25), prefs)
        println("replan: before ${hhmm(re.before.homeArrival)} after ${hhmm(re.after.homeArrival)} via ${re.after.inbound.corridor.name}/${re.after.exit.name}")
        assertTrue(re.after.homeArrival.isAfter(r.recommended.homeArrival))
    }

    @Test
    fun passThroughAvoidsStadiumCrowd() {
        val r = PassThroughOptimizer(Scenarios.passBaseball).analyze(Preferences())
        r.plans.forEach { (k, p) ->
            println("%-12s dep %s %-22s %5.1fm crowd %4.1f(%4.1fm) glare %4.1f arrive %s".format(
                k.label, hhmm(p.departure), p.leg.corridor.name, p.leg.driveMinutes,
                p.leg.crowdWeightedMin, p.leg.crowdExposedMin, p.leg.glareWeightedMin, hhmm(p.arrival)))
        }
        println(r.note)
        assertTrue(r.crowdActive)
        assertTrue(r.recommended.leg.crowdWeightedMin < r.baseline.leg.crowdWeightedMin)
    }

    private fun hhmm(t: ZonedDateTime) = JourneyOptimizer.hhmm(t)

    private fun describe(r: AnalysisResult) = buildString {
        appendLine("=== ${r.scenario.title} (${r.evaluatedCombinations} combos)")
        r.plans.forEach { (k, p) ->
            appendLine(
                "%-14s dep %s | out %-10s %5.1fm | %-12s %-14s in %4.1f exit %4.1f | back %-10s %5.1fm | total %5.1f | glare %4.1f(%s) | km %5.1f | fee %d | home %s".format(
                    k.label, hhmm(p.departure), p.outbound.corridor.name, p.outbound.driveMinutes,
                    p.lot.name, p.exit.name, p.entryWaitMin, p.exitWaitMin, p.inbound.corridor.name,
                    p.inbound.driveMinutes, p.totalTravelMin, p.glareWeightedMin, p.glareLevel.label,
                    p.totalDistanceKm, p.feeKrw, hhmm(p.homeArrival),
                )
            )
        }
        appendLine("modules: ${r.modules}")
    }
}

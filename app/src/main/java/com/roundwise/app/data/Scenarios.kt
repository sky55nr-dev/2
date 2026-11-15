package com.roundwise.app.data

import com.roundwise.app.engine.*
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Demo scenarios. Coordinates are approximate and traffic/parking parameters are
 * illustrative values for demonstrating the planner — not measured data.
 */
object Scenarios {
    val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

    private val home = Place("집 · 상암동", LatLng(37.5779, 126.8920), "서울 마포구 상암동")
    private val jamsilBallpark = Venue("잠실야구장", LatLng(37.5122, 127.0719))
    private val jamsilStadium = Venue("잠실 주경기장", LatLng(37.5157, 127.0731))

    /** Corridors ordered home -> Jamsil. */
    private val corridors = listOf(
        Corridor(
            id = "olympic",
            name = "올림픽대로",
            description = "한강 남단 · 가장 빠른 경로",
            points = listOf(
                LatLng(37.5779, 126.8920), LatLng(37.5560, 126.8880), LatLng(37.5420, 126.8920),
                LatLng(37.5330, 126.9050), LatLng(37.5270, 126.9300), LatLng(37.5180, 126.9600),
                LatLng(37.5100, 126.9950), LatLng(37.5180, 127.0200), LatLng(37.5240, 127.0500),
                LatLng(37.5160, 127.0640), LatLng(37.5122, 127.0719),
            ),
            freeFlowKmh = 64.0, congestionSensitivity = 1.0,
            viaPoint = LatLng(37.5100, 126.9950), viaName = "반포 (올림픽대로)",
            naviHint = NaviRouteHint.SHORT_TIME,
        ),
        Corridor(
            id = "gangbyeon",
            name = "강변북로",
            description = "한강 북단 · 잠실대교 진입",
            points = listOf(
                LatLng(37.5779, 126.8920), LatLng(37.5560, 126.9050), LatLng(37.5450, 126.9300),
                LatLng(37.5390, 126.9500), LatLng(37.5280, 126.9800), LatLng(37.5260, 127.0000),
                LatLng(37.5370, 127.0300), LatLng(37.5420, 127.0600), LatLng(37.5300, 127.0880),
                LatLng(37.5180, 127.0860), LatLng(37.5122, 127.0719),
            ),
            freeFlowKmh = 60.0, congestionSensitivity = 1.1,
            viaPoint = LatLng(37.5280, 126.9800), viaName = "이촌 (강변북로)",
            naviHint = NaviRouteHint.HIGHWAY,
        ),
        Corridor(
            id = "southern",
            name = "서부간선·남부순환",
            description = "남서쪽 우회 · 귀가 시 서쪽 저녁 해를 등지거나 비껴 감",
            points = listOf(
                LatLng(37.5779, 126.8920), LatLng(37.5450, 126.8800), LatLng(37.5150, 126.8820),
                LatLng(37.4850, 126.8850), LatLng(37.4780, 126.9150), LatLng(37.4770, 126.9500),
                LatLng(37.4790, 126.9850), LatLng(37.4850, 127.0300), LatLng(37.4950, 127.0550),
                LatLng(37.5050, 127.0650), LatLng(37.5122, 127.0719),
            ),
            freeFlowKmh = 52.0, congestionSensitivity = 0.85,
            viaPoint = LatLng(37.4780, 126.9500), viaName = "사당 (남부순환로)",
            naviHint = NaviRouteHint.COMFORTABLE,
        ),
    )

    private val lots = listOf(
        ParkingLot(
            id = "main",
            name = "종합운동장 주차장",
            location = LatLng(37.5148, 127.0735),
            address = "서울 송파구 올림픽로 25",
            walkMinutesToVenue = 3.0,
            capacity = 1500,
            feePerHourKrw = 2400,
            peakEntryWaitMin = 14.0,
            fullMinutesBeforeStart = 30,
            exits = listOf(
                ParkingExit("main-north", "정문(올림픽로)", lanes = 2, crowdShare = 0.75,
                    accessMinutes = mapOf("olympic" to 4.0, "gangbyeon" to 4.0, "southern" to 5.0)),
                ParkingExit("main-east", "동문(탄천동로)", lanes = 1, crowdShare = 0.25,
                    accessMinutes = mapOf("olympic" to 3.0, "gangbyeon" to 7.0, "southern" to 4.0)),
            ),
        ),
        ParkingLot(
            id = "tancheon",
            name = "탄천 공영주차장",
            location = LatLng(37.5108, 127.0660),
            address = "서울 강남구 대치동 탄천주차장",
            walkMinutesToVenue = 9.0,
            capacity = 420,
            feePerHourKrw = 1800,
            peakEntryWaitMin = 4.0,
            exits = listOf(
                ParkingExit("tan-ramp", "올림픽대로 직결 램프", lanes = 2, crowdShare = 1.0,
                    accessMinutes = mapOf("olympic" to 1.0, "gangbyeon" to 7.0, "southern" to 3.0)),
            ),
        ),
        ParkingLot(
            id = "sinchon",
            name = "잠실새내역 민영주차장",
            location = LatLng(37.5116, 127.0858),
            address = "서울 송파구 잠실동 잠실새내역 인근",
            walkMinutesToVenue = 7.0,
            capacity = 260,
            feePerHourKrw = 3000,
            peakEntryWaitMin = 5.0,
            exits = listOf(
                ParkingExit("sin-bridge", "잠실대교 방면 출구", lanes = 1, crowdShare = 1.0,
                    accessMinutes = mapOf("olympic" to 6.0, "gangbyeon" to 1.5, "southern" to 6.0)),
            ),
        ),
    )

    private fun t(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, SEOUL)

    val summerBaseball = Scenario(
        id = "baseball-summer",
        title = "주말 야구 · 잠실야구장",
        subtitle = "7월 토요일 15:00 경기 · 18:00 전후 종료 예상",
        home = home,
        venue = jamsilBallpark,
        eventStart = t(2026, 7, 18, 15, 0),
        eventEnd = t(2026, 7, 18, 18, 0),
        plannedDeparture = t(2026, 7, 18, 13, 30),
        corridors = corridors,
        lots = lots,
        weekend = true,
    )

    val autumnEvening = Scenario(
        id = "baseball-autumn",
        title = "가을 야구 · 잠실야구장",
        subtitle = "10월 토요일 14:00 경기 · 17:10 종료 예상 (석양 귀가)",
        home = home,
        venue = jamsilBallpark,
        eventStart = t(2026, 10, 10, 14, 0),
        eventEnd = t(2026, 10, 10, 17, 10),
        plannedDeparture = t(2026, 10, 10, 12, 30),
        corridors = corridors,
        lots = lots,
        weekend = true,
    )

    val winterConcert = Scenario(
        id = "concert-winter",
        title = "겨울 콘서트 · 잠실 주경기장",
        subtitle = "12월 토요일 18:00 공연 · 21:00 종료 (야간 · SUNBLIND 자동 비활성)",
        home = home,
        venue = jamsilStadium,
        eventStart = t(2026, 12, 19, 18, 0),
        eventEnd = t(2026, 12, 19, 21, 0),
        plannedDeparture = t(2026, 12, 19, 16, 40),
        corridors = corridors,
        lots = lots,
        weekend = true,
    )

    val all = listOf(summerBaseball, autumnEvening, winterConcert)

    /** Not going to the game: Sangam -> Starfield Hana, passing Jamsil right when the game ends. */
    val passBaseball = PassScenario(
        id = "pass-hanam",
        title = "야구 안 봐요 · 상암 → 스타필드 하남",
        subtitle = "7월 토요일 17:20 출발 · 잠실야구장 18:00 경기 종료와 겹침",
        origin = home,
        destination = Place("스타필드 하남", LatLng(37.5455, 127.2240), "경기 하남시 미사대로 750"),
        plannedDeparture = t(2026, 7, 18, 17, 20),
        corridors = listOf(
            Corridor(
                id = "pass-olympic",
                name = "올림픽대로 (잠실 경유)",
                description = "가장 짧지만 잠실종합운동장 옆을 지남",
                points = listOf(
                    LatLng(37.5779, 126.8920), LatLng(37.5420, 126.8920), LatLng(37.5270, 126.9300),
                    LatLng(37.5100, 126.9950), LatLng(37.5240, 127.0500), LatLng(37.5165, 127.0700),
                    LatLng(37.5190, 127.0950), LatLng(37.5330, 127.1250), LatLng(37.5480, 127.1600),
                    LatLng(37.5455, 127.2240),
                ),
                freeFlowKmh = 64.0, congestionSensitivity = 1.0,
                viaPoint = LatLng(37.5165, 127.0700), viaName = "잠실 (올림픽대로)",
                naviHint = NaviRouteHint.SHORT_TIME,
            ),
            Corridor(
                id = "pass-gangbyeon",
                name = "강변북로 → 구리암사대교",
                description = "한강 북단 · 잠실 맞은편으로 통과",
                points = listOf(
                    LatLng(37.5779, 126.8920), LatLng(37.5450, 126.9300), LatLng(37.5280, 126.9800),
                    LatLng(37.5370, 127.0300), LatLng(37.5440, 127.0650), LatLng(37.5470, 127.1000),
                    LatLng(37.5560, 127.1300), LatLng(37.5600, 127.1650), LatLng(37.5455, 127.2240),
                ),
                freeFlowKmh = 60.0, congestionSensitivity = 1.1,
                viaPoint = LatLng(37.5470, 127.1000), viaName = "광장동 (강변북로)",
                naviHint = NaviRouteHint.HIGHWAY,
            ),
            Corridor(
                id = "pass-north",
                name = "내부순환 → 동부간선 → 구리",
                description = "북쪽 우회 · 행사장과 멀리 떨어짐",
                points = listOf(
                    LatLng(37.5779, 126.8920), LatLng(37.5850, 126.9400), LatLng(37.5950, 127.0000),
                    LatLng(37.5900, 127.0450), LatLng(37.5980, 127.0700), LatLng(37.5950, 127.1100),
                    LatLng(37.5750, 127.1500), LatLng(37.5600, 127.1900), LatLng(37.5455, 127.2240),
                ),
                freeFlowKmh = 44.0, congestionSensitivity = 1.0,
                viaPoint = LatLng(37.5980, 127.0700), viaName = "월릉 (동부간선)",
                naviHint = NaviRouteHint.COMFORTABLE,
            ),
        ),
        hotspots = listOf(
            CrowdHotspot("잠실야구장", jamsilBallpark.location, t(2026, 7, 18, 15, 0), t(2026, 7, 18, 18, 0), 23000),
        ),
        weekend = true,
    )
}

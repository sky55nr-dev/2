package com.roundwise.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roundwise.app.data.Scenarios
import com.roundwise.app.engine.*
import com.roundwise.app.engine.JourneyOptimizer.Companion.hhmm
import com.roundwise.app.pleos.PleosStatus
import com.roundwise.app.pleos.SdkState
import kotlin.math.roundToInt

@Composable
fun RoundwiseScreen(ui: UiState, sdk: PleosStatus, vm: RoundwiseViewModel) {
    Column(Modifier.fillMaxSize().background(RW.bg).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Header(sdk)
        Spacer(Modifier.height(10.dp))
        ModeTabs(ui.mode, vm::setMode)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(0.27f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ui.mode == AppMode.EVENT) ScenarioPicker(ui, vm) else PassIntro()
                PreferencePanel(ui, sdk, vm)
            }
            Column(Modifier.weight(0.38f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val map = mapModel(ui)
                if (map != null) JourneyMap(map, Modifier.fillMaxWidth().weight(1f)) else Box(Modifier.weight(1f))
                Legend()
                if (ui.mode == AppMode.EVENT) ui.analysis?.let { ModuleChips(it.modules) }
                else ui.pass?.let { PassNote(it) }
            }
            Column(Modifier.weight(0.35f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ui.computing && ui.analysis == null && ui.pass == null) {
                    CircularProgressIndicator()
                } else if (ui.mode == AppMode.EVENT) {
                    ui.analysis?.let { EventPanel(ui, it, vm) }
                } else {
                    ui.pass?.let { PassPanel(ui, it, vm) }
                }
            }
        }
    }
}

// ---- header -------------------------------------------------------------------------------------

@Composable
private fun Header(sdk: PleosStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("ROUND", color = RW.outbound, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("WISE", color = RW.inbound, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(16.dp))
        Text("가장 빨리 도착하는 길이, 가장 빨리 집에 돌아오는 길은 아니다.", color = RW.sub, fontSize = 15.sp, modifier = Modifier.weight(1f))
        SdkChip("NaviHelper", sdk.navi)
        Spacer(Modifier.width(6.dp))
        SdkChip("Location", sdk.location)
        Spacer(Modifier.width(6.dp))
        SdkChip("TTS", sdk.tts)
    }
}

@Composable
private fun SdkChip(name: String, s: SdkState) {
    val c = when (s) { SdkState.READY -> RW.good; SdkState.CONNECTING -> RW.outbound; SdkState.FAILED -> RW.muted; SdkState.IDLE -> RW.muted }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(RW.surface).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(6.dp))
        Text("$name · ${s.label}", color = RW.sub, fontSize = 12.sp)
    }
}

@Composable
private fun ModeTabs(mode: AppMode, onSelect: (AppMode) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(RW.surface).padding(4.dp)) {
        AppMode.entries.forEach { m ->
            val sel = m == mode
            Text(
                m.label,
                color = if (sel) RW.bg else RW.sub,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (sel) RW.inbound else Color.Transparent)
                    .clickable { onSelect(m) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

// ---- left column --------------------------------------------------------------------------------

@Composable
private fun ScenarioPicker(ui: UiState, vm: RoundwiseViewModel) {
    Card("여정 선택") {
        Scenarios.all.forEachIndexed { i, s ->
            val sel = i == ui.scenarioIndex
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (sel) RW.surface2 else Color.Transparent)
                    .border(1.dp, if (sel) RW.inbound else RW.line, RoundedCornerShape(10.dp))
                    .clickable { vm.selectScenario(i) }
                    .padding(12.dp),
            ) {
                Text(s.title, color = RW.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(s.subtitle, color = RW.sub, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
        Text("※ 교통·주차·인파 수치는 서비스 동작을 보여주기 위한 가상 데이터입니다.", color = RW.muted, fontSize = 11.sp)
    }
}

@Composable
private fun PassIntro() {
    Card("야구 안 봐요, 그냥 지나가요") {
        val s = Scenarios.passBaseball
        Text(s.title, color = RW.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(s.subtitle, color = RW.sub, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "기존 내비는 출발 시점의 교통만 보고 경로를 고릅니다. 경기 종료 인파·차량이 몰려나오는 시각에 " +
                "행사장 옆을 지나게 되는지까지 예측해, 우회 경로나 출발 시각 조정을 제안합니다.",
            color = RW.sub, fontSize = 13.sp,
        )
    }
}

@Composable
private fun PreferencePanel(ui: UiState, sdk: PleosStatus, vm: RoundwiseViewModel) {
    val p = ui.prefs
    Card("내 선호 (λ 가중치)") {
        Pref("역광 민감도 λ1", p.glareWeight, 0f..4f, "%.1f") { v -> vm.updatePrefs { it.copy(glareWeight = v.toDouble()) } }
        Pref("인파 회피 λ3", p.crowdWeight, 0f..4f, "%.1f") { v -> vm.updatePrefs { it.copy(crowdWeight = v.toDouble()) } }
        if (ui.mode == AppMode.EVENT) {
            Pref("주차비 민감도 λ2 (분/천원)", p.feeWeight, 0f..2f, "%.1f") { v -> vm.updatePrefs { it.copy(feeWeight = v.toDouble()) } }
            Pref("도보 부담", p.walkWeight, 0.5f..3f, "×%.1f") { v -> vm.updatePrefs { it.copy(walkWeight = v.toDouble()) } }
        }
        Toggle("출발 시각 조정 허용", p.flexibleDeparture) { v -> vm.updatePrefs { it.copy(flexibleDeparture = v) } }
        Toggle(
            "현재 차량 위치에서 출발" + if (sdk.currentLocation == null) " (위치 없음)" else "",
            ui.useCurrentLocation,
        ) { vm.setUseCurrentLocation(it) }
    }
}

@Composable
private fun Pref(label: String, value: Double, range: ClosedFloatingPointRange<Float>, fmt: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = RW.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(fmt.format(value), color = RW.text, fontSize = 13.sp)
    }
    Slider(
        value = value.toFloat(), onValueChange = onChange, valueRange = range,
        colors = SliderDefaults.colors(thumbColor = RW.inbound, activeTrackColor = RW.inbound, inactiveTrackColor = RW.line),
    )
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = RW.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = RW.inbound))
    }
}

// ---- middle column ------------------------------------------------------------------------------

private fun mapModel(ui: UiState): MapModel? = if (ui.mode == AppMode.EVENT) {
    ui.analysis?.let { a ->
        val p = ui.activePlan ?: a.recommended
        MapModel(
            corridors = a.scenario.corridors, outbound = p.outbound, inbound = p.inbound,
            home = a.scenario.home, destination = a.scenario.venue.name to a.scenario.venue.location,
            lots = a.scenario.lots, chosenLot = p.lot, hotspots = a.scenario.hotspots,
            sun = p.inbound.sunAtDepart,
            sunLabel = "귀가 ${hhmm(p.inbound.depart)} 태양 ${p.inbound.sunAtDepart.elevationDeg.roundToInt()}°",
        )
    }
} else {
    ui.pass?.let { r ->
        val p = r.plans.getValue(ui.selectedPass)
        MapModel(
            corridors = r.scenario.corridors, outbound = p.leg, inbound = null,
            home = r.scenario.origin, destination = r.scenario.destination.name to r.scenario.destination.location,
            lots = emptyList(), chosenLot = null, hotspots = r.scenario.hotspots,
            sun = p.leg.sunAtDepart,
            sunLabel = "출발 ${hhmm(p.departure)} 태양 ${p.leg.sunAtDepart.elevationDeg.roundToInt()}°",
        )
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Dot(RW.outbound, "가는 길"); Dot(RW.inbound, "돌아오는 길"); Dot(RW.glare, "역광 구간")
        Dot(RW.crowd, "인파 밀집"); Dot(RW.sub, "주차장")
    }
}

@Composable
private fun Dot(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(5.dp))
        Text(t, color = RW.sub, fontSize = 12.sp)
    }
}

@Composable
private fun ModuleChips(m: ModuleStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ModuleRow("REVERSE ETA", m.reverseEtaActive, m.reverseEtaNote, RW.inbound)
        ModuleRow("EXIT FIRST", m.exitFirstActive, m.exitFirstNote, RW.queue)
        ModuleRow("SUNBLIND", m.sunblindActive, m.sunblindNote, RW.glare)
        ModuleRow("CROWD AVOID", m.crowdActive, m.crowdNote, RW.crowd)
    }
}

@Composable
private fun ModuleRow(name: String, active: Boolean, note: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(RW.surface).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name, color = if (active) RW.bg else RW.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (active) color else RW.surface2)
                .padding(horizontal = 8.dp, vertical = 3.dp).width(92.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(note, color = if (active) RW.text else RW.sub, fontSize = 13.sp)
    }
}

@Composable
private fun PassNote(r: PassAnalysis) {
    ModuleRow("CROWD AVOID", r.crowdActive, r.note, RW.crowd)
}

// ---- right column: event mode -------------------------------------------------------------------

@Composable
private fun EventPanel(ui: UiState, a: AnalysisResult, vm: RoundwiseViewModel) {
    val plan = ui.activePlan ?: a.recommended
    val base = a.baseline
    val saved = (base.totalTravelMin - plan.totalTravelMin).roundToInt()

    Card(null) {
        Text(ui.phase.label + if (ui.delayMin > 0) " · 일정 ${ui.delayMin}분 지연 반영" else "", color = RW.sub, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("귀가 예정 ", color = RW.sub, fontSize = 16.sp)
            Text(hhmm(plan.homeArrival), color = RW.inbound, fontSize = 44.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("총 이동 ${plan.totalTravelMin.roundToInt()}분", color = RW.text, fontSize = 15.sp)
                Text(
                    if (saved > 0) "기존 내비 ${base.totalTravelMin.roundToInt()}분 대비 ${saved}분 단축" else "기존 내비와 비슷",
                    color = if (saved > 0) RW.good else RW.sub, fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Timeline(plan, a.scenario)
        Spacer(Modifier.height(10.dp))
        ActionButtons(ui, vm)
        ui.replan?.let { re ->
            Spacer(Modifier.height(8.dp))
            val diff = minutesBetween(re.before.homeArrival, re.after.homeArrival).roundToInt()
            Text(
                "재계획: 기존 계획 유지 시 ${hhmm(re.before.homeArrival)} → 변경 후 ${hhmm(re.after.homeArrival)}" +
                    (if (diff < 0) " (${-diff}분 절약)" else "") +
                    (if (re.changedExit || re.changedCorridor) " · ${re.after.exit.name} / ${re.after.inbound.corridor.name}" else " · 계획 유지"),
                color = RW.outbound, fontSize = 13.sp,
            )
        }
    }

    Card("여정 비교 · ${a.evaluatedCombinations}개 조합 탐색") {
        a.plans.forEach { (k, p) -> PlanRow(k.label, p, k == ui.selected && ui.replan == null, max = a.plans.values.maxOf { it.totalTravelMin }) { vm.select(k) } }
        Text("왕복 주행 · 도보 · 주차장 입·출차 대기 합계 (일정 시간 제외)", color = RW.muted, fontSize = 11.sp)
    }
}

@Composable
private fun Timeline(p: JourneyPlan, s: Scenario) {
    val steps = listOf(
        Triple(hhmm(p.departure), "출발 · ${p.outbound.corridor.name}", "주행 ${p.outbound.driveMinutes.roundToInt()}분 · 역광 ${glareLevelOf(p.outbound.glareWeightedMin).label}"),
        Triple(hhmm(p.outbound.arrive), "주차 · ${p.lot.name}", "입차 대기 ${p.entryWaitMin.roundToInt()}분 · 도보 ${p.walkInMin.roundToInt()}분" + if (p.lotFullRisk) " · 만차 위험" else ""),
        Triple(hhmm(s.eventStart), s.venue.name, "도착 ${hhmm(p.arriveAtVenue)} · 종료 ${hhmm(p.eventEnd)}"),
        Triple(hhmm(p.inbound.depart.minusSeconds((p.accessMin * 60).toLong())), "출차 · ${p.exit.name}", "출차 대기 ${p.exitWaitMin.roundToInt()}분 · 주차비 ${"%,d".format(p.feeKrw)}원"),
        Triple(hhmm(p.homeArrival), "귀가 · ${p.inbound.corridor.name}", "주행 ${p.inbound.driveMinutes.roundToInt()}분 · 역광 ${glareLevelOf(p.inbound.glareWeightedMin).label} · 인파 ${p.inbound.crowdExposedMin.roundToInt()}분"),
    )
    val colors = listOf(RW.outbound, RW.walk, RW.text, RW.queue, RW.inbound)
    Column {
        steps.forEachIndexed { i, (t, title, sub) ->
            Row(verticalAlignment = Alignment.Top) {
                Text(t, color = RW.sub, fontSize = 13.sp, modifier = Modifier.width(52.dp))
                Box(Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(colors[i]))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, color = RW.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(sub, color = RW.sub, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ActionButtons(ui: UiState, vm: RoundwiseViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (ui.phase) {
            Phase.PLANNING -> Btn("가는 길 안내 시작", RW.outbound, Modifier.weight(1f)) { vm.startOutbound() }
            Phase.OUTBOUND -> Btn("주차 완료", RW.walk, Modifier.weight(1f)) { vm.onArrived() }
            Phase.AT_EVENT -> Btn("귀가 안내 시작", RW.inbound, Modifier.weight(1f)) { vm.startReturn() }
            Phase.RETURNING -> Btn("여정 종료", RW.sub, Modifier.weight(1f)) { vm.resetJourney() }
        }
        Btn("경기 +15분 지연", RW.surface2, Modifier.weight(1f), textColor = RW.text) { vm.delayEvent(15) }
        Btn("음성 브리핑", RW.surface2, Modifier.weight(0.8f), textColor = RW.text) { vm.speakBriefing() }
    }
}

@Composable
private fun Btn(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = RW.bg, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = textColor),
        shape = RoundedCornerShape(12.dp),
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun PlanRow(label: String, p: JourneyPlan, selected: Boolean, max: Double, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) RW.surface2 else Color.Transparent)
            .clickable(onClick = onClick).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (label.startsWith("ROUNDWISE")) RW.inbound else RW.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${p.totalTravelMin.roundToInt()}분", color = RW.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("  귀가 ${hhmm(p.homeArrival)}", color = RW.sub, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        StackedBar(
            listOf(p.roundTripDriveMin to RW.outbound, p.parkingQueueMin to RW.queue, p.walkTotalMin to RW.walk),
            total = max,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "${p.lot.name} · ${p.outbound.corridor.name}/${p.inbound.corridor.name} · 출발 ${hhmm(p.departure)} · " +
                "역광 ${p.glareLevel.label} · 인파 ${p.crowdExposedMin.roundToInt()}분 · ${"%.1f".format(p.totalDistanceKm)}km",
            color = RW.sub, fontSize = 11.sp,
        )
    }
}

@Composable
private fun StackedBar(parts: List<Pair<Double, Color>>, total: Double) {
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(RW.bg)) {
        parts.forEach { (v, c) -> if (v > 0) Box(Modifier.fillMaxHeight().weight((v / total).toFloat().coerceAtLeast(0.001f)).background(c)) }
        val rest = total - parts.sumOf { it.first }
        if (rest > 0.01) Spacer(Modifier.weight((rest / total).toFloat()))
    }
}

// ---- right column: pass-through mode ------------------------------------------------------------

@Composable
private fun PassPanel(ui: UiState, r: PassAnalysis, vm: RoundwiseViewModel) {
    val p = r.plans.getValue(ui.selectedPass)
    val b = r.baseline
    Card(null) {
        Text("목적지 도착 예정", color = RW.sub, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(hhmm(p.arrival), color = RW.inbound, fontSize = 44.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("${p.leg.corridor.name} · 출발 ${hhmm(p.departure)}", color = RW.text, fontSize = 15.sp)
                val d = minutesBetween(p.arrival, b.arrival).roundToInt()
                Text(
                    "인파 구간 ${b.leg.crowdExposedMin.roundToInt()}분 → ${p.leg.crowdExposedMin.roundToInt()}분" +
                        if (d > 0) " · ${d}분 일찍 도착" else "",
                    color = RW.good, fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("이 경로로 안내 시작", RW.inbound, Modifier.weight(1f)) { vm.startPass() }
            Btn("음성 브리핑", RW.surface2, Modifier.weight(0.6f), textColor = RW.text) { vm.speakBriefing() }
        }
    }
    Card("경로 비교") {
        val max = r.plans.values.maxOf { it.leg.driveMinutes }
        r.plans.forEach { (k, plan) ->
            val sel = k == ui.selectedPass
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (sel) RW.surface2 else Color.Transparent)
                    .clickable { vm.selectPass(k) }.padding(10.dp),
            ) {
                Row {
                    Text(k.label, color = if (k == PassStrategy.ROUNDWISE) RW.inbound else RW.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${plan.leg.driveMinutes.roundToInt()}분", color = RW.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("  도착 ${hhmm(plan.arrival)}", color = RW.sub, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                StackedBar(listOf(plan.leg.driveMinutes - plan.leg.crowdExposedMin to RW.outbound, plan.leg.crowdExposedMin to RW.crowd), max)
                Text("${plan.leg.corridor.name} · 출발 ${hhmm(plan.departure)} · 인파 구간 ${plan.leg.crowdExposedMin.roundToInt()}분 · 역광 ${glareLevelOf(plan.leg.glareWeightedMin).label}", color = RW.sub, fontSize = 11.sp)
            }
        }
        Text("보라색 = 행사 인파·차량 집중 구간을 지나는 시간", color = RW.muted, fontSize = 11.sp)
    }
}

// ---- shared -------------------------------------------------------------------------------------

@Composable
private fun Card(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RW.surface).padding(16.dp)) {
        if (title != null) {
            Text(title, color = RW.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}


package com.roundwise.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roundwise.app.data.Scenarios
import com.roundwise.app.engine.*
import com.roundwise.app.pleos.PleosBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class AppMode(val label: String) { EVENT("행사 가는 길 · 왕복 설계"), PASS("지나가기만 · 인파 회피") }

enum class Phase(val label: String) {
    PLANNING("출발 전 계획"), OUTBOUND("가는 길 안내 중"), AT_EVENT("주차 완료 · 일정 중"), RETURNING("귀가 안내 중")
}

data class UiState(
    val mode: AppMode = AppMode.EVENT,
    val scenarioIndex: Int = 0,
    val prefs: Preferences = Preferences(),
    val useCurrentLocation: Boolean = false,
    val delayMin: Int = 0,
    val phase: Phase = Phase.PLANNING,
    val selected: Strategy = Strategy.ROUNDWISE,
    val computing: Boolean = true,
    val analysis: AnalysisResult? = null,
    val replan: ReturnReplan? = null,
    val pass: PassAnalysis? = null,
    val selectedPass: PassStrategy = PassStrategy.ROUNDWISE,
    val toast: String? = null,
) {
    val scenario get() = Scenarios.all[scenarioIndex]
    /** The plan currently in effect (after a re-plan, the updated one). */
    val activePlan: JourneyPlan? get() = replan?.after ?: analysis?.plans?.get(selected)
}

class RoundwiseViewModel(app: Application) : AndroidViewModel(app) {
    val pleos = PleosBridge(app)
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private var job: Job? = null
    private var optimizer: JourneyOptimizer? = null

    init {
        pleos.onArrived = { onArrived() }
        recompute()
    }

    fun setMode(m: AppMode) { _ui.update { it.copy(mode = m, phase = Phase.PLANNING, replan = null) }; recompute() }
    fun selectScenario(i: Int) { _ui.update { it.copy(scenarioIndex = i, phase = Phase.PLANNING, replan = null, delayMin = 0) }; recompute() }
    fun updatePrefs(f: (Preferences) -> Preferences) { _ui.update { it.copy(prefs = f(it.prefs)) }; recompute() }
    fun setUseCurrentLocation(v: Boolean) { _ui.update { it.copy(useCurrentLocation = v) }; recompute() }
    fun select(s: Strategy) = _ui.update { it.copy(selected = s, replan = null) }
    fun selectPass(s: PassStrategy) = _ui.update { it.copy(selectedPass = s) }
    fun consumeToast() = _ui.update { it.copy(toast = null) }

    private fun origin(): LatLng? =
        if (_ui.value.useCurrentLocation) pleos.status.value.currentLocation else null

    fun recompute() {
        job?.cancel()
        _ui.update { it.copy(computing = true) }
        val s = _ui.value
        job = viewModelScope.launch {
            if (s.mode == AppMode.EVENT) {
                val opt = JourneyOptimizer(s.scenario, origin())
                val r = withContext(Dispatchers.Default) { opt.analyze(s.prefs) }
                optimizer = opt
                _ui.update { it.copy(analysis = r, computing = false, replan = null, delayMin = 0) }
            } else {
                val r = withContext(Dispatchers.Default) { PassThroughOptimizer(Scenarios.passBaseball, origin()).analyze(s.prefs) }
                _ui.update { it.copy(pass = r, computing = false) }
            }
        }
    }

    // ---- journey actions ------------------------------------------------------------------------

    fun startOutbound() {
        val p = _ui.value.activePlan ?: return
        val ok = pleos.startGuidance(p.lot.location, p.lot.name, p.lot.address, p.outbound.corridor)
        _ui.update { it.copy(phase = Phase.OUTBOUND, toast = if (ok) "차량 내비게이션으로 경로를 전달했습니다" else "Pleos NaviHelper 미연결 · 시뮬레이션으로 진행합니다") }
        pleos.speak(briefing(p))
    }

    fun onArrived() {
        if (_ui.value.phase == Phase.OUTBOUND) _ui.update { it.copy(phase = Phase.AT_EVENT) }
        else if (_ui.value.phase == Phase.RETURNING) _ui.update { it.copy(phase = Phase.PLANNING, toast = "귀가 완료") }
    }

    /** Simulates the event running late; REVERSE ETA re-plans exit and route home. */
    fun delayEvent(byMin: Int) {
        val s = _ui.value
        val r = s.analysis ?: return
        val opt = optimizer ?: return
        val base = r.plans.getValue(s.selected)
        val total = s.delayMin + byMin
        viewModelScope.launch {
            val re = withContext(Dispatchers.Default) { opt.replanReturn(base, r.eventEnd.plusMinutes(total.toLong()), s.prefs) }
            _ui.update { it.copy(replan = re, delayMin = total, phase = if (it.phase == Phase.PLANNING) Phase.AT_EVENT else it.phase) }
            pleos.speak(replanSpeech(re, total))
        }
    }

    fun startReturn() {
        val s = _ui.value
        val p = s.activePlan ?: return
        val home = s.scenario.home
        val ok = pleos.startGuidance(home.location, home.name, home.address, p.inbound.corridor)
        _ui.update { it.copy(phase = Phase.RETURNING, toast = if (ok) "귀가 경로를 차량 내비게이션으로 전달했습니다" else "Pleos NaviHelper 미연결 · 시뮬레이션으로 진행합니다") }
        pleos.speak("${p.exit.name}로 출차 후 ${p.inbound.corridor.name}로 귀가합니다. 도착 예정 ${say(p.homeArrival)}입니다.")
    }

    fun resetJourney() { pleos.cancelGuidance(); _ui.update { it.copy(phase = Phase.PLANNING, replan = null, delayMin = 0) } }

    fun startPass() {
        val s = _ui.value
        val r = s.pass ?: return
        val p = r.plans.getValue(s.selectedPass)
        val d = r.scenario.destination
        val ok = pleos.startGuidance(d.location, d.name, d.address, p.leg.corridor)
        _ui.update { it.copy(toast = if (ok) "인파 회피 경로를 차량 내비게이션으로 전달했습니다" else "Pleos NaviHelper 미연결 · 시뮬레이션으로 진행합니다") }
        pleos.speak(r.note)
    }

    fun speakBriefing() {
        val s = _ui.value
        if (s.mode == AppMode.PASS) { s.pass?.let { pleos.speak(it.note) }; return }
        s.activePlan?.let { pleos.speak(briefing(it)) }
    }

    private fun briefing(p: JourneyPlan): String {
        val base = _ui.value.analysis?.baseline
        val saved = base?.let { (it.totalTravelMin - p.totalTravelMin).roundToInt() } ?: 0
        return buildString {
            append("가장 빨리 도착하는 길이, 가장 빨리 집에 돌아오는 길은 아닙니다. ")
            append("${say(p.departure)}에 출발해 ${p.outbound.corridor.name}로 이동하고, ${p.lot.name}에 주차하세요. ")
            append("귀가는 ${p.exit.name} 출차 후 ${p.inbound.corridor.name}, 도착 예정 ${say(p.homeArrival)}입니다. ")
            if (saved > 0) append("기존 경로보다 약 ${saved}분 빠릅니다.")
        }
    }

    private fun replanSpeech(re: ReturnReplan, delay: Int) = buildString {
        append("일정이 ${delay}분 늦어졌습니다. ")
        if (re.changedExit || re.changedCorridor) append("${re.after.exit.name}, ${re.after.inbound.corridor.name}로 귀가 계획을 변경합니다. ")
        append("새 도착 예정은 ${say(re.after.homeArrival)}입니다.")
    }

    private fun say(t: java.time.ZonedDateTime): String {
        val h = t.hour
        val ampm = if (h < 12) "오전" else "오후"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$ampm ${h12}시 ${t.minute}분"
    }

    override fun onCleared() { pleos.release(); super.onCleared() }
}

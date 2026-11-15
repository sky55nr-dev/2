package com.roundwise.app.pleos

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.roundwise.app.engine.Corridor
import com.roundwise.app.engine.LatLng
import com.roundwise.app.engine.NaviRouteHint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

enum class SdkState(val label: String) { IDLE("대기"), CONNECTING("연결 중"), READY("연결됨"), FAILED("사용 불가") }

data class PleosStatus(
    val navi: SdkState = SdkState.IDLE,
    val location: SdkState = SdkState.IDLE,
    val tts: SdkState = SdkState.IDLE,
    val currentLocation: LatLng? = null,
    val lastNaviEvent: String = "",
    /** Real ETA reported by the Pleos navigation once guidance starts (minutes, meters). */
    val naviRouteMinutes: Int? = null,
    val naviRouteMeters: Int? = null,
    val arrivedAt: String? = null,
)

/**
 * Thin wrapper around the Pleos Connect SDKs used by ROUNDWISE:
 *  - NaviHelper: hands the optimised plan (destination + corridor waypoint + route option) to the car's navigation.
 *  - FusedLocation: vehicle position as journey origin.
 *  - TextToSpeech (ON_DEVICE): spoken briefings for the driver, no network or app key needed.
 *
 * Every call is guarded: if a service is not available (e.g. a plain Android emulator)
 * the app keeps working in simulation mode and shows the SDK state in the status bar.
 */
class PleosBridge(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _status = MutableStateFlow(PleosStatus())
    val status: StateFlow<PleosStatus> = _status.asStateFlow()

    private var navi: NaviHelper? = null
    private var fused: FusedLocation? = null
    private var tts: TextToSpeech? = null

    var onArrived: ((String) -> Unit)? = null

    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            _status.update {
                it.copy(
                    lastNaviEvent = "경로 안내 시작 · ${routeStartInfo.name}",
                    naviRouteMinutes = routeStartInfo.duration / 60,
                    naviRouteMeters = routeStartInfo.distance,
                )
            }
        }
        override fun onRoutingCompleted() = event("경로 탐색 완료")
        override fun onReRouteEnded() = event("재탐색 완료")
        override fun onRouteCancelled() = event("경로 안내 취소")
        override fun onRouteEnded() = event("경로 안내 종료")
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            _status.update { it.copy(lastNaviEvent = "목적지 도착 · ${destinationArrivedInfo.name}", arrivedAt = destinationArrivedInfo.name) }
            main.post { onArrived?.invoke(destinationArrivedInfo.name) }
        }
        override fun onError(errorCode: NaviErrorCode) = event("내비 오류 · ${errorCode.name}")
    }

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(locationData: LocationData?) {
            if (locationData == null) return
            _status.update {
                it.copy(location = SdkState.READY, currentLocation = LatLng(locationData.latitude, locationData.longitude))
            }
        }
        override fun onError(exception: Exception) {
            Log.w(TAG, "FusedLocation error", exception)
            _status.update { it.copy(location = SdkState.FAILED) }
        }
    }

    private fun event(msg: String) = _status.update { it.copy(lastNaviEvent = msg) }

    fun start(locationPermissionGranted: Boolean) {
        startNavi()
        startTts()
        if (locationPermissionGranted) startLocation() else _status.update { it.copy(location = SdkState.FAILED) }
    }

    private fun startNavi() = guard("NaviHelper", { _status.update { s -> s.copy(navi = SdkState.FAILED) } }) {
        _status.update { it.copy(navi = SdkState.CONNECTING) }
        val n = NaviHelper(app)
        navi = n
        n.initialize(
            onConnected = {
                n.addListener(naviListener)
                _status.update { it.copy(navi = SdkState.READY) }
            },
            onFailed = { t ->
                Log.w(TAG, "NaviHelper connect failed", t)
                _status.update { it.copy(navi = SdkState.FAILED) }
            },
        )
    }

    fun startLocation() = guard("FusedLocation", { _status.update { s -> s.copy(location = SdkState.FAILED) } }) {
        _status.update { it.copy(location = SdkState.CONNECTING) }
        val f = FusedLocation(app)
        fused = f
        f.initialize()
        f.getLastKnownLocation(locationListener)
        f.registerFusedLocationCallback(locationListener)
    }

    private fun startTts() = guard("TextToSpeech", { _status.update { s -> s.copy(tts = SdkState.FAILED) } }) {
        _status.update { it.copy(tts = SdkState.CONNECTING) }
        val t = TextToSpeech(app, Mode.ON_DEVICE)
        tts = t
        t.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                guard("TTS locale", {}) { t.setLocale(Locale.KOREA) }
                _status.update { it.copy(tts = SdkState.READY) }
            }
            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "TTS init failed: $errorType")
                _status.update { it.copy(tts = SdkState.FAILED) }
            }
        })
    }

    fun speak(text: String) {
        if (_status.value.tts != SdkState.READY) return
        guard("TTS speak", {}) { tts?.speak(text, true) }
    }

    /**
     * Sends the plan to the vehicle navigation: final destination + one waypoint on the chosen corridor,
     * so the car actually follows the corridor ROUNDWISE selected.
     */
    fun startGuidance(destination: LatLng, name: String, address: String, corridor: Corridor?): Boolean {
        val n = navi
        if (n == null || _status.value.navi != SdkState.READY) {
            event("NaviHelper 미연결 — 시뮬레이션 모드")
            return false
        }
        return guard("requestRoute", { event("경로 요청 실패") }) {
            val option = corridor?.naviHint?.toRouteOption() ?: RouteOption.RECOMMENDED
            val dest = RouteInfo(
                destination.lat, destination.lon, null, null,
                name, "", address, "", option, "", false,
            )
            if (corridor != null) {
                val via = RequestWaypointInfo(
                    corridor.viaPoint.lat, corridor.viaPoint.lon, corridor.viaName,
                    WaypointIndex.FIRST, "", "", "", "",
                )
                n.requestRouteWithWaypoints(RouteWithWaypointsRequest(dest, listOf(via)))
            } else {
                n.requestRoute(dest)
            }
            _status.update { it.copy(lastNaviEvent = "경로 요청 · $name (${corridor?.name ?: "추천"})", arrivedAt = null) }
        }
    }

    fun cancelGuidance() = guard("cancelRoute", {}) { navi?.cancelRoute() }

    fun release() {
        guard("release navi", {}) { navi?.removeListener(naviListener); navi?.release() }
        guard("release fused", {}) { fused?.unregisterFusedLocationCallback(locationListener); fused?.release() }
        guard("release tts", {}) { tts?.stop(); tts?.release() }
        navi = null; fused = null; tts = null
    }

    private inline fun guard(what: String, onFail: () -> Unit, block: () -> Unit): Boolean =
        try {
            block(); true
        } catch (t: Throwable) {
            Log.w(TAG, "$what failed", t)
            onFail(); false
        }

    private fun NaviRouteHint.toRouteOption() = when (this) {
        NaviRouteHint.RECOMMENDED -> RouteOption.RECOMMENDED
        NaviRouteHint.SHORT_TIME -> RouteOption.SHORT_TIME
        NaviRouteHint.SHORT_DISTANCE -> RouteOption.SHORT_DISTANCE
        NaviRouteHint.HIGHWAY -> RouteOption.HIGHWAY
        NaviRouteHint.COMFORTABLE -> RouteOption.COMFORTABLE
    }

    companion object {
        const val TAG = "Roundwise"
        const val LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }
}

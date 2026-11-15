# ROUNDWISE — 도착이 아닌, 귀환까지 설계하는 내비게이션

> "가장 빨리 도착하는 길이, 가장 빨리 집에 돌아오는 길은 아니다."

Pleos Connect용 차량 앱(현대자동차그룹 차량용 앱 개발 공모전 출품작).
출발 → 이동 → 주차 → 일정 → 출차 → 귀가 전체 여정을 하나의 최적화 문제로 풉니다.

```
J = T_왕복주행 + T_도보·w + T_입출차대기 + λ1·R_역광 + λ2·C_주차비 + λ3·R_인파 + λ_idle·T_대기
```
출발 시각 × 가는 경로 × 주차장 × 출구 × 귀가 경로 조합(시나리오당 360개)을 전수 탐색합니다.

## 모듈
| 모듈 | 역할 | 구현 |
|---|---|---|
| REVERSE ETA | 핵심 엔진. 귀가 시각까지 예측해 출발 시각·경로·주차장 결정, 일정 지연 시 귀가 재계획 | 시간대별 교통 프로파일 + 행사 시작/종료 전후 surge 모델, 구간별 시간 적분 |
| EXIT FIRST | 주차장·출구 선택 | 출구별 차로 수·이용 비율 기반 유체 대기열 모델, 입차 대기·만차 위험 |
| SUNBLIND | 역광 위험 | 태양 위치(천문 알고리즘) vs 구간 진행 방위각, 고도 0~35° · ±40° 이내 |
| CROWD AVOID | 행사 인파·차량 밀집 회피 | 행사장 반경·관중 규모·시간 창 기반 인파 강도, `CrowdSource` 인터페이스로 실데이터 교체 가능 |

상황에 따라 모듈이 자동 비활성화됩니다(예: 야간 → SUNBLIND 꺼짐).

## 웹 데모
`web/index.html` — 같은 엔진을 JS로 옮긴 브라우저 데모 (파일을 브라우저로 열면 됨).

## 두 가지 모드
1. **행사 가는 길 · 왕복 설계** — 주말 야구(여름/가을), 겨울 콘서트 시나리오. 기존 내비(편도 최단 + 가장 가까운 주차장) 대비 비교, `경기 +15분 지연` 버튼으로 귀가 재계획.
2. **지나가기만 · 인파 회피** — 야구를 보러 가지 않는 사람. 기존 내비는 *출발 시점 교통*만 보고 잠실 경유 경로를 고르지만, ROUNDWISE는 지나갈 시각의 경기 종료 인파를 예측해 우회.

## Pleos SDK 사용
| SDK | 버전 | 용도 |
|---|---|---|
| NaviHelper | 3.0.5 | 최종 목적지(주차장/집) + 선택 경로의 경유지 + RouteOption을 `requestRouteWithWaypoints`로 차량 내비에 전달, `onRouteStarted`/`onDestinationArrived` 수신 |
| FusedLocation | 1.1.3 | 차량 현재 위치를 출발지로 사용 (`pleos.car.permission.FUSED_LOCATION` 런타임 요청) |
| TextToSpeech | 3.3.1 | `Mode.ON_DEVICE` 음성 브리핑 (앱 등록 불필요) |

SDK 서비스가 연결되지 않는 환경에서도 앱은 시뮬레이션 모드로 동작하며, 상단에 SDK 연결 상태가 표시됩니다.

## 실행 방법
- 설치: `adb install -r apk/ROUNDWISE_release.apk`
- 빌드: `./gradlew :app:assembleRelease` (Android API 28+, Gradle 8.9, AGP 8.5.2, Kotlin 2.0.20, JDK 17+)
- 엔진 테스트: `./gradlew :app:testReleaseUnitTest`
- 경로에 한글이 포함된 Windows 폴더에서는 `android.overridePathCheck=true`가 설정되어 있음. 단위 테스트는 ASCII 경로에서 실행 권장.

## 테스트 환경
- Pleos Connect Emulator v2.1.0 (API 34, Automotive) 기준 개발 — 가로형 차량 화면 레이아웃
- release 빌드는 심사 설치 편의를 위해 debug 키로 서명

## 데이터 주의
교통량·주차장 용량·출차 대기·인파 수치는 서비스 동작을 설명하기 위한 **가상/추정 데이터**이며 실제 검증된 절감 효과가 아닙니다.
실서비스에서는 행사 일정 API, 서울시 실시간 도시데이터(인구 혼잡도), 주차장 실시간 정보로 `CrowdSource`·주차 모델을 대체할 계획입니다.

## 구조
```
app/src/main/java/com/roundwise/app/
  engine/  Geo, SolarPosition, Modules(Traffic·SunBlind·ExitFirst·LegSimulator), JourneyOptimizer, PassThroughOptimizer
  data/    Scenarios (데모 시나리오)
  pleos/   PleosBridge (NaviHelper·FusedLocation·TTS)
  ui/      Compose 화면 (지도 캔버스, 여정 타임라인, 비교 카드)
```

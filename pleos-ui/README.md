# ROUNDWISE Pleos UI

차량용 화면 개선 개발판입니다. 원본 `com.roundwise.app`과 별도로 `com.roundwise.pleos`를 설치하므로 기존 앱과 데이터를 보존합니다.

## 실행

프로젝트 루트의 `RUN-ROUNDWISE-PLEOS.cmd`를 실행합니다. Pleos Connect 2.1 에뮬레이터와 앱을 순서대로 실행하고 화면을 앞으로 가져옵니다. 현재 설치 환경의 AVD `Pleos_Connect_2_1`, 포트 5554, Android 사용자 10을 사용합니다.

빌드: `powershell -NoProfile -ExecutionPolicy Bypass -File pleos-ui/build.ps1`

결과: `pleos-ui/build/ROUNDWISE_Pleos.apk`. JDK 17, Android SDK 35와 build-tools 34가 필요합니다. 로컬 개발 키는 build 폴더에만 저장합니다.

## 변경한 사용 흐름

- 여정: 시연 일정 선택 → 예상 귀가 시간·주차장 확인 → 목적지 확인 → 차량 내비에 요청.
- 경로 비교: 같은 일정의 계산 결과를 비교하고 선택.
- 설정: 균형·도보·혼잡·주차비 선호를 실제 계산 엔진에 적용. 설정과 즐겨찾기는 로컬 저장.
- 여정 상세: 종료 시간 15/30분 지연 시 귀가 경로 재계산, 계획 초기화.
- 정차 상태 확인 후 조작 허용. 알 수 없는 차량 UX 상태는 잠금. 플랫폼 `distractionOptimized=false`도 유지.
- 가로 화면은 지도/정보 카드, 좁은 화면은 위아래 배치. 주·야간 색상 지원.

## 구현 방식과 범위

이 환경에는 원본 Compose 프로젝트의 Maven 의존성이 모두 캐시되어 있지 않아 **원본 Compose 소스를 전체 재빌드한 결과물이 아닙니다**. `apk/ROUNDWISE_release.apk`의 기존 엔진·SDK DEX, 리소스, 서비스를 유지하고, 이 폴더의 Java 화면을 추가해 새 패키지와 개발 키로 빌드합니다. 원본 Kotlin 화면은 수정하지 않습니다. 장기 유지·상용 배포 전에는 원본 Gradle 프로젝트로 UI를 통합하고 전체 소스 빌드와 정식 서명을 해야 합니다.

`RoundwiseBridge`는 원본 `RoundwiseViewModel`과 `StateFlow`를 사용합니다. 계산 값을 UI에서 별도로 흉내 내지 않습니다. 원본 APK 버전 변경 시 공개 모델/메서드 호환성 확인이 필요합니다. `_local/source-import.json`은 원본 가져오기 기록입니다.

모든 일정·혼잡·주차비·도착 예상은 **시연 데이터**입니다. 지도는 원본 좌표 기반의 도식이며 실제 지도 서비스가 아닙니다. 출발지는 시연 위치이고 GPS는 활성화하지 않았습니다. `NaviHelper READY`는 SDK 바인딩 상태이며 실제 경로 생성·안내 성공과 다릅니다. 내비 요청 결과는 연결 상태 화면에 표시하며 실제 차량 내비 화면에서 안내를 확인해야 합니다. 주차 예약·실시간 교통·실시간 주차 데이터는 연결하지 않았습니다.

UI 상용 배포에 필요한 계정·개인정보·오류 수집·실차 검증·접근성/주행 제한 인증은 완료하지 않았습니다. 이 결과물은 직접 조작 가능한 개발 시연판입니다.

## 내비 연동 확인 (2026-11-16)

NaviHelper 연결과 목적지 경로 요청은 확인했습니다. 개선판은 요청 후 공식 `requestNavigationScreen(NavigationScreenRequest.Foreground)` API로 내비 화면을 요청합니다. `NAVI_CUSTOM_MAP` 권한을 선언합니다. SDK의 연결 실패·화면 요청 실패 여부와 관계없이 설치된 Pleos 내비의 런처를 직접 실행합니다. 메인 화면의 **내비게이션 열기** 버튼으로 경로 전송 없이도 내비를 열 수 있습니다. 화면 열기는 경로 안내 성공과 구분합니다. 별도 이벤트 리스너의 `onRouteStarted`를 받아야만 안내 시작으로 표시하며, 요청 후 20초 동안 안내 시작이 확인되지 않으면 확인 필요 상태를 표시합니다.

에이전트가 점검한 당시 에뮬레이터의 내장 내비 `ai.umos.maps.android.navigation.app`는 `서비스 연결 중입니다.`에서 대기합니다. 해당 프로세스 로그에서 `gateway probe failed UnknownHostException: Unable to resolve host connect-gw-coral.umos.ai`를 확인했습니다. 호스트 Windows DNS 조회는 성공했지만 에뮬레이터 안에서는 실패하므로 **실제 경로 안내 성공은 확인되지 않았습니다**. PC/에뮬레이터의 허용된 외부 네트워크 환경에서 다시 확인해야 합니다. 시스템 내비 데이터 삭제, 인증값 변경, 방화벽 해제는 수행하지 않았습니다.

사용자는 이후 에뮬레이터 연결 복구와 정상 동작을 확인했다고 보고했습니다. 이는 사용자 확인이며, 에이전트가 실제 안내 시작 콜백을 재검증한 결과와는 구분합니다.

공식 API: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro

원본: https://github.com/JunHan-K/roundwise

## 확인

`pleos-ui/tools/test-pleos-ui.ps1`은 설치된 앱에서 UIAutomator로 경로 선택, 선호 적용, 일정 지연/초기화, 모드 전환을 확인합니다. `pleos-ui/tools/DeviceAdb.java`는 기존 개발자 키로 localhost 에뮬레이터에만 접속하는 ADB 전송 도구입니다. PC ADB 서버의 사용자 폴더 접근 오류를 피하기 위해 사용하며, 외부 네트워크 연결은 없습니다.

# ROUNDWISE — Pleos 로컬 실행

원본 저장소: https://github.com/JunHan-K/roundwise
앱 패키지: com.roundwise.app
버전: 1.0.0

## 실행

RUN-ROUNDWISE.cmd를 더블클릭합니다. 이미 설치된 경우 앱을 바로 열고, 설치되지 않았다면 apk/ROUNDWISE_release.apk를 설치합니다. Pleos AVD 이름은 Pleos_Connect_2_1, ADB 포트는 5554입니다.

## 가져온 파일과 검증

- 원본 텍스트 파일 30개와 Gradle Wrapper JAR를 로컬에 준비했습니다.
- 모든 원본 텍스트의 Git blob 해시와 Wrapper JAR 해시가 저장소와 일치함을 확인했습니다.
- 다운로드한 원본 APK의 Git blob SHA: fb68151a0e06cdccec20499d4b150ba0221c09bd
- 위 APK를 수정하거나 재서명하지 않고 Pleos API 34 에뮬레이터에 설치했습니다.
- APK 최초 설치 시 Android의 dex 최적화가 길어졌으나 최종 설치 완료를 확인했습니다.
- MainActivity 실행과 실제 화면 표시를 확인했습니다.
- 앱 표시: NaviHelper 연결됨 / Location 연결됨 / TTS 연결됨.
- ‘경기 +15분 지연’ 조작 후 ‘일정 15분 지연 반영’으로 갱신되는 것을 확인했습니다.

원본 소스와 APK는 변경하지 않았습니다. local.properties와 _local/의 실행 보조 파일, 이 안내 파일만 추가했습니다. Git clone이 아니라 GitHub 연결 도구로 원본 파일을 가져온 스냅샷입니다. 파일별 출처 해시는 _local/source-import.json에 있습니다.

SDK 연결 표시는 실제 경로 안내 완료를 의미하지 않습니다. 실제 내비게이션 경로 요청의 성공 여부는 별도로 확인해야 합니다. 교통·주차·인파 수치는 원본 README에 명시된 가상/추정 데이터입니다. 소스로 재빌드하거나 원격 저장소를 수정/업로드하지 않았습니다.
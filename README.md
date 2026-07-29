# GunpoSchoolAndroid

군포고등학교 SwiftUI 앱을 Android용 Jetpack Compose로 이식한 프로젝트입니다.

## 현재 구현

- 홈: 학생 정보, 현재/다음 수업, 오늘 급식, 다가오는 학사일정
- 주간 시간표 및 8교시 설정 반영
- 2026년 여름방학 급식 달력
- 학교 홈페이지 공지·가정통신문 실시간 동기화와 앱 내부 상세 화면
- DataStore 기반 이름·학번·학년·반, 8교시, 야간자습 설정
- 여름방학 5교시 강좌·강의실 선택과 방학 시간표 자동 전환
- Android 16 promoted ongoing 실시간 수업 알림 및 교시 경계 자동 갱신
- 학교 반경 200m 진입·이탈 Geofencing 자동 시작/종료
- 현재/다음 수업 Glance 홈 화면 위젯
- 시간표·급식·현재 수업·8교시·야자를 답하는 오프라인 학교 AI
- Cloudflare Worker 기반 NEIS 급식·연간 학사일정·학년/반별 시간표 동기화
- 서버의 시간당 공지 수집, 무료 D1 저장, 홈페이지 지문 불일치 시 즉시 재동기화
- 네트워크 실패 시 마지막 NEIS 응답 및 내장 데이터 자동 폴백
- AI 응답: Firebase AI Logic의 Gemini 사용(학교 데이터 질문은 확인된 앱 데이터만 근거로 답변)
- 앱 아이콘 빠른 실행: 오늘 시간표, 오늘 급식, 학교 AI
- API 33(Android 13) 이상 지원

## 실행

1. Android Studio에서 이 폴더를 엽니다.
2. SDK Manager에서 Android API 37과 JDK 17을 선택합니다.
3. `gradle-wrapper.jar`가 없다면 PowerShell에서 `./bootstrap-gradle.ps1`을 한 번 실행합니다.
4. Gradle Sync 후 에뮬레이터 또는 기기에서 `app`을 실행합니다.

Firebase AI Logic을 구성하지 않은 개발 빌드에서만 직접 Gemini 키를 사용하려면 Git에서 제외되는 `local.properties`에 다음 값을 넣습니다.

```properties
GEMINI_KEY=Your key
```

NEIS 인증키는 Android 앱에 포함하지 않습니다. `CLOUDFLARE_SETUP.md`의 안내에 따라 Cloudflare Worker Secret의 `NEIS_API_KEY`로 등록합니다. 서버가 아직 배포되지 않은 개발 환경에서는 NEIS의 제한적인 무키 호출과 로컬 캐시로 자동 폴백합니다.


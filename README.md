# GunpoSchoolAndroid

군포고등학교 SwiftUI 앱을 Android용 Jetpack Compose로 이식한 프로젝트입니다.

## 현재 구현

- 홈: 학생 정보, 현재/다음 수업, 오늘 급식, 다가오는 학사일정
- 주간 시간표 및 8교시 설정 반영
- 이전 주·다음 주 시간표 이동 및 월 경계 자동 동기화
- 2026년 여름방학 급식 달력
- 학교 홈페이지 공지·가정통신문 실시간 동기화와 앱 내부 상세 화면
- DataStore 기반 이름·학번·학년·반, 8교시, 야간자습 설정
- 2학기 보충 과목·교사 선택과 월·목/화·금 8교시, 목요일 9·10교시 자동 반영
- Android 16 promoted ongoing 실시간 수업 알림 및 교시 경계 자동 갱신
- 학교 반경 200m 진입·이탈 Geofencing 자동 시작/종료
- 현재/다음 수업 Glance 홈 화면 위젯
- 시간표·급식·현재 수업·8교시·야자를 답하는 오프라인 학교 AI
- Cloudflare Worker 기반 NEIS 급식·연간 학사일정·학년/반별 시간표 동기화
- NEIS 시간표가 없는 날짜는 D1의 2026학년도 2학기 임시 시간표로 보완하고, 공식 NEIS 등록 시 날짜 단위로 자동 교체
- 2학년 선택수업은 지정 시간만 `2A`~`2D`로 표시하고 `2E`는 `논리와 사고`, `2F`는 `인간과 경제활동`으로 표시
- 월·금 7교시와 수요일 4~5교시 자율, 수요일 6~7교시 NEIS 학사일정 기반 동아리·교육 시간표 보완
- 법정공휴일 전 교시 자습, 설날·추석·수능 당일 자습실 미운영, 학력평가 공식 시험 시간표 자동 전환
- 위탁 식단표 이미지에서 확인한 2026년 8~9월 석식을 D1에서 급식 정보와 병합
- Firebase SMS로 전화번호 수신 가능 여부를 확인하고 이름·학번·전화번호를 별도 D1에 암호화 저장
- 서버의 시간당 공지 수집, 무료 D1 저장, 홈페이지 지문 불일치 시 즉시 재동기화
- 네트워크 실패 시 마지막 NEIS 응답 및 내장 데이터 자동 폴백
- AI 응답: Firebase AI Logic의 Gemini 사용(학교 데이터 질문은 확인된 앱 데이터만 근거로 답변)
- 실시간 웹 검색: Tavily 무료 keyless 검색과 Cloudflare Workers AI 요약, 출처 링크와 확인 시각 표시
- 앱 아이콘 빠른 실행: 오늘 시간표, 오늘 급식, 학교 AI
- API 33(Android 13) 이상 지원

## 실행

1. Android Studio에서 이 폴더를 엽니다.
2. SDK Manager에서 Android API 37을 설치하고 Gradle JDK는 Android Studio 내장 JBR 또는 JDK 17/21로 선택합니다.
3. Gradle Wrapper가 포함되어 있습니다. Linux/macOS는 `./gradlew`, Windows는 `gradlew.bat`을 사용합니다.
4. Gradle Sync 후 에뮬레이터 또는 기기에서 `app`을 실행합니다.

### Linux에서 빌드와 Worker 검증

```sh
./scripts/dev.sh setup
./scripts/dev.sh android :app:assembleDebug :app:testDebugUnitTest
./scripts/dev.sh worker
```

`setup`은 Git에서 제외되는 `.tools/`에 Node.js 24.19.0과 pnpm 11.25.0을 설치합니다.
`android`는 설치된 Android Studio의 JBR을 찾아 사용합니다. `worker`는 타입 검사,
모의 인증 회귀 테스트, 두 D1 DB의 격리된 로컬 마이그레이션과 HTTP 검증, 배포 파일 생성을 실행합니다.
자세한 개발·릴리스·Cloudflare 연결 절차는 `DEV_SETUP.md`를 참고하세요.

Firebase AI Logic을 구성하지 않은 개발 빌드에서만 직접 Gemini 키를 사용하려면 Git에서 제외되는 `local.properties`에 다음 값을 넣습니다.

```properties
GEMINI_KEY=Your key
```

NEIS 인증키는 Android 앱에 포함하지 않습니다. `CLOUDFLARE_SETUP.md`의 안내에 따라 Cloudflare Worker Secret의 `NEIS_API_KEY`로 등록합니다. 서버가 아직 배포되지 않은 개발 환경에서는 NEIS의 제한적인 무키 호출과 로컬 캐시로 자동 폴백합니다.

## 분반 조회 웹

앱의 분반 확인 화면은 Cloudflare Worker의 `/class-align`을 사용합니다.
CSV 가져오기와 갱신 방법은 [ENROLLMENTS_SETUP.md](ENROLLMENTS_SETUP.md)를 참고하세요.

## Gemini 실시간 웹 검색

Gemini 화면의 **웹 검색**을 켜거나 `최신`, `실시간`, `검색해줘` 등의 질문을 입력하면
Tavily의 공개 웹 검색 결과 최대 5개를 받아 Cloudflare Workers AI가 출처 번호와 함께 답합니다.
시간표·급식처럼 일반적인 학교 데이터 질문은 기존 앱 데이터로 답합니다.
검색 실패나 한도 초과 시 실패를 안내하며 검색 없이 만든 답변을 최신 검색 결과로 표시하지 않습니다.
AI 요약 연결이 실패해도 실제 검색 결과와 출처는 표시합니다.

검색에는 별도 API 키나 가입이 필요 없는 [Tavily keyless](https://docs.tavily.com/documentation/keyless)를 사용합니다.
무료 체험/가벼운 사용에 적합하며 제공자 사용량 제한이 있습니다. 자동 유료 전환은 없습니다.
검색 제공자에는 현재 질문만 보내며 학생 설정과 대화 기록은 보내지 않습니다.
개인정보는 웹 검색 질문에 입력하지 않는 것이 좋습니다.
큰 규모의 운영에는 별도 Tavily 무료 계정(월 1,000 credits)을 만들어 서버에 키를 보관하는 방식으로 확장할 수 있습니다.
현재 구현은 APK에 검색 API 키를 포함하지 않습니다.

웹 리서치의 요약에는 `@cf/qwen/qwen3-30b-a3b-fp8`를 사용합니다.
최신 뉴스는 최근 1주, 오늘 뉴스는 1일 범위에서 조회하며, 질문에 과거 연도나 기간을 지정하면 이를 강제로 덮어쓰지 않습니다.
`POST /v1/research/summary`는 Firebase 로그인 토큰을 검증하고, 요약 요청을 사용자당 하루 10회,
앱 전체 하루 100회로 제한합니다(UTC 자정 초기화). D1의 원자적 예약으로 동시 요청에도 한도를 지킵니다.
입력 16,000 UTF-8 bytes, 출력 700 tokens를 상한으로 두어 이 경로의 사용량을
Workers AI 일일 10,000 neurons 무료 할당 안으로 제한합니다. 계정의 다른 AI 사용량과 무료 할당은 공유합니다.
검색 자료·대화·이름·학번은 DB에 저장하지 않으며 3일이 지난 요약 횟수 기록은 정리합니다.
Cloudflare에는 현재 질문과 공개 검색 자료를 전달하고 대화 기록·학생 설정은 보내지 않습니다.
Gemini나 유료 검색으로 자동 전환하지 않습니다.

**일반 Gemini 답변은 웹 리서치의 무료 경로와 별개입니다.**
Firebase 프로젝트가 Blaze인 경우 기존 일반 Gemini 답변 생성은 모델별 토큰 요금이 적용될 수 있습니다.
현재 Firebase AI Logic의 App Check replay protection 모니터링 설정과 호환되도록
Gemini 요청마다 limited-use App Check 토큰을 사용합니다.
완전한 무료 생성이 필요하면 별도 Spark 프로젝트의 Gemini Developer API 무료 한도를 검토해야 합니다.
[Firebase 요금 안내](https://firebase.google.com/docs/ai-logic/pricing)
[Workers AI 요금 안내](https://developers.cloudflare.com/workers-ai/platform/pricing/)

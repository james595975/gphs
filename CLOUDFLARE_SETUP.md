# Cloudflare 무료 데이터 서버 설정

`cloudflare/`의 Worker는 D1에 학교 데이터를 저장하며 다음 기능을 제공합니다.

- 매시간 군포고 공지사항·가정통신문 수집
- 매시간 현재 월 급식과 연간 학사일정 갱신
- 앱 조회 시 학년·반별 시간표를 NEIS에서 실시간 갱신하고 D1에 저장
- 공식 NEIS 시간표가 없는 날짜는 D1 임시 시간표를 사용하고, NEIS 등록 후에는 공식 데이터로 자동 교체
- 2학년 자율 시간과 수요일 동아리·교육 활동을 D1 보완 시간표로 병합
- 위탁급식 식단표에서 확인한 석식을 D1에서 NEIS 급식과 병합
- 매일 00:15(한국 시간)에 당월 조회 이력이 있는 학년·반 시간표 선갱신
- 앱이 홈페이지 지문 차이를 발견하면 공지 즉시 재검사
- NEIS 인증키를 암호화된 Worker Secret으로 보관

## 1. Cloudflare 로그인과 D1 생성

현재 `wrangler.jsonc`에는 `gunpo-school-data`와 `gunpo-school-profiles` 두 데이터베이스가
이미 연결되어 있습니다. 기존 Cloudflare 계정으로 작업한다면 DB를 다시 만들지 않습니다.

Linux에서는 프로젝트 루트에서 다음 명령으로 로컬 도구와 빌드 검증을 준비합니다.

```sh
./scripts/dev.sh setup
./scripts/dev.sh worker
./scripts/dev.sh worker exec wrangler login
./scripts/dev.sh worker exec wrangler whoami
```

Node.js 22.22 이상과 pnpm 11.25.0이 이미 설치되어 있으면 `cloudflare/`에서 직접 실행할 수 있습니다.

```sh
cd cloudflare
pnpm install --frozen-lockfile
pnpm exec wrangler login
```

새 Cloudflare 계정에 처음 구성하는 경우에만 두 DB를 생성합니다.

```sh
pnpm exec wrangler d1 create gunpo-school-data
pnpm exec wrangler d1 create gunpo-school-profiles
```

출력된 각각의 `database_id`를 `cloudflare/wrangler.jsonc`에서 해당 이름의 DB 설정에 반영합니다.

## 2. 테이블과 NEIS 비밀키 등록

```sh
pnpm run db:migrate:remote
pnpm exec wrangler secret put NEIS_API_KEY
```

`db:migrate:remote`는 학교 데이터 DB와 학생 프로필 DB 모두에 마이그레이션을 적용합니다.
로컬 개발은 `pnpm run db:migrate:local`을 사용합니다. `pnpm run check`는 새 임시 로컬 DB를
만들어 검증한 후 삭제하므로 기존 개발 DB나 운영 DB를 변경하지 않습니다.

비밀키는 `wrangler.jsonc`, D1, Android APK에 기록되지 않습니다. 명령 실행 후 입력 프롬프트에서 새 NEIS 키를 붙여 넣습니다.

## 3. Worker 배포

```sh
pnpm run check
pnpm run deploy
```

`pnpm run build`는 `dist/`에 배포 파일만 생성합니다. 실제 배포는 `pnpm run deploy`이며,
학생 프로필·소셜 로그인에 필요한 Secret 목록은 `docs/auth-setup.md`와 `PRIVACY_IDENTITY_SETUP.md`에 있습니다.

배포 결과에 표시되는 `https://gunpo-school.<계정>.workers.dev` 주소를 복사합니다. 브라우저에서 다음 주소가 JSON을 반환하는지 확인합니다.

```text
https://gunpo-school.<계정>.workers.dev/health
```

앱과 같은 실시간 NEIS 조회 형식은 다음과 같습니다.

```text
GET /v1/neis?grade=2&classNumber=4&year=2026&month=8&sync=true
```

`sync=true`는 D1 캐시를 우회해 공식 NEIS를 즉시 조회하고, 성공한 응답을 D1에 저장합니다.
공식 NEIS 장애 시에는 마지막 D1 저장 데이터를 `stale: true`로 반환합니다. `sync`를 생략하면
기존처럼 30분 동안 캐시를 우선 사용합니다.

응답의 `timetableSource`는 `neis`, `temporary`, `mixed`, `none` 중 하나이며,
`temporaryFallback.dates`에는 임시 시간표가 적용된 날짜가 `YYYY-MM-DD` 형식으로 포함됩니다.
현재 임시 시간표 원본은 `2026-2학기학반별임시시간표(2026.07.15).pdf`이고 D1에는
1~3학년 1~10반의 PDF 수업 890개가 2026-08-13부터 적용되도록 저장됩니다. 2학년의
월·금 7교시 및 수요일 4~5교시 자율 수업은 공통 보완 행으로 추가됩니다. 수요일 6~7교시는
NEIS 학사일정에서 `동아리활동` 날짜를 판별하고, 평가·현장체험·휴일이 아닌 나머지 정상수업
수요일을 `교육`으로 판단해 `timetable_date_overrides`에 저장합니다.

원본 PDF가 바뀌면 다음 명령으로 마이그레이션 SQL을 다시 생성한 뒤 새 마이그레이션 번호로 추가합니다.

```sh
node scripts/generate-temporary-timetable-sql.mjs <시간표.pdf> migrations/<새 번호>_temporary_timetable.sql
```

## 4. Android 앱 연결

Git에서 제외되는 프로젝트 루트 `local.properties`에 Worker 주소를 추가합니다.

```properties
CLOUDFLARE_API_BASE_URL=https://gunpo-school.<계정>.workers.dev
```

Gradle Sync 후 앱을 다시 빌드합니다. 주소가 비어 있거나 Worker에 장애가 생기면 앱은 학교 홈페이지 직접 조회와 NEIS 무키 요청으로 자동 폴백합니다.

## 보안 참고

공지·급식·학사일정은 공개 학교 정보라 읽기 API는 공개되어 있습니다. 공지 동기화 요청은 서버에서 1분 쿨다운을 적용하며, 실제 홈페이지 내용은 Worker가 직접 검증합니다. NEIS 키 자체는 어떤 API 응답에도 포함되지 않습니다.

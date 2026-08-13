# Cloudflare 무료 데이터 서버 설정

`cloudflare/`의 Worker는 D1에 학교 데이터를 저장하며 다음 기능을 제공합니다.

- 매시간 군포고 공지사항·가정통신문 수집
- 매시간 현재 월 급식과 연간 학사일정 갱신
- 앱 조회 시 학년·반별 시간표를 NEIS에서 실시간 갱신하고 D1에 저장
- 매일 00:15(한국 시간)에 당월 조회 이력이 있는 학년·반 시간표 선갱신
- 앱이 홈페이지 지문 차이를 발견하면 공지 즉시 재검사
- NEIS 인증키를 암호화된 Worker Secret으로 보관

## 1. Cloudflare 로그인과 D1 생성

```powershell
cd cloudflare
pnpm install
pnpm exec wrangler login
pnpm exec wrangler d1 create gunpo-school-data
```

출력된 `database_id`를 `cloudflare/wrangler.jsonc`의 `REPLACE_WITH_D1_DATABASE_ID`와 교체합니다.

## 2. 테이블과 NEIS 비밀키 등록

```powershell
pnpm run db:migrate:remote
pnpm exec wrangler secret put NEIS_API_KEY
```

비밀키는 `wrangler.jsonc`, D1, Android APK에 기록되지 않습니다. 명령 실행 후 입력 프롬프트에서 새 NEIS 키를 붙여 넣습니다.

## 3. Worker 배포

```powershell
pnpm run typecheck
pnpm run deploy
```

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

## 4. Android 앱 연결

Git에서 제외되는 프로젝트 루트 `local.properties`에 Worker 주소를 추가합니다.

```properties
CLOUDFLARE_API_BASE_URL=https://gunpo-school.<계정>.workers.dev
```

Gradle Sync 후 앱을 다시 빌드합니다. 주소가 비어 있거나 Worker에 장애가 생기면 앱은 학교 홈페이지 직접 조회와 NEIS 무키 요청으로 자동 폴백합니다.

## 보안 참고

공지·급식·학사일정은 공개 학교 정보라 읽기 API는 공개되어 있습니다. 공지 동기화 요청은 서버에서 1분 쿨다운을 적용하며, 실제 홈페이지 내용은 Worker가 직접 검증합니다. NEIS 키 자체는 어떤 API 응답에도 포함되지 않습니다.

# 인증 공급자 설정

앱의 기준 계정은 Firebase UID입니다. 비밀번호와 비밀번호 해시는 Firebase Authentication에만 저장하며,
Cloudflare D1에는 로그인 ID·검증 이메일·학생정보를 AES-GCM으로 암호화해서 저장합니다.

## Firebase Authentication

Firebase 프로젝트 `gphs-beta`에서 다음 공급자를 활성화합니다.

- 이메일/비밀번호와 이메일 링크
- 전화번호
- Google
- GitHub
- 카카오·네이버는 Worker OAuth와 Firebase Custom Token을 사용하므로 별도 OIDC 공급자는 필요 없습니다.

Google은 Android 앱의 SHA-1/SHA-256을 등록하고 새 `google-services.json`을 내려받아
`app/google-services.json`을 교체해야 합니다. 파일에 `default_web_client_id`가 생겨야 앱의 Google 버튼이 활성화됩니다.

GitHub Developer Settings에서 OAuth App을 만들 때 다음 값을 사용합니다.

```text
Homepage URL: https://gphs-beta.web.app
Authorization callback URL / Redirect URI:
https://gphs-beta.firebaseapp.com/__/auth/handler
```

Redirect URI를 여러 개 등록할 수 있는 GitHub App 화면에서도 아래 주소 하나를 정확히 추가합니다.
와일드카드는 켜지 않습니다.

```text
https://gphs-beta.firebaseapp.com/__/auth/handler
```

그 다음 GitHub에서 발급된 Client ID와 Client Secret을 Firebase Console의
Authentication → Sign-in method → GitHub에 입력하고 활성화합니다. Client Secret은
`google-services.json`, `local.properties`, Git 저장소 또는 Cloudflare Worker에 넣지 않습니다.
Android 앱에서는 Firebase SDK가 이 Redirect URI와 `state` 검증을 관리하므로 별도의
`gunposchool://...` GitHub Redirect URI를 추가하지 않습니다.

Kakao Developers에서 카카오 로그인을 활성화하고 REST API 키와 Client Secret을 등록합니다.
REST API 키의 카카오 로그인 Redirect URI:

```text
https://gunpo-school.hjs595975.workers.dev/v1/auth/kakao/callback
```

현재 카카오 로그인에는 Firebase OIDC 업그레이드가 필요하지 않습니다.
앱 → Worker start → 카카오 로그인 → Worker callback → 앱의 일회성 코드 교환 → Firebase 로그인 순서입니다.
기존 학생 계정에서 연결한 경우 동일 Firebase UID를 유지하며, 다른 계정에 연결된 카카오는 중복 연결할 수 없습니다.

## Cloudflare Worker secrets

실제 값을 채워 각 명령을 실행합니다. 비밀값은 저장소나 채팅에 남기지 않습니다.

```shell
cd cloudflare
pnpm wrangler secret put FIREBASE_WEB_API_KEY
pnpm wrangler secret put NAVER_CLIENT_ID
pnpm wrangler secret put NAVER_CLIENT_SECRET
pnpm wrangler secret put KAKAO_REST_API_KEY
pnpm wrangler secret put KAKAO_CLIENT_SECRET
pnpm wrangler secret put FIREBASE_SERVICE_ACCOUNT_EMAIL
pnpm wrangler secret put FIREBASE_SERVICE_ACCOUNT_PRIVATE_KEY
```

Naver Developers callback URL:

```text
https://gunpo-school.hjs595975.workers.dev/v1/auth/naver/callback
```

Firebase 서비스 계정은 사용자 생성 권한을 최소 범위로 부여하고 키를 주기적으로 교체합니다.
각 공급자 키 또는 서비스 계정 키가 없으면 앱 버튼은 서버의 `503` 안내를 표시합니다.
기존 `naver_auth_sessions`, `naver_login_codes` 테이블을 공유하며, 해시 입력에 공급자를 포함하여 요청을 분리합니다.
로컬 회귀 검증: `node scripts/test-social-auth.cjs` (Node 22 이상, 실제 인증 서버 호출 없음).

계정 설정은 Google/GitHub의 Firebase 연결 상태와 카카오/네이버의 Worker `GET /v1/auth/{provider}/status`를 함께 조회합니다.
앱 복귀 및 Firebase 토큰 갱신 시 다시 조회하며, 실패는 미연결로 처리하지 않습니다.
연결됨 버튼의 확인창에서 해제하면 Google/GitHub는 Firebase `unlink`, 카카오/네이버는
`DELETE /v1/auth/{provider}/status`로 GPHS 로그인 연결을 제거합니다.
카카오/네이버 해제에는 최근 로그인과 다른 로그인 방법이 필요합니다. 공급자 웹사이트의 동의 철회와는 별개입니다.

## 이메일 링크

Firebase Authentication의 Authorized domains에 `gphs-beta.firebaseapp.com`을 유지하고 이메일 템플릿을 설정합니다.
ProjectConfig의 `mobileLinksConfig.domain`도 `gphs-beta.firebaseapp.com`으로 지정해야 하며, 앱은
`/__/auth/links` Hosting 링크를 받도록 설정되어 있습니다.
아이디 찾기 링크는 요청한 기기에 저장된 일회성 이메일 상태와 함께 확인합니다.

## 배포

```shell
cd cloudflare
pnpm wrangler d1 migrations apply gunpo-school-profiles --remote
pnpm typecheck
pnpm wrangler deploy
```

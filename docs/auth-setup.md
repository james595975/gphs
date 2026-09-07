# 인증 공급자 설정

앱의 기준 계정은 Firebase UID입니다. 비밀번호와 비밀번호 해시는 Firebase Authentication에만 저장하며,
Cloudflare D1에는 로그인 ID·검증 이메일·학생정보를 AES-GCM으로 암호화해서 저장합니다.

## Firebase Authentication

Firebase 프로젝트 `gphs-beta`에서 다음 공급자를 활성화합니다.

- 이메일/비밀번호와 이메일 링크
- 전화번호
- Google
- GitHub
- OpenID Connect 공급자 ID `oidc.kakao`

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

Kakao Developers에서 OpenID Connect를 활성화하고 REST API 키와 Client Secret을 만든 뒤,
Firebase Identity Platform의 OIDC 공급자에 아래 issuer를 등록합니다.

```text
Provider ID: oidc.kakao
Issuer: https://kauth.kakao.com
```

Kakao OIDC는 Firebase Authentication with Identity Platform 업그레이드가 필요합니다.

## Cloudflare Worker secrets

실제 값을 채워 각 명령을 실행합니다. 비밀값은 저장소나 채팅에 남기지 않습니다.

```shell
cd cloudflare
pnpm wrangler secret put FIREBASE_WEB_API_KEY
pnpm wrangler secret put NAVER_CLIENT_ID
pnpm wrangler secret put NAVER_CLIENT_SECRET
pnpm wrangler secret put FIREBASE_SERVICE_ACCOUNT_EMAIL
pnpm wrangler secret put FIREBASE_SERVICE_ACCOUNT_PRIVATE_KEY
```

Naver Developers callback URL:

```text
https://gunpo-school.hjs595975.workers.dev/v1/auth/naver/callback
```

Firebase 서비스 계정은 사용자 생성 권한을 최소 범위로 부여하고 키를 주기적으로 교체합니다.
Naver 키 또는 서비스 계정 키가 없으면 앱 버튼은 서버의 `503` 안내를 그대로 표시합니다.

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

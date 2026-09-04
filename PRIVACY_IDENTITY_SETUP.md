# 학생 SMS 인증 및 개인정보 저장

학생 프로필은 학교 데이터와 분리한 Cloudflare D1 `gunpo-school-profiles`에 저장한다.
Firebase SMS 인증은 입력한 전화번호로 인증문자를 받을 수 있다는 사실만 확인한다. 이름과 학번은
학생이 직접 입력한 값이며 학교·통신사·공공 본인확인기관이 검증한 실명 정보로 표시하면 안 된다.

## 처리 흐름

1. 앱에서 이름, 5자리 학번, 휴대전화 번호를 입력한다.
2. Firebase Authentication이 SMS 코드를 발송하고 앱에서 코드를 확인한다.
3. 앱은 Firebase ID 토큰과 이름·학번·동의 버전을 Worker의 `PUT /v1/profile`로 전송한다.
4. Worker는 Google 공개 인증서로 토큰 서명, 발급자, 대상 프로젝트, 만료시간, `phone` 로그인 방식을 검증한다.
5. Worker는 전화번호·이름·학번을 AES-256-GCM으로 암호화하고, Firebase UID와 전화번호 검색키는
   각각 HMAC-SHA-256으로 처리해 별도 D1에 저장한다.
6. 인증한 사용자는 `GET /v1/profile`로 자신의 정보만 조회하고 `DELETE /v1/profile`로 삭제할 수 있다.

프로필은 마지막 동의·저장 시점부터 최대 1년간 보관하며, 조회 시 기한이 지났다면 즉시 삭제한다.

## 운영 전에 반드시 설정할 항목

- Firebase Console > Authentication > Sign-in method에서 전화번호 로그인을 활성화한다.
- Android 앱의 릴리스 SHA-256(Play Integrity)과 SHA-1(reCAPTCHA 대체 경로)을 Firebase에 등록한다.
- 테스트 전화번호로 인증 흐름을 먼저 검증하고 SMS 할당량·과금·악용 방지 정책을 확인한다.
- `PROFILE_ENCRYPTION_KEY`에는 `openssl rand -base64 32` 결과를, `PROFILE_HASH_KEY`에는 별도 난수를
  Wrangler secret으로 등록한다. 두 값은 저장소나 D1에 기록하지 않는다.
- 실제 운영 주체, 개인정보 보호 담당 연락처, 수집 목적과 항목, 보유기간, 삭제 방법을 개인정보
  처리방침에 명시한다.
- Firebase로 전화번호가 전송되는 처리와 Cloudflare D1 저장 위치에 대한 개인정보 국외 이전 고지를
  실제 계약·리전 정보에 맞게 작성한다. 앱의 짧은 동의 문구만으로 법적 고지를 대신하지 않는다.

공식 문서:

- Firebase Android 전화번호 인증: https://firebase.google.com/docs/auth/android/phone-auth
- Firebase ID 토큰 서버 검증: https://firebase.google.com/docs/auth/admin/verify-id-tokens
- Cloudflare D1 데이터 보안: https://developers.cloudflare.com/d1/reference/data-security/

# Firebase AI Logic + App Check 설정

앱 패키지 이름은 `kr.hs.gunpo.school`입니다. `google-services.json`이 없을 때는 Firebase 코드가 비활성화되어 기존 개발 빌드가 계속 동작합니다.

## 1. Firebase 프로젝트와 Android 앱 등록

1. [Firebase Console](https://console.firebase.google.com/)에서 프로젝트를 만듭니다. Analytics는 필수가 아닙니다.
2. Android 앱 추가를 누르고 패키지 이름에 `kr.hs.gunpo.school`을 입력합니다.
3. 이 문서 아래에 적힌 SHA-256 인증서 지문을 등록합니다.
4. `google-services.json`을 다운로드해 `app/google-services.json` 위치에 둡니다.

현재 PC의 debug 서명 SHA-256:

```text
76:3A:D1:1C:18:07:C7:60:5A:F4:03:E6:7F:ED:A9:90:92:96:6C:CD:54:E6:D3:BB:E1:FA:CF:95:86:02:FC:99
```

## 2. AI Logic 켜기

1. Firebase Console에서 **AI Services > AI Logic > Get started**로 이동합니다.
2. Gemini API 공급자는 무료로 시작할 수 있는 **Gemini Developer API**를 선택합니다.
3. 설정 완료 후 **Security > App Check > APIs**에서 **Firebase AI Logic**이 `Enforced`인지 확인합니다.

Firebase 연결 후 앱의 AI 순서는 `Gemini Nano -> Firebase AI Logic -> 정확한 규칙형 답변`입니다. Firebase가 연결된 빌드에서는 기존 직접 API 키 호출로 우회하지 않습니다. 정상 확인 후 `local.properties`의 `GEMINI_API_KEY`는 삭제해도 됩니다.

## 3. 로컬 디버그 App Check 등록

1. Android Studio에서 debug 앱을 실행하고 AI에 질문을 한 번 보냅니다.
2. Logcat에서 `DebugAppCheckProvider`를 검색합니다.
3. `Enter this debug secret into the allow list...` 뒤의 토큰을 복사합니다.
4. Firebase Console의 **Security > App Check > Apps > 메뉴(점 3개) > Manage debug tokens**에서 토큰을 등록합니다.

디버그 토큰은 인증 우회 권한이 있으므로 공유하거나 Git에 올리면 안 됩니다. 이 프로젝트는 debug 빌드에만 Debug provider를 포함합니다.

## 4. 배포용 Play Integrity

1. Firebase Console의 **Security > App Check > Apps**에서 Android 앱을 선택합니다.
2. 공급자로 **Play Integrity**를 등록합니다.
3. Play Console로 배포한다면 Play Console의 앱 서명 SHA-256도 Firebase Android 앱 설정에 추가합니다.
4. 처음에는 App Check 요청 지표에서 정상/실패 비율을 확인한 뒤 적용 범위를 확정합니다.

release 빌드는 자동으로 Play Integrity provider를 사용합니다. Play 스토어 밖에서 APK를 직접 설치할 계획이라면 App Check의 Play Integrity 고급 설정에서 해당 배포 방식을 허용하는 구성이 필요합니다.

공지와 NEIS 서버는 무료 운영을 위해 Cloudflare Workers + D1을 사용합니다. Firebase는 AI Logic과 App Check 용도로만 유지됩니다. 서버 배포 방법은 `CLOUDFLARE_SETUP.md`를 참고합니다.

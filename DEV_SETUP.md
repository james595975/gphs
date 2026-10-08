# Linux 개발·빌드 및 Cloudflare 연결

프로젝트는 Jetpack Compose Android 앱과 TypeScript Cloudflare Worker로 구성됩니다.
Worker의 `DB`는 학교 데이터, `PROFILE_DB`는 학생 프로필과 인증 연결 정보를 저장합니다.
Android 앱은 `local.properties`의 `CLOUDFLARE_API_BASE_URL`을 빌드 설정에 넣어 서버를 호출합니다.

## 개발 도구 준비

```sh
./scripts/dev.sh setup
```

Node.js 24.19.0 공식 Linux 배포본의 SHA-256을 확인한 뒤 `.tools/node/`에 설치합니다.
pnpm 11.25.0은 `.tools/pnpm/`에 설치하고, `cloudflare/pnpm-lock.yaml`에 고정된 의존성을 복원합니다.
셸 설정이나 전역 Node 설치를 변경하지 않습니다. 이후 명령은 프로젝트의 `scripts/dev.sh`로 실행합니다.
전역 Node.js 22.22 이상과 pnpm이 있으면 `cloudflare/`에서 직접 pnpm을 사용해도 됩니다.

Android Studio의 SDK Manager에서 API 37과 Build Tools 36.0.0 이상을 설치합니다.
`local.properties`의 `sdk.dir`은 실제 SDK 경로여야 합니다. 이 컴퓨터의 경로는 `/home/james/Android/Sdk`입니다.
다른 컴퓨터에 복사할 때는 이 경로를 해당 컴퓨터의 SDK 경로로 바꿉니다.

`scripts/dev.sh android`는 `JAVA_HOME`을 우선 사용하고, 설정되지 않았으면
`~/android-studio/jbr`, `/opt/android-studio/jbr`, Snap Android Studio의 JBR을 순서대로 확인합니다.
별도 JDK를 쓴다면 JDK 17/21 등 Gradle 9.3.1과 호환되는 JDK로 `JAVA_HOME`을 지정합니다.
Gradle 배포 파일의 checksum과 공식 Wrapper JAR의 일치를 확인했습니다.

## Android 앱

```sh
./scripts/dev.sh android :app:assembleDebug :app:testDebugUnitTest
```

디버그 APK: `app/build/outputs/apk/debug/app-debug.apk`

기존 단위 테스트를 캐시 없이 다시 실행하려면:

```sh
./scripts/dev.sh android :app:testDebugUnitTest --rerun
```

릴리스 APK와 Play 업로드용 AAB:

```sh
./scripts/dev.sh android :app:assembleRelease :app:bundleRelease
```

릴리스 서명은 Git에서 제외되는 `local.properties`의 `RELEASE_STORE_FILE`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`를 사용합니다.
서명 정보가 없는 환경의 릴리스 산출물은 배포 서명이 완료되지 않을 수 있으므로 별도로 확인합니다.
Firebase 설정은 `app/google-services.json`을 사용하며 서버 Secret을 APK에 추가하지 않습니다.

## Cloudflare Worker 검증과 로컬 개발

```sh
./scripts/dev.sh worker
```

이 명령은 다음 검증을 순서대로 실행합니다.

1. TypeScript 타입 검사
2. 카카오·네이버 로그인, 연결, 충돌, 재사용, PKCE, 만료, 연결 해제의 모의 인증 테스트
3. 두 D1 DB에 모든 마이그레이션을 적용하고 실제 workerd에서 health, 시간, CORS,
   비인증 프로필 접근, 존재하지 않는 URL을 검증하는 로컬 테스트
4. Wrangler의 `--dry-run`으로 `cloudflare/dist/`에 배포 파일 생성

로컬 테스트 DB는 `.wrangler/local-check-*`에 새로 만들고 종료 시 삭제합니다.
로컬 서버와 기존 개발 DB를 직접 사용하려면:

```sh
./scripts/dev.sh worker run db:migrate:local
./scripts/dev.sh worker run dev
```

학교 데이터 DB 7개, 학생 프로필 DB 6개 마이그레이션이 포함되어 있습니다.
Secret이 필요한 실제 인증/학교 데이터 API를 시험할 때만
`cloudflare/.dev.vars.example`을 `cloudflare/.dev.vars`로 복사해 필요한 값을 채웁니다.
모의 인증 테스트, health, 시간, DB 마이그레이션에는 실제 Secret이 필요하지 않습니다.

## 기존 Cloudflare 계정으로 연결

기존 Worker는 `https://gunpo-school.hjs595975.workers.dev`입니다.
`wrangler.jsonc`에 두 D1 DB ID와 `gphs-beta` Firebase 프로젝트가 설정되어 있습니다.
다른 계정으로 이동하는 경우에만 DB를 새로 만들고 각 DB ID와 앱의 API 주소를 수정합니다.

```sh
./scripts/dev.sh worker exec wrangler login
./scripts/dev.sh worker exec wrangler whoami
./scripts/dev.sh worker exec wrangler d1 migrations list gunpo-school-data --remote
./scripts/dev.sh worker exec wrangler d1 migrations list gunpo-school-profiles --remote
./scripts/dev.sh worker exec wrangler secret list
```

위 명령은 계정 연결과 운영 설정 확인용입니다. 실제 운영 반영은 내용을 확인한 뒤 별도로 실행합니다.

```sh
./scripts/dev.sh worker run db:migrate:remote
./scripts/dev.sh worker run deploy
curl --fail https://gunpo-school.hjs595975.workers.dev/health
```

필요한 서버 Secret과 인증 공급자 callback URL은 `docs/auth-setup.md`와
`PRIVACY_IDENTITY_SETUP.md`를 참고합니다. Secret 값, Firebase Admin 개인키, 서명 keystore는
Git에 넣지 않습니다. 앱은 `CLOUDFLARE_API_BASE_URL`이 바뀌면 다시 빌드해야 합니다.

## 공식 문서

- [AGP 9.1.1과 Gradle/JDK 요구 사항](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [Cloudflare 로컬 개발](https://developers.cloudflare.com/workers/local-development/)
- [D1 로컬 개발](https://developers.cloudflare.com/d1/best-practices/local-development/)
- [Wrangler D1 명령](https://developers.cloudflare.com/workers/wrangler/commands/d1/)

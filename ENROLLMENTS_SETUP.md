# 선택과목 분반 조회

앱의 **분반 확인**은 `CLOUDFLARE_API_BASE_URL/class-align`을 엽니다.
같은 Worker의 `/`에서도 웹 화면을 열 수 있습니다. 기존 Vercel 사이트 의존성은 제거했습니다.

학번과 이름이 모두 일치할 때 2A~2F의 과목, 분반, 해당 분반 학생 목록을 보여줍니다.
분반은 **블록 + 과목 + 분반 번호**로 구분합니다. 과목이 다르면 같은 번호여도 합치지 않습니다.
공유 IP를 사용하는 학교 Wi-Fi를 고려해 IP당 15분에 300회까지 조회할 수 있습니다.
필요하면 `wrangler.jsonc`의 `ENROLLMENT_RATE_LIMIT`을 조정하세요.

## CSV 가져오기

CSV 열: `id,student_id,student_name,block,subject,class_num`.
학번은 문자열로 처리하며 5자리 숫자여야 합니다. 각 학생은 각 블록에 한 번씩 배정되어야 합니다.
빈 값, 중복 행 ID, 중복 블록, 학번별 이름 불일치는 업로드 전에 거부합니다.

처음에는 마이그레이션을 적용하고 데이터를 준비합니다.

```bash
cd /home/james/Desktop/gphs-main
./scripts/dev.sh worker run db:profiles:migrate:local
./scripts/dev.sh worker run enrollments:import --csv /home/james/Downloads/enrollments_rows.csv --local
./scripts/dev.sh worker run dev
```

암호화 SQL만 준비하려면 `--local` 대신 `--prepare`를 사용하세요.
로컬 가져오기는 `.dev.vars`에 `ENROLLMENT_KEY`를 설정합니다. 다른 로컬 설정은 보존합니다.

원격 반영:

```bash
./scripts/dev.sh worker exec wrangler whoami
./scripts/dev.sh worker run db:profiles:migrate:remote
./scripts/dev.sh worker run enrollments:import --csv /home/james/Downloads/enrollments_rows.csv --remote
./scripts/dev.sh worker run check
./scripts/dev.sh worker run deploy
```

`--remote`는 기존 `gunpo-school-profiles` D1 DB에 데이터를 넣고 Worker secret을 등록합니다.
같은 명령으로 새 CSV를 반영할 수 있습니다. 새 버전의 모든 행을 넣은 다음 활성 버전을 바꿉니다.
실패한 가져오기를 재실행해도 같은 버전의 행은 중복되지 않습니다.

## 학생 정보 저장

- 학번·이름은 AES-256-GCM으로 암호화합니다. 조회 인덱스는 비밀 키를 사용하는 HMAC-SHA256입니다.
- `ENROLLMENT_KEY`에서 서로 다른 용도의 암호화 키와 조회 키를 유도합니다.
- 생성한 키와 SQL은 Git에서 제외된 `cloudflare/.enrollments/`에만 저장됩니다.
- **`.enrollments/key`를 안전하게 백업하세요. 키를 잃거나 바꾸면 기존 암호화 데이터 조회가 불가능합니다.**
- 기존 데이터가 있는데 이 PC에 원격 등록 기록이 없으면 가져오기를 거부합니다. 원래 키와 `.enrollments/remote-key-registered`를 복원해야 합니다.
- CSV 원본은 웹 리소스나 Git에 포함하지 않습니다. 전체 학생 명단을 공개하는 API도 없습니다.
- 조회는 `POST /v1/enrollments/lookup`에 `studentId`, `studentName`을 JSON으로 전달합니다.
- 학번·이름은 계정 로그인 인증이 아닙니다. 정확히 일치하는 학생의 분반과 같은 반 학생을 보여주는 기존 사이트의 조회 방식을 유지합니다.
- 응답은 `no-store`이며 브라우저의 URL, 쿠키, 로컬 저장소에 학생 정보를 저장하지 않습니다.

## 검증

`./scripts/dev.sh worker run check`는 타입 검사, 기존 소셜 로그인 테스트,
로컬 D1 마이그레이션, 암호화된 가상 학생 CSV 가져오기, 실제 Worker HTTP 조회와 반복 요청 제한,
배포 번들 생성을 검사합니다. 실제 학생 정보를 테스트 fixture로 사용하지 않습니다.

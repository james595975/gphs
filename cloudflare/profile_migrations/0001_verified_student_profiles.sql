-- SMS 인증 및 개인정보 처리방침/동의 절차가 완료되기 전에는 이 DB에 데이터를 넣지 않는다.
-- 전화번호, 이름, 학년, 반, 번호는 하나의 JSON으로 묶어 AES-GCM 암호화한 뒤 ciphertext만 저장한다.
-- provider_subject_hash와 token_hash는 Worker 비밀키로 HMAC-SHA-256 처리한 값만 저장한다.
CREATE TABLE IF NOT EXISTS verified_student_profiles (
  provider_subject_hash TEXT PRIMARY KEY,
  verification_provider TEXT NOT NULL CHECK(verification_provider IN ('pass', 'ipin')),
  verification_reference_hash TEXT NOT NULL UNIQUE,
  profile_ciphertext TEXT NOT NULL,
  profile_iv TEXT NOT NULL,
  encryption_key_version INTEGER NOT NULL DEFAULT 1,
  consent_version TEXT NOT NULL,
  consented_at INTEGER NOT NULL,
  verified_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_verified_student_profiles_expires_at
ON verified_student_profiles(expires_at);

CREATE TABLE IF NOT EXISTS profile_sessions (
  token_hash TEXT PRIMARY KEY,
  provider_subject_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  revoked_at INTEGER,
  FOREIGN KEY (provider_subject_hash)
    REFERENCES verified_student_profiles(provider_subject_hash)
    ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_profile_sessions_subject
ON profile_sessions(provider_subject_hash);

CREATE INDEX IF NOT EXISTS idx_profile_sessions_expires_at
ON profile_sessions(expires_at);

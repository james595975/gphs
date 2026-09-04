-- Firebase SMS로 전화번호 소유를 확인한다. 이름과 학번은 사용자 입력값이며
-- 학교 또는 공공 본인확인기관이 확인한 정보로 취급하지 않는다.
PRAGMA foreign_keys = OFF;

CREATE TABLE verified_student_profiles_new (
  provider_subject_hash TEXT PRIMARY KEY,
  verification_provider TEXT NOT NULL CHECK(verification_provider IN ('pass', 'ipin', 'firebase_sms')),
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

INSERT INTO verified_student_profiles_new
SELECT * FROM verified_student_profiles;

DROP TABLE profile_sessions;
DROP TABLE verified_student_profiles;
ALTER TABLE verified_student_profiles_new RENAME TO verified_student_profiles;

CREATE INDEX idx_verified_student_profiles_expires_at
ON verified_student_profiles(expires_at);

CREATE TABLE profile_sessions (
  token_hash TEXT PRIMARY KEY,
  provider_subject_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  revoked_at INTEGER,
  FOREIGN KEY (provider_subject_hash)
    REFERENCES verified_student_profiles(provider_subject_hash)
    ON DELETE CASCADE
);

CREATE INDEX idx_profile_sessions_subject
ON profile_sessions(provider_subject_hash);

CREATE INDEX idx_profile_sessions_expires_at
ON profile_sessions(expires_at);

PRAGMA foreign_keys = ON;

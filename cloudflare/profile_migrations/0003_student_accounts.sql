-- Passwords and password hashes stay in Firebase Authentication. D1 only keeps
-- a deterministic lookup hash plus an AES-GCM encrypted login ID/recovery email.
CREATE TABLE IF NOT EXISTS student_accounts (
  provider_subject_hash TEXT PRIMARY KEY,
  login_id_hash TEXT NOT NULL UNIQUE,
  email_hash TEXT NOT NULL UNIQUE,
  account_ciphertext TEXT NOT NULL,
  account_iv TEXT NOT NULL,
  encryption_key_version INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY (provider_subject_hash)
    REFERENCES verified_student_profiles(provider_subject_hash)
    ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_student_accounts_email_hash
ON student_accounts(email_hash);

CREATE TABLE IF NOT EXISTS auth_rate_limits (
  rate_key TEXT PRIMARY KEY,
  window_started_at INTEGER NOT NULL,
  request_count INTEGER NOT NULL,
  blocked_until INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_rate_limits_updated_at
ON auth_rate_limits(updated_at);

CREATE TABLE IF NOT EXISTS naver_auth_sessions (
  state_hash TEXT PRIMARY KEY,
  code_challenge TEXT NOT NULL,
  firebase_uid_ciphertext TEXT,
  firebase_uid_iv TEXT,
  expires_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_naver_auth_sessions_expires_at
ON naver_auth_sessions(expires_at);

CREATE TABLE IF NOT EXISTS social_identity_links (
  provider TEXT NOT NULL,
  provider_user_hash TEXT NOT NULL,
  firebase_uid_ciphertext TEXT NOT NULL,
  firebase_uid_iv TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(provider, provider_user_hash)
);

CREATE TABLE IF NOT EXISTS firebase_social_links (
  provider_subject_hash TEXT NOT NULL,
  provider TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY(provider_subject_hash, provider)
);

CREATE TABLE IF NOT EXISTS naver_login_codes (
  code_hash TEXT PRIMARY KEY,
  code_challenge TEXT NOT NULL,
  custom_token_ciphertext TEXT NOT NULL,
  custom_token_iv TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_naver_login_codes_expires_at
ON naver_login_codes(expires_at);

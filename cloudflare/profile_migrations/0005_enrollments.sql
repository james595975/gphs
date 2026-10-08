-- Student identifiers are HMAC lookup keys; names/numbers are AES-GCM encrypted.
CREATE TABLE IF NOT EXISTS enrollment_students (
  dataset_id TEXT NOT NULL,
  lookup_hash TEXT NOT NULL,
  identity_ciphertext TEXT NOT NULL,
  identity_iv TEXT NOT NULL,
  PRIMARY KEY(dataset_id, lookup_hash)
);
CREATE TABLE IF NOT EXISTS enrollments (
  dataset_id TEXT NOT NULL,
  lookup_hash TEXT NOT NULL,
  block TEXT NOT NULL,
  subject TEXT NOT NULL,
  class_num INTEGER NOT NULL CHECK(class_num > 0),
  PRIMARY KEY (dataset_id, lookup_hash, block),
  FOREIGN KEY(dataset_id, lookup_hash) REFERENCES enrollment_students(dataset_id, lookup_hash) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_enrollments_class ON enrollments(dataset_id, block, subject, class_num);
CREATE TABLE IF NOT EXISTS enrollment_dataset (
  id INTEGER PRIMARY KEY CHECK(id = 1),
  source_sha256 TEXT NOT NULL,
  imported_at TEXT NOT NULL,
  student_count INTEGER NOT NULL,
  enrollment_count INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS enrollment_lookup_limits (
  rate_key TEXT PRIMARY KEY,
  window_started_at INTEGER NOT NULL,
  request_count INTEGER NOT NULL
);

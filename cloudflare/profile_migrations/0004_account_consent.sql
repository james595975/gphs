ALTER TABLE student_accounts
ADD COLUMN consent_version TEXT NOT NULL DEFAULT '2026-09-07';

ALTER TABLE student_accounts
ADD COLUMN consented_at INTEGER NOT NULL DEFAULT 0;

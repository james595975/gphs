CREATE TABLE IF NOT EXISTS data_cache (
    cache_key TEXT PRIMARY KEY,
    value_json TEXT NOT NULL,
    fingerprint TEXT,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_data_cache_updated_at
ON data_cache(updated_at);

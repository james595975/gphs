CREATE TABLE IF NOT EXISTS contract_dinners (
    meal_date TEXT PRIMARY KEY,
    menu_json TEXT NOT NULL,
    grades TEXT NOT NULL DEFAULT '1,2,3',
    source TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_contract_dinners_month
ON contract_dinners(substr(meal_date, 1, 7));

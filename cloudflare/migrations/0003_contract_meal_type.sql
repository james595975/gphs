ALTER TABLE contract_dinners
ADD COLUMN meal_type TEXT NOT NULL DEFAULT '석식';

CREATE INDEX IF NOT EXISTS idx_contract_dinners_type_date
ON contract_dinners(meal_type, meal_date);

-- Store only irreversible user budget keys and counts; never search questions or excerpts.
CREATE TABLE IF NOT EXISTS research_daily_budget (
  budget_day TEXT NOT NULL,
  budget_key TEXT NOT NULL,
  request_count INTEGER NOT NULL,
  PRIMARY KEY (budget_day, budget_key)
);

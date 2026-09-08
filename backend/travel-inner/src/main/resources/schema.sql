-- ── Travel records ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS travel_records (
    id            VARCHAR(36)   PRIMARY KEY,
    owner_uuid    VARCHAR(36)   NOT NULL,
    title         VARCHAR(255)  NOT NULL,
    start_date    VARCHAR(10)   NOT NULL,   -- YYYY-MM-DD
    end_date      VARCHAR(10)   NOT NULL,   -- YYYY-MM-DD
    stops_json    TEXT,                     -- JSON array of stops
    expenses_json TEXT,                     -- JSON array of expenses
    notes         TEXT,
    allow_chat    BOOLEAN       NOT NULL DEFAULT FALSE,  -- opt-in: expose this trip to the chat agent
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_travel_owner (owner_uuid)
);

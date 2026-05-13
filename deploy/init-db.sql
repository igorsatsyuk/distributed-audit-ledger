-- ============================================================
-- Distributed Audit Ledger — initial database schema
-- Applied automatically by PostgreSQL on first container start
-- ============================================================

CREATE SCHEMA IF NOT EXISTS audit;

-- ---------------------------------------------------------------
-- audit.events  (core event store, written by event-store-service)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit.events (
    id           BIGSERIAL    PRIMARY KEY,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type   VARCHAR(128) NOT NULL,
    user_id      VARCHAR(255),
    payload      JSONB        NOT NULL,
    event_hash   VARCHAR(64),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_events_aggregate_id ON audit.events (aggregate_id);
CREATE INDEX IF NOT EXISTS idx_events_event_type   ON audit.events (event_type);
CREATE INDEX IF NOT EXISTS idx_events_user_id      ON audit.events (user_id);
CREATE INDEX IF NOT EXISTS idx_events_created_at   ON audit.events (created_at);

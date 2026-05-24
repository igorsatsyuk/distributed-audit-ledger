-- flyway:executeInTransaction=false
-- Enable pg_trgm extension for trigram-based full-text search on JSONB payload.
-- This index makes ILIKE '%...%' queries on payload::text use a GIN index scan
-- instead of a sequential scan, keeping search performant as audit.events grows.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Build index concurrently to avoid long ACCESS EXCLUSIVE locks on audit.events.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_payload_trgm
    ON audit.events USING GIN ((payload::text) gin_trgm_ops);

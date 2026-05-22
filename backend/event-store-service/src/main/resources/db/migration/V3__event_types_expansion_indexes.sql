-- ============================================================
-- V3__event_types_expansion_indexes.sql
-- Query optimization for larger event type matrix (issue #14).
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_events_type_created_at
	ON audit.events (event_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_events_user_type_created_at
	ON audit.events (user_id, event_type, created_at DESC)
	WHERE user_id IS NOT NULL;


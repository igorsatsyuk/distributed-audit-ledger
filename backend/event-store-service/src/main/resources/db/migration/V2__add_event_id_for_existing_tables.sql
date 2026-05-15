-- ============================================================
-- V2__add_event_id_for_existing_tables.sql
-- Backfill path for environments that already had audit.events
-- without event_id (for example from early init-db.sql versions).
-- ============================================================

ALTER TABLE audit.events
    ADD COLUMN IF NOT EXISTS event_id VARCHAR(36);

-- Populate stable IDs for legacy rows before enforcing constraints.
UPDATE audit.events
SET event_id = CONCAT('legacy-', id::text)
WHERE event_id IS NULL;

ALTER TABLE audit.events
    ALTER COLUMN event_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_events_event_id'
          AND conrelid = 'audit.events'::regclass
    ) THEN
        ALTER TABLE audit.events
            ADD CONSTRAINT uk_events_event_id UNIQUE (event_id);
    END IF;
END
$$;


# Event Hash Canonical Migration

This runbook is intended for environments where `audit.events` already contains
historical rows that were created before the switch to canonical JSON serialization.

## Why this matters

`event_hash` is calculated as `SHA-256(payload_json)`. If the serialization rules
(field order, `Instant` format) change, the same logical event may produce a different hash.
Without this migration, the `DB event_hash == on-chain hash` check will fail for historical rows.

## Who this applies to

- **New environments with no data**: no migration is required.
- **Environments with existing data**: complete the steps below before turning on strict verification.

## Migration plan

1. **Stop the writers** (`event-store-service`, `audit-writer-service`) to prevent new writes.
2. **Back up** the `audit.events` table.
3. **Recalculate the canonical hash** for each row based on the actual `payload` and update `event_hash`.
4. **Synchronize with the blockchain (`AuditLedger`)**:
   - for rows that have not yet been anchored on-chain, backfill them via `appendAuditRecord(canonicalHash, timestamp, eventType, sourceAddress)`:
     * `canonicalHash` — the SHA-256 of the recalculated payload, passed as `bytes32` (`0x` + 64 hex chars, or the same 32 raw bytes when calling through Web3j);
     * `timestamp` — Unix epoch seconds from `audit.events.created_at`;
     * `eventType` — the event type name (e.g. `USER_LOGGED_IN`) from `audit.events.event_type`;
     * `sourceAddress` — the writer address (the owner of the `AuditLedger` contract);
   - for rows already anchored with a legacy hash, define the strategy up front: either keep a separate historical zone without strict `DB == chain`, or recreate the environment/ledger with canonical hashes;
   - make sure there are no "DB-only" records for the target dataset without an on-chain counterpart.
5. **Verify consistency**:
   - no `NULL` values in `event_hash`;
   - hashes are stored as lowercase hex and are 64 characters long;
   - spot-check several events end-to-end (DB -> blockchain).
6. **Restart the services** and enable strict verification only after all checks pass.

## Minimal SQL checks

```sql
-- 1) Empty hashes (should be 0)
SELECT count(*) AS null_hashes
FROM audit.events
WHERE event_hash IS NULL;

-- 2) Invalid format (should be 0)
SELECT count(*) AS invalid_format
FROM audit.events
WHERE event_hash IS NULL
   OR length(event_hash) <> 64
   OR event_hash !~ '^[0-9a-f]{64}$';
```

## Backfill note

The backfill must use the same canonical mapper as the services (`CanonicalObjectMapperFactory`).
This guarantees that `event-store-service` and `audit-writer-service` produce identical digests.


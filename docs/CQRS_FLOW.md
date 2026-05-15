# CQRS Runtime Flow

This document describes the runtime sequence for one event from command ingestion
to read-side consumption and blockchain anchoring.

## End-to-End Sequence

1. Client sends command to `command-service` (HTTP).
2. Command is validated and transformed into a domain event.
3. Event is published to Kafka topic `user.login.events`.
4. `event-store-service` consumes the event and writes immutable record into `audit.events`.
5. `audit-writer-service` consumes the same event stream and anchors hash on `AuditLedger`.
6. `query-service` projects/serves read models for UI/API clients.

## Data Artifacts by Stage

- Command payload (HTTP request DTO).
- Domain event payload (shared event model).
- Event-store row (`audit.events.payload`, `event_hash`).
- Blockchain transaction (`appendAuditRecord`, tx hash, block number).
- Query response DTO for timeline/integrity views.

## Failure/Retry Expectations

- Kafka decouples write/read/anchor paths (no direct service-to-service calls).
- Event persistence and blockchain anchoring are independent consumers.
- If blockchain write fails, retry logic should not mutate persisted event history.

## Related Docs

- Component boundaries: `docs/ARCHITECTURE.md`
- Infra setup and checks: `deploy/README.md`


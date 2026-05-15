# Architecture Overview

This document is the canonical description of component boundaries and integration points.

## Core Pattern

The platform follows CQRS + event sourcing with blockchain anchoring:

1. Command API receives business commands.
2. Command service validates and emits domain events.
3. Event store persists immutable events in PostgreSQL (`audit.events`).
4. Audit writer anchors event hashes on-chain (`AuditLedger`).
5. Query service projects read models for UI/API consumption.

## Component Boundaries

### Backend services

- `command-service` - write-side API and event emission.
- `event-store-service` - immutable event persistence and event-hash storage.
- `audit-writer-service` - blockchain anchoring worker for event hashes.
- `query-service` - read-side API for audit log browsing and integrity status.

### Shared backend modules

- `common/event-model` - shared domain event types.
- `common/shared-contracts` - shared DTOs and API contracts.

### Blockchain

- `blockchain/contracts/AuditLedger.sol` - owner-gated append-only hash ledger.
- `blockchain/scripts/deploy.js` - deployment script for local Ganache.
- `blockchain/test/AuditLedger.test.js` - contract behavior tests.

### Infrastructure

- Kafka - asynchronous event backbone.
- PostgreSQL - event store (`audit` schema).
- Ganache - local Ethereum-compatible chain.

## Integration Contracts

- Event topic: `user.login.events`.
- Event-store table: `audit.events` with `payload JSONB` and optional `event_hash`.
- Blockchain write path: event hash -> `AuditLedger.appendAuditRecord(...)`.

## Documentation Map

- Runtime sequence: `docs/CQRS_FLOW.md`
- Local infrastructure and environment: `deploy/README.md`
- Backend module run/build details: `backend/README.md`
- Blockchain compile/test/deploy details: `blockchain/README.md`


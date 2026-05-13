# Architecture Overview

## Core Pattern

The system follows CQRS + event sourcing:

1. Command API receives business commands.
2. Command service validates and emits domain events.
3. Event store persists immutable events.
4. Audit writer anchors event hashes on blockchain.
5. Query service builds read models for UI.

## Planned Components

- `command-service`
- `event-store-service`
- `audit-writer-service`
- `query-service`
- `AuditLedger` smart contract
- Angular audit UI


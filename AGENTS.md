# AGENTS.md

## Project snapshot
- Repository is in **bootstrap/planning stage**: infra + blockchain module are implemented; backend and frontend are mostly scaffolds.
- Start context in `README.md`, `START_HERE.md`, `GITHUB_ISSUES_PLAN.md`, and `docs/ARCHITECTURE.md`.
- Main folders: `backend/` (multi-module Maven), `blockchain/` (Hardhat), `deploy/` (local stack), `docs/` (CQRS/event-sourcing intent).

## Architecture and service boundaries
- Intended flow (documented in `docs/ARCHITECTURE.md` and `docs/CQRS_FLOW.md`):
  1) command API accepts commands,
  2) command service emits domain events,
  3) event store persists immutable events,
  4) audit writer anchors event hash on-chain,
  5) query service projects read models for UI.
- Backend boundaries are already defined as Maven modules in `backend/pom.xml`:
  `command-service`, `event-store-service`, `audit-writer-service`, `query-service`, plus `common/event-model` and `common/shared-contracts`.
- Treat backend contracts and event schemas as shared artifacts under `backend/common/*`.

## What is actually implemented now
- `deploy/docker-compose.yml` runs local dependencies: Postgres, ZooKeeper, Kafka, Kafka UI, Ganache, pgAdmin.
- Event store schema exists in `deploy/init-db.sql` (`audit.events` table with `payload JSONB` and optional `event_hash`).
- Smart contract is implemented in `blockchain/contracts/AuditLedger.sol`.
  - Write access is owner-only (`onlyOwner`), duplicate hashes rejected (`DuplicateHash`), ownership rotation supported (`transferOwnership`).
- Contract behavior is covered by `blockchain/test/AuditLedger.test.js` (custom errors + ownership scenarios).

## Critical workflows (use these first)
- Infra startup (from `deploy/`): copy `deploy/.env.example` to `.env`, then `docker compose up -d`.
- Infra verification:
  - Kafka topics: `docker exec dal-kafka kafka-topics --bootstrap-server localhost:9092 --list`
  - Ganache RPC: POST `eth_blockNumber` to `http://localhost:8545`
  - DB schema: check `audit.events` in Postgres/pgAdmin.
- Blockchain dev (from `blockchain/`): `npm install`, `npm run compile`, `npm test`.
- Ganache deploy: copy `blockchain/.env.example` to `.env`, set `GANACHE_PRIVATE_KEY`, then `npm run deploy:ganache`.

## Project-specific conventions to preserve
- Hardhat config intentionally omits `accounts` for Ganache when `GANACHE_PRIVATE_KEY` is invalid/missing; do not replace with `accounts: []` (`blockchain/hardhat.config.js`).
- Ganache chain assumptions are fixed by deploy stack: chainId `1337` and deterministic mnemonic (`deploy/docker-compose.yml`, `deploy/README.md`).
- Git workflow conventions are documented and reused across docs:
  - branch: `feature/#XX-description`
  - commit: `[#XX] short message`
  - PR text includes `Closes #XX` (`START_HERE.md`, `CONTRIBUTING.md`).

## Integration points and dependencies
- Event hash bridge: backend `event-store-service`/`audit-writer-service` should map DB `event_hash` to on-chain `AuditLedger.appendAuditRecord(...)`.
- Messaging backbone is Kafka (`dal-kafka`), so design async command->event->projection pipelines around topics rather than direct service calls.
- Local blockchain endpoint defaults to `http://127.0.0.1:8545` and is consumed by Hardhat deploy scripts.
- Postgres schema namespace is `audit`; use this schema explicitly in SQL and ORM mappings.

## Practical guidance for coding agents
- Do not assume existing Spring/Angular app code; create minimal skeletons first when implementing new backend/frontend issues.
- Align new code with the issue-driven plan in `GITHUB_ISSUES_PLAN.md` because this repo is organized around those milestones.
- When changing blockchain write paths, update tests in `blockchain/test/AuditLedger.test.js` in the same change.


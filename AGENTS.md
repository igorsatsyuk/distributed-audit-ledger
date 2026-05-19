# AGENTS.md

## Project snapshot
- **MVP Phase 1 (issues #1–#13) is complete**; issue #14 (additional event types) is the next open item.
- Start context in `README.md`, `START_HERE.md`, `docs/ROADMAP.md`, and `docs/ARCHITECTURE.md`.
- Main folders: `backend/` (multi-module Maven), `blockchain/` (Hardhat), `deploy/` (local stack), `docs/` (CQRS/event-sourcing intent), `frontend/audit-ui/` (Angular 17+).

## Architecture and service boundaries
- Intended flow (documented in `docs/ARCHITECTURE.md` and `docs/CQRS_FLOW.md`):
  1) command API accepts commands,
  2) command service emits domain events,
  3) event store persists immutable events,
  4) audit writer anchors event hash on-chain,
  5) query service projects read models for UI.
- Backend boundaries are defined as Maven modules in `backend/pom.xml`:
  `command-service`, `event-store-service`, `audit-writer-service`, `query-service`, plus `common/event-model` and `common/shared-contracts`.
- Treat backend contracts and event schemas as shared artifacts under `backend/common/*`.
- All backend services use **Spring WebFlux + Project Reactor** (reactive); database access uses **Spring Data R2DBC** (not JPA).
- **`event-store-service` is the sole owner of Flyway migrations** (`src/main/resources/db/migration`). `query-service` has Flyway explicitly disabled (`flyway.enabled: false`).

## What is actually implemented now
- `deploy/docker-compose.yml` runs local dependencies: Postgres, ZooKeeper, Kafka, Kafka UI, Ganache, pgAdmin.
- Event store schema exists in `deploy/init-db.sql` (`audit.events` table with `payload JSONB` and optional `event_hash`).
- Smart contract is implemented in `blockchain/contracts/AuditLedger.sol`.
  - Write access is owner-only (`onlyOwner`), duplicate hashes rejected (`DuplicateHash`), ownership rotation supported (`transferOwnership`).
- Contract behavior is covered by `blockchain/test/AuditLedger.test.js` (custom errors + ownership scenarios).
- **`command-service` (port 8081)**: WebFlux `POST /commands/user/login`, publishes `UserLoggedInEvent` to Kafka topic `user.login.events`; temporary in-memory bounded storage.
- **`event-store-service` (port 8082)**: Kafka consumer on `user.login.events` (group `event-store-consumer`), persists to `audit.events` via R2DBC, computes SHA-256 `event_hash`, owns Flyway migrations.
- **`audit-writer-service` (port 8083)**: Kafka consumer on `user.login.events` (group `audit-writer-consumer`), anchors SHA-256 hash on-chain via Web3j (`appendAuditRecord`); uses dead-letter topic `user.login.events.dlt`.
- **`query-service` (port 8084)**: WebFlux read API — `GET /api/audit-logs` (filters: `userId`, `eventType`, `from`, `to`, `limit`, `offset`) and `GET /api/audit-logs/{id}`; also exposes `GET /api/audit-logs/{id}/integrity-check` (status: `ON_CHAIN` / `MISMATCH` / `PENDING`).
- **`common/event-model`**: `AuditEvent`, `UserLoggedInEvent`, `EventType`.
- **`common/shared-contracts`**: `UserLoginCommand`, `AuditEventDto`, `CommandResponse`, `CanonicalObjectMapperFactory`.
- **Frontend** (`frontend/audit-ui/`): Angular 17 + Material, `audit-dashboard` feature component, `AuditLogService` calling `query-service`; issue #11 (live data integration) is complete.

## Critical workflows (use these first)
- Infra startup (from `deploy/`): copy `deploy/.env.example` to `.env`, then `docker compose up -d`.
- Infra verification:
  - Kafka topics: `docker exec dal-kafka kafka-topics --bootstrap-server localhost:9092 --list`
  - Ganache RPC: POST `eth_blockNumber` to `http://localhost:8545`
  - DB schema: check `audit.events` in Postgres/pgAdmin.
- Blockchain dev (from `blockchain/`): `npm install`, `npm run compile`, `npm test`.
- Ganache deploy: copy `blockchain/.env.example` to `.env`, set `GANACHE_PRIVATE_KEY`, then `npm run deploy:ganache`.
- **Backend build** (from `backend/`):
  ```pwsh
  # Install shared modules once (required on a clean checkout)
  mvn clean install -pl common/event-model,common/shared-contracts -DskipTests
  # Run all tests (integration tests need Docker)
  mvn clean verify
  # Run a single service
  mvn spring-boot:run -pl command-service -am   # ports: 8081/8082/8083/8084
  ```
- **Frontend dev** (from `frontend/audit-ui/`): `npm install`, then `ng serve` (proxies to query-service on 8084).
- **Backend test quick-check** (requires running infra):
  ```pwsh
  curl -X POST http://localhost:8081/commands/user/login -H "Content-Type: application/json" -d '{"userId":"user1"}'
  curl "http://localhost:8084/api/audit-logs"
  curl "http://localhost:8084/api/audit-logs/1/integrity-check"
  ```

## Project-specific conventions to preserve
- Hardhat config intentionally omits `accounts` for Ganache when `GANACHE_PRIVATE_KEY` is invalid/missing; do not replace with `accounts: []` (`blockchain/hardhat.config.js`).
- Ganache chain assumptions are fixed by deploy stack: chainId `1337` and deterministic mnemonic (`deploy/docker-compose.yml`, `deploy/README.md`).
- Git workflow conventions are documented and reused across docs:
  - branch (mandatory for every issue): `<type>/#XX-description`, where `type` = `feature|fix|docs|test`
  - commit: `[#XX] short message`
  - PR text includes `Closes #XX` (`START_HERE.md`, `CONTRIBUTING.md`).

## Integration points and dependencies
- Event hash bridge: backend `event-store-service`/`audit-writer-service` should map DB `event_hash` to on-chain `AuditLedger.appendAuditRecord(...)`.
- Messaging backbone is Kafka (`dal-kafka`), so design async command->event->projection pipelines around topics rather than direct service calls.
- Local blockchain endpoint defaults to `http://127.0.0.1:8545` and is consumed by Hardhat deploy scripts.
- Postgres schema namespace is `audit`; use this schema explicitly in SQL and ORM mappings.
- **Canonical hash**: both `event-store-service` and `audit-writer-service` must compute `event_hash` using `CanonicalObjectMapperFactory.create()` from `common/shared-contracts`. This ensures byte-identical JSON serialization (sorted fields, ISO-8601 `Instant`, no type headers). Never use a plain `new ObjectMapper()` for hashing.
- **Dead-letter topic**: `user.login.events.dlt` is the Kafka DLT for `audit-writer-service`; do not consume it in unrelated services.
- **Required env vars** for services that interact with the blockchain (`audit-writer-service`, `query-service`): `AUDIT_LEDGER_CONTRACT_ADDRESS` (set after `npm run deploy:ganache`), `GANACHE_PRIVATE_KEY` (audit-writer only), `AUDIT_LEDGER_CONTRACT_DEPLOYMENT_BLOCK` (query-service, defaults to `0`).
- **Testcontainers**: integration tests that require Kafka or PostgreSQL spin up containers automatically; they self-skip when Docker is unavailable. Examples: `EventStoreKafkaToPostgresIntegrationTest`, `AuditEventConsumerKafkaTestcontainersTest`, `IntegrityCheckIntegrationTest`.

## Practical guidance for coding agents
- Do not assume existing Spring/Angular app code; create minimal skeletons first when implementing new backend/frontend issues.
- Align new code with the issue-driven plan in `docs/ROADMAP.md` because this repo is organized around those milestones.
- When changing blockchain write paths, update tests in `blockchain/test/AuditLedger.test.js` in the same change.
- **Schema migrations belong only in `event-store-service`** (`src/main/resources/db/migration`). Never add Flyway config to other services.
- **Any code that computes `event_hash` must use `CanonicalObjectMapperFactory.create()`** from `common/shared-contracts`. Deviating from the canonical mapper silently breaks DB↔on-chain hash consistency; see runbook `docs/EVENT_HASH_CANONICAL_MIGRATION.md` for historical data recovery.
- Always build from the **`backend/`** root with `-am` (also-make) so Maven resolves `common/*` siblings: `mvn spring-boot:run -pl <service> -am`. Running from a service subdirectory directly will fail on a clean checkout.
- Java 25+ and Maven 3.9+ are required for the backend build.


# Backend — Distributed Audit Ledger

Multi-module Maven project implementing the backend services for the Distributed Audit Ledger.

## Module overview

| Module | Port | Description |
|---|---|---|
| `common/event-model` | — | Shared domain event classes (`AuditEvent`, `UserLoggedInEvent`, `EventType`) |
| `common/shared-contracts` | — | Shared DTOs and API contracts (`UserLoginCommand`, `AuditEventDto`, `CommandResponse`) |
| `command-service` | **8081** | Reactive WebFlux API for commands; publishes domain events to Kafka |
| `event-store-service` | **8082** | Reactive Kafka consumer; persists events to PostgreSQL `audit.events` via R2DBC |
| `audit-writer-service` | **8083** | Consumes Kafka events, anchors SHA-256 hashes on Ganache via Web3j |
| `query-service` | **8084** | Reactive WebFlux read API for audit logs and integrity checks via R2DBC |

## Prerequisites

- Java 25+
- Maven 3.9+
- Running infrastructure: `cd ../deploy && docker compose up -d`

## Build

```bash
# From backend/ directory

# Compile & package all modules
mvn clean package -DskipTests

# Run tests (requires running Docker infrastructure for integration tests)
mvn clean verify

# Build only common modules
mvn clean install -pl common/event-model,common/shared-contracts
```

## Run individual services

```bash
# Command Service (port 8081)
cd command-service
mvn spring-boot:run

# Event Store Service (port 8082)
cd event-store-service
mvn spring-boot:run

# Audit Writer Service (port 8083)
cd audit-writer-service
mvn spring-boot:run

# Query Service (port 8084)
cd query-service
mvn spring-boot:run
```

## Environment variables

Each service reads its configuration from `application.yml` with environment variable overrides.
Reactive PostgreSQL services (`event-store-service`, `query-service`) use R2DBC; `event-store-service` also uses JDBC-only Flyway for migrations:

| Variable | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `R2DBC_URL` | `r2dbc:postgresql://localhost:5432/audit_ledger` | Reactive PostgreSQL connection URL |
| `DB_URL` | `jdbc:postgresql://localhost:5432/audit_ledger` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `GANACHE_RPC_URL` | `http://localhost:8545` | Ganache JSON-RPC endpoint |
| `AUDIT_LEDGER_CONTRACT_ADDRESS` | — | Deployed AuditLedger contract address |
| `GANACHE_PRIVATE_KEY` | — | Ethereum private key for signing transactions |

## Architecture

The diagram below shows the **target integration flow** for upcoming backend issues
(`#5` and beyond). In this PR (`#4`) only service skeletons and shared modules are
bootstrapped.

```
Client
  │
  ▼  command API (planned)
command-service (8081)
  │  publishes UserLoggedInEvent
  ▼
Kafka topic: user.login.events
  │                  │
  ▼                  ▼
event-store     audit-writer
  -service        -service
  (8082)          (8083)
  │  persists       │  appendAuditRecord()
  ▼  to Postgres    ▼
audit.events     Ganache
  │              blockchain
  ▼
query-service (8084)
  │  query API (planned)
  ▼
Angular UI
```

## Issue tracker

This module implements **Issue #4** in the GitHub Issues Plan.
See `GITHUB_ISSUES_PLAN.md` for the full roadmap.

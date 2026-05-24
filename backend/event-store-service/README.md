# Event Store Service

`event-store-service` implements Issue #6: it reads events from Kafka (`user.login.events`) and stores them in PostgreSQL (`audit.events`).

## What's inside

- Kafka consumer (`AuditEventConsumer`)
- Mapping `AuditEvent` -> `StoredAuditEvent`
- Calculating the `SHA-256` payload hash (`event_hash`)
- Persistence via Spring Data R2DBC
- Poison record handling: 3 retries and then skip (without DLT publishing); persistence errors are not skipped by the recoverer
- Retry/backoff tuning via properties:
  `spring.kafka.listener.error-handler.backoff-interval-ms` and
  `spring.kafka.listener.error-handler.max-retries`
- Flyway migrations in `src/main/resources/db/migration`

## PostgreSQL extension prerequisites

- Migration `V4__add_payload_trgm_index.sql` uses `pg_trgm` to speed up payload text search.
- `CREATE EXTENSION IF NOT EXISTS pg_trgm;` may require elevated DB privileges depending on your PostgreSQL setup.
- If your Flyway role is restricted in production, pre-enable `pg_trgm` at database provisioning time:
  `CREATE EXTENSION IF NOT EXISTS pg_trgm;`
- `V4__add_payload_trgm_index.sql` is marked with `-- flyway:executeInTransaction=false`
  so Flyway can run `CREATE INDEX CONCURRENTLY` and avoid long blocking locks on `audit.events`.
- Ensure the runtime role has enough permissions to create indexes in schema `audit`.

## `event_hash` compatibility

- For new records, payload serialization uses the canonical `ObjectMapper` shared with `audit-writer-service`.
- Historical records created before the canonical scheme may have a different `event_hash` for the same payload.
- Before enabling strict matching of `audit.events.event_hash` with on-chain hashes, follow this runbook:
  `../../docs/EVENT_HASH_CANONICAL_MIGRATION.md`.

## Quick start

From the `backend/` directory:

```bash
mvn spring-boot:run -pl event-store-service -am
```

## Tests

From the `backend/` directory:

```bash
mvn -pl event-store-service test
```

The Kafka -> PostgreSQL integration flow is covered by
`EventStoreKafkaToPostgresIntegrationTest` using Testcontainers.
If Docker is unavailable, this test is skipped automatically.

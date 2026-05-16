# Query Service (Issue #8 MVP)

Read-side service for audit history queries from `audit.events`.

## What is implemented

- `GET /api/audit-logs` with optional filters:
  - `userId`
  - `eventType` (e.g. `USER_LOGGED_IN`)
  - `from` / `to` (ISO-8601, UTC)
- `GET /api/audit-logs/{id}` for a single event
- Reactive query layer with dynamic SQL (`DatabaseClient`)
- MapStruct mapper from DB model to shared `AuditEventDto`
- WebFlux + service + mapper tests

## Run locally

From `backend/`:

```bash
mvn -pl query-service spring-boot:run
```

Service port: `8084`

## Quick checks

```bash
curl "http://localhost:8084/api/audit-logs"
curl "http://localhost:8084/api/audit-logs?userId=user-1&eventType=USER_LOGGED_IN"
curl "http://localhost:8084/api/audit-logs/1"
```

## Tests

From `backend/`:

```bash
mvn -pl query-service test
```


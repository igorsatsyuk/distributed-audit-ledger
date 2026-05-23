# Query Service (Issue #8 MVP)

Read-side service for audit history queries from `audit.events`.

## What is implemented

- `GET /api/audit-logs` with optional filters:
  - `userId`
  - `eventType` (e.g. `USER_LOGGED_IN`)
  - `from` / `to` (ISO-8601, UTC)
  - `limit` (default `100`, max `500`)
  - `offset` (default `0`)
- `GET /api/audit-logs/{id}` for a single event
- Reactive query layer with dynamic SQL (`DatabaseClient`)
- MapStruct mapper from DB model to shared `AuditEventDto`
- WebFlux + service + mapper tests

## Run locally

From `backend/`:

```bash
mvn spring-boot:run -pl query-service -am
```

`query-service` production config intentionally requires these JWT environment variables:

- `AUTH_JWT_SECRET`
- `AUTH_JWT_ISSUER`
- `AUTH_JWT_EXPIRATION`

For local development only, you can run with the dev-only `local` profile from
`src/main/resources/application-local.yml`:

```bash
mvn spring-boot:run -pl query-service -am -Dspring-boot.run.profiles=local
```

Service port: `8084`

## Quick checks

```bash
curl "http://localhost:8084/api/audit-logs"
curl "http://localhost:8084/api/audit-logs?userId=user-1&eventType=USER_LOGGED_IN&limit=50&offset=0"
curl "http://localhost:8084/api/audit-logs/1"
```

## Tests

From `backend/`:

```bash
mvn test -pl query-service -am
```

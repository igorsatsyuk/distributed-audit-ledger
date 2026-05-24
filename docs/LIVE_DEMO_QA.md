# Live Demo Scenario and Q&A (Issue #20)

This document is an interview-ready script for demonstrating the Distributed Audit Ledger end to end.

## Audience and Goal

- Audience: interviewer, tech lead, architect, or security engineer.
- Goal: prove immutable audit flow from command API to on-chain anchoring and read-side integrity checks.
- Duration: 12-20 minutes (core path: ~10 minutes, Q&A: ~10 minutes).

## Demo Narrative (What to Say)

1. "I send a command to the command service."
2. "The command service emits a domain event to Kafka."
3. "Event store persists immutable JSON payload + canonical SHA-256 hash."
4. "Audit writer anchors the same hash in the smart contract."
5. "Query service exposes audit logs and integrity status (`ON_CHAIN`, `MISMATCH`, `PENDING`)."

## Prerequisites

- Docker stack from `deploy/` is running.
- Backend services are running on ports `8081`-`8084`.
- `AUDIT_LEDGER_CONTRACT_ADDRESS` is configured for blockchain-aware services.
- Tools: `curl`, `jq`.

Quick health check:

```pwsh
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

## Demo Script (Core Path)

### Step 0: Obtain JWT token

```pwsh
$TOKEN = (curl -s -X POST http://localhost:8081/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123!"}' | jq -r '.accessToken')
```

### Step 1: Send one command

```pwsh
$CMD = (curl -s -X POST http://localhost:8081/commands/user/login -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"userId":"demo.user@example.com"}')
$CMD
$EVENT_ID = ($CMD | jq -r '.eventId')
```

Expected: response contains `success=true` and `eventId`.

### Step 2: Read events from query service

```pwsh
curl -s "http://localhost:8084/api/audit-logs?userId=demo.user@example.com&limit=5" -H "Authorization: Bearer $TOKEN"
```

Expected:

- At least one event with `eventType=USER_LOGGED_IN`.
- `eventHash` is present.

### Step 3: Resolve audit ID for integrity check

Use the `eventId` captured in Step 1 to find the exact record:

```pwsh
$AUDIT_ID = (curl -s "http://localhost:8084/api/audit-logs?userId=demo.user@example.com&limit=20" -H "Authorization: Bearer $TOKEN" | jq -r --arg eid "$EVENT_ID" '.[] | select(.eventId == $eid) | .id')
$AUDIT_ID
```

Expected: numeric ID (for example `42`).

### Step 4: Run integrity check

```pwsh
curl -s "http://localhost:8084/api/audit-logs/$AUDIT_ID/integrity-check" -H "Authorization: Bearer $TOKEN"
```

Expected: `status` eventually becomes `ON_CHAIN`.

If `MISMATCH` appears immediately after command, wait a few seconds and retry (asynchronous anchor timing).

### Step 5: Show Kafka flow proof (optional)

```pwsh
docker exec dal-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

Expected topics include `user.login.events`.

### Step 6: Show DB evidence (optional)

```pwsh
psql -h localhost -U postgres -d audit_ledger -c "SELECT id, event_id, event_type, user_id, event_hash, created_at FROM audit.events ORDER BY id DESC LIMIT 5;"
```

Expected: latest event row with non-empty `event_hash`.

## UI Walkthrough (2-4 minutes)

Use screenshot pack and/or running Angular app:

1. Command accepted response: `docs/screenshots/01-command-accepted.png`
2. Audit list response: `docs/screenshots/02-audit-logs-list.png`
3. Integrity `ON_CHAIN`: `docs/screenshots/03-integrity-on-chain.png`
4. Integrity `MISMATCH`: `docs/screenshots/04-integrity-mismatch.png`
5. Kafka topics: `docs/screenshots/05-kafka-topics.png`
6. Postgres events: `docs/screenshots/06-postgres-audit-events.png`
7. Health endpoints: `docs/screenshots/07-health-endpoints.png`
8. Angular dashboard: `docs/screenshots/08-angular-dashboard.png`

## Demo Variants

### Fast path (5-7 minutes)

- Run Step 1, 2, 4 only.
- Explain asynchronous eventual consistency in one sentence.

### Deep technical path (15-20 minutes)

- Run full core path.
- Add Kafka consumer groups and lag checks.
- Discuss canonical hash serialization and why it prevents DB/on-chain divergence.

## Typical Interview Q&A

### 1) Why blockchain here if you already store data in PostgreSQL?

- PostgreSQL stores operational history.
- Blockchain adds external tamper-evidence with immutable anchoring.
- Integrity endpoint cross-checks DB hash against on-chain evidence.

### 2) How do you guarantee hash consistency across services?

- Both `event-store-service` and `audit-writer-service` use `CanonicalObjectMapperFactory.create()`.
- Canonical JSON (stable field order and timestamp formatting) guarantees byte-identical hashing.

### 3) Why Kafka between services?

- Decouples write pipeline stages.
- Supports backpressure/retries and independent scaling.
- Preserves event-driven architecture boundaries.

### 4) What does `PENDING` mean?

`PENDING` means the `event_hash` column for this record is null or blank in the database — the hash has not been written yet (e.g., the event processor hasn't completed). `PENDING` is **not** returned when the blockchain is unreachable; that scenario results in an error response instead.

### 5) What does `MISMATCH` mean?

- Stored DB hash is not observed on-chain.
- Could indicate delayed anchoring, write failure, or tampering.

### 6) How do you handle duplicate blockchain writes?

- Contract rejects duplicate hashes.
- Service uses retries + dead-letter topic for failed processing.

### 7) What are your failure isolation points?

- Command acceptance is isolated from downstream anchoring latency.
- Event store and audit writer consume asynchronously and can recover independently.

### 8) Which service owns schema migrations?

- Only `event-store-service` owns Flyway migrations for `audit.events`.
- Other services must not introduce independent Flyway schemas.

### 9) Is this exactly-once processing?

- End-to-end exactly-once is hard in distributed systems.
- Design is effectively-once at business level via idempotency keys, duplicate hash rejection, and unique constraints.

### 10) How do you scale read traffic?

- `query-service` is read-optimized and stateless.
- Scale horizontally behind load balancer.
- Keep writes and reads separated (CQRS).

### 11) Why WebFlux + Reactor?

- Non-blocking model for I/O heavy services.
- Better resource use under concurrent traffic.
- Fits event-driven architecture.

### 12) How do you secure this in production?

- JWT + role-based access control.
- Secret management for private keys and contract addresses.
- TLS, network policies, audit logging, and limited DB privileges.

## Risk/Trade-off Talking Points

- Eventual consistency: integrity status may lag right after command acceptance.
- Operational complexity: adds Kafka + blockchain infra.
- Cost/performance: anchoring every event can be expensive on public networks; batching is a future optimization.

## Troubleshooting During Demo

### Integrity never reaches ON_CHAIN

- Check `AUDIT_LEDGER_CONTRACT_ADDRESS` for `audit-writer-service` and `query-service`.
- Verify Ganache RPC is reachable at `http://127.0.0.1:8545`.
- Ensure private key env var is set for `audit-writer-service`.

### No events in query service

- Verify Kafka topic exists: `user.login.events`.
- Check event-store consumer logs and PostgreSQL connectivity.

### 401/403 from APIs

- Re-authenticate and include JWT token.
- Confirm role permissions for requested endpoint.

## Related Docs

- `docs/ARCHITECTURE.md`
- `docs/CQRS_FLOW.md`
- `docs/TESTING_SCENARIOS.md`
- `docs/DEPLOYMENT.md`
- `docs/EVENT_HASH_CANONICAL_MIGRATION.md`


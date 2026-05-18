# CQRS Runtime Flow

This document describes the **complete step-by-step runtime sequence** from command ingestion through event persistence, blockchain anchoring, and read-side query.

## End-to-End Sequence Diagram

```
    Client (Browser / API)
         │
         │ 1. HTTP POST /commands/user/login
         ▼
    command-service:8081 ─────────────────────────────────────┐
         │                                                      │
         │ 2. Validate command (UserLoginCommand)               │
         │    ├─ userId: String (required)                     │
         │    ├─ ipAddress: String (optional, may be overridden)
         │    └─ userAgent: String (optional)                  │
         │                                                      │
         │ 3. Create domain event (UserLoggedInEvent)          │
         │    ├─ eventId: UUID (stable)                        │
         │    ├─ aggregateId: userId                           │
         │    ├─ eventType: USER_LOGGED_IN                     │
         │    ├─ userId: String (from command)                 │
         │    ├─ ipAddress: String (server-derived preferred)  │
         │    ├─ userAgent: String                             │
         │    ├─ timestamp: Instant.now()                      │
         │    └─ payload: JSON (serialized event)              │
         │                                                      │
         │ 4. Publish to Kafka                                 │
         │    ├─ Topic: user.login.events                      │
         │    ├─ Partition Key: eventId (ordering)             │
         │    └─ Value: UserLoggedInEvent (serialized)        │
         │                                                      │
         │ 5a. Return HTTP 202 Accepted (to client)            │
         │    Body: { "eventId": "<UUID>", "status": "accepted" }
    ┌────┴────────────────────────────────────────────────────┘────┐
    │                                                               │
    │  Kafka Topic: user.login.events                              │
    │                                                               │
    ├────────────────────────────────────┬─────────────────────────┤
    │ 5b. Consumer Group                 │ 5c. Consumer Group      │
    │     event-store-consumer           │     audit-writer-consumer
    │                                    │                         │
    ▼                                    ▼                         ▼
event-store-service:8082          audit-writer-service:8083
    │                                    │
    │ 6. Consume message                 │ 6. Consume message
    │    ├─ Deserialize event            │    ├─ Deserialize event
    │    └─ Extract payload              │    └─ Extract payload
    │                                    │
    │ 7. Compute event_hash              │ 7. Compute event_hash
    │    ├─ CanonicalObjectMapper        │    ├─ CanonicalObjectMapper
    │    ├─ JSON serialize (sorted keys) │    ├─ JSON serialize (sorted keys)
    │    └─ SHA-256(JSON_bytes)          │    └─ SHA-256(JSON_bytes)
    │                                    │
    │ 8. Store in PostgreSQL             │ 8. Call blockchain
    │    INSERT INTO audit.events        │    ├─ Web3j client
    │    (event_id, aggregate_id,        │    ├─ Ganache RPC
    │     event_type, user_id,           │    ├─ AuditLedger contract
    │     payload, event_hash)           │    ├─ appendAuditRecord(hash,...)
    │                                    │    └─ Sign with private key
    │ 9a. Row inserted in DB             │
    │ ✓ Persist complete                 │ 9b. Transaction mined
    │                                    │    ├─ Receipt captured
    │                                    │    └─ Block number recorded
    │                                    │
    │                                    │ 9c (ERROR PATH)
    │                                    │    ├─ If append failed:
    │                                    │    │  Send to DLT
    │                                    │    │  user.login.events.dlt
    │                                    │    └─ Alert operator
    │                                    │
    └────────────────────┬───────────────┴──────────────────────────┘
                         │
                         │ 10. Query service reads from DB
                         │     (independent of Kafka consumption)
                         ▼
                    query-service:8084
                         │
                         │ 11. Client requests audit log
                         │     GET /api/audit-logs
                         │
                         │ 12. Service queries PostgreSQL
                         │     SELECT * FROM audit.events
                         │     WHERE ... (filters applied)
                         │
                         │ 13. Response
                         ▼
                    Client (UI / API)
                    ├─ Array of AuditEventDto
                    ├─ Includes event_hash (for integrity)
                    └─ Links to integrity-check endpoint
```

## Detailed Step-by-Step Flow

### Phase 1: Command Acceptance

**Step 1-2: HTTP Request**
```
POST /commands/user/login
Content-Type: application/json

{
  "userId": "alice@example.com",
  "ipAddress": "192.0.2.1",      // Optional (may be overridden by server IP)
  "userAgent": "Mozilla/5.0..."  // Optional
}
```

**Step 3: Command Validation & Deserialization**
```
UserLoginCommand validates:
  ✓ userId is not blank
  ✓ ipAddress and userAgent are optional
```

**Step 4: Domain Event Creation**
```java
UserLoggedInEvent event = UserLoggedInEvent.of(
    userId = "alice@example.com",
    ipAddress = "192.0.2.1",  // server-derived; client value preferred
    userAgent = "Mozilla/5.0"
);

// Generated by library:
event.eventId = "550e8400-e29b-41d4-a716-446655440000" (UUID)
event.timestamp = Instant.now()  // ISO-8601 UTC
```

**Step 5a: HTTP Response**
```
HTTP 202 Accepted
Content-Type: application/json

{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "accepted"
}
```

### Phase 2: Kafka Publishing

**Step 5b: Publish to Kafka**
```
Topic:    user.login.events
Key:      550e8400-e29b-41d4-a716-446655440000  (eventId, for ordering)
Value:    {
            "eventId": "550e8400-e29b-41d4-a716-446655440000",
            "aggregateId": "alice@example.com",
            "eventType": "USER_LOGGED_IN",
            "userId": "alice@example.com",
            "ipAddress": "192.0.2.1",
            "userAgent": "Mozilla/5.0",
            "timestamp": "2026-05-18T12:00:00Z"
          }

Consumer Groups (concurrent processing):
1. event-store-consumer → event-store-service:8082
2. audit-writer-consumer → audit-writer-service:8083
```

### Phase 3a: Event Store Processing

**Step 6-7: Event Store Service**

```java
// Kafka consumer invokes:
@KafkaListener(topics = "user.login.events", groupId = "event-store-consumer")
void onUserLoginEvent(UserLoggedInEvent event) {
    // 6. Deserialize (Spring handles via Jackson)
    // 7. Compute canonical hash
    ObjectMapper canonicalMapper = CanonicalObjectMapperFactory.create();
    byte[] eventJson = canonicalMapper.writeValueAsBytes(event);
    String eventHash = DigestUtils.sha256Hex(eventJson);
    // ← Same method used by audit-writer-service
    
    // 8. Persist to DB
    AuditEventEntity entity = new AuditEventEntity();
    entity.setEventId(event.getEventId());
    entity.setAggregateId(event.getAggregateId());
    entity.setEventType(event.getEventType());
    entity.setUserId(event.getUserId());
    entity.setPayload(eventJson);  // JSONB in PostgreSQL
    entity.setEventHash(eventHash);
    entity.setCreatedAt(event.getTimestamp());
    
    // Insert via reactive R2DBC
    repository.save(entity).subscribe();
    // ✓ 9a. Row inserted
}
```

**Step 8: Database INSERT**
```sql
INSERT INTO audit.events
  (event_id, aggregate_id, event_type, user_id, payload, event_hash, created_at)
VALUES
  ('550e8400-e29b-41d4-a716-446655440000',
   'alice@example.com',
   'USER_LOGGED_IN',
   'alice@example.com',
   '{"eventId":"550e8400-e29b-41d4-a716-446655440000",...}'::jsonb,
   'abc123def456...',
   '2026-05-18 12:00:00+00:00');
```

### Phase 3b: Blockchain Anchoring

**Step 6-7: Audit Writer Service**

```java
// Same Kafka topic, different consumer group
@KafkaListener(topics = "user.login.events", groupId = "audit-writer-consumer")
void onUserLoginEvent(UserLoggedInEvent event) {
    try {
        // 6. Deserialize
        // 7. Compute canonical hash (MUST match event-store)
        ObjectMapper canonicalMapper = CanonicalObjectMapperFactory.create();
        byte[] eventJson = canonicalMapper.writeValueAsBytes(event);
        String eventHash = DigestUtils.sha256Hex(eventJson);
        
        // 8. Call blockchain via Web3j
        TransactionReceipt receipt = auditLedgerContract.appendAuditRecord(
            Numeric.hexStringToByteArray(eventHash),  // bytes32
            event.getTimestamp().getEpochSecond(),     // uint256
            event.getEventType(),                      // string
            senderAddress                              // address
        ).send();
        
        // ✓ 9b. Transaction mined
        log.info("Transaction: {}", receipt.getTransactionHash());
    } catch (Exception e) {
        // 9c. On error → send to DLT
        template.send("user.login.events.dlt", event);
        throw new BlockchainAnchoringException(e);
    }
}
```

**Output: Blockchain State**
```
Contract State (AuditLedger.sol):
├── records[0]
│   ├─ eventHash: 0xabc123def456...
│   ├─ timestamp: 1716033600 (Unix epoch)
│   ├─ eventType: "USER_LOGGED_IN"
│   └─ source: 0x123...abc (writer address)
│
└── recordCount: 1
```

### Phase 4: Query / Read Side

**Step 10-11: Query Service Processing**

```java
// HTTP GET request from client
GET /api/audit-logs?userId=alice@example.com&limit=10

// Query service handles via WebFlux
@GetMapping
public Flux<AuditEventDto> getAuditLogs(
    @RequestParam String userId,
    @RequestParam(required = false) EventType eventType,
    @RequestParam(required = false) Instant from,
    @RequestParam(required = false) Instant to,
    @RequestParam(required = false, defaultValue = "100") Integer limit,
    @RequestParam(required = false, defaultValue = "0") Long offset
) {
    // 12. Dynamic query construction
    return repository.findByFilter(new AuditLogFilter(
        userId, eventType, from, to, limit, offset
    ))
    .map(mapper::toDto);  // MapStruct DTO projection
}
```

**Step 12: Dynamic SQL**
```sql
SELECT
    id, event_id, aggregate_id, event_type, user_id,
    payload, event_hash, created_at
FROM audit.events
WHERE user_id = $1
  AND created_at >= $2
  AND created_at <= $3
ORDER BY created_at DESC
LIMIT $4 OFFSET $5;
```

**Step 13: Response**
```json
[
  {
    "id": 1,
    "eventId": "550e8400-e29b-41d4-a716-446655440000",
    "aggregateId": "alice@example.com",
    "eventType": "USER_LOGGED_IN",
    "userId": "alice@example.com",
    "payload": {
      "eventId": "550e8400-e29b-41d4-a716-446655440000",
      "ipAddress": "192.0.2.1",
      "userAgent": "Mozilla/5.0",
      "timestamp": "2026-05-18T12:00:00Z"
    },
    "eventHash": "abc123def456..."  // For integrity checks
  }
]
```

## Integrity Checks (Query Service)

**Optional: Verify blockchain anchoring**

```
GET /api/audit-logs/1/integrity-check

Response:
{
  "auditLogId": 1,
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventHash": "abc123def456...",
  "status": "ON_CHAIN",
  "blockchainRecord": {
    "exists": true,
    "transactionHash": "0x456def789...",
    "blockNumber": 42,
    "timestamp": 1716033600
  }
}

Possible statuses:
- ON_CHAIN:  Hash found in contract ✓ Anchor complete
- MISMATCH:  Hash in DB but NOT on-chain ✗ DB modified after anchor
- PENDING:   No hash computed yet (event_hash IS NULL)
```

## Failure Scenarios

### Scenario A: Kafka Publish Fails

```
command-service(8081)
  ├─ Event created
  ├─ Kafka send() fails (broker unreachable)
  └─ HTTP 500 returned to client
     ✗ Client must retry manually

Recovery:
  - Client retries POST /commands/user/login
  - Kafka topic auto-creates on first attempt (config: auto.create.topics.enable=true)
```

### Scenario B: Event Store Write Fails

```
event-store-service(8082)
  ├─ Message consumed from Kafka ✓
  ├─ Hash computed ✓
  ├─ PostgreSQL write fails (connection lost)
  └─ Exception logged; message remains in Kafka queue
     (consumer offset NOT committed)

Recovery:
  - Consumer restarts
  - Reprocesses same message
  - Retry backoff (Spring + Kafka config)
```

### Scenario C: Blockchain Anchor Fails

```
audit-writer-service(8083)
  ├─ Message consumed ✓
  ├─ Hash computed ✓
  ├─ Web3j call fails (contract error, gas insufficient)
  ├─ ✗ Send to Dead-Letter Topic
  │   Topic: user.login.events.dlt
  └─ ✓ Consumer offset committed
     (Event persistence NOT blocked)

DLT Consumer:
  - Manual inspection required
  - Observe: error reason, event data
  - Operator manually retries or updates blockchain state
```

## Key Guarantees

1. **At-Least-Once Delivery**: Kafka consumers use auto-offset strategy; on restart, replay oldest unprocessed messages
2. **Idempotent Event Store**: Event uniqueness via `UNIQUE(event_id)` constraint—duplicate messages safely ignored
3. **Canonical Hashing**: Both services use `CanonicalObjectMapperFactory` → identical SHA-256 across DB and blockchain
4. **Async Decoupling**: Command response returned immediately; event persistence happens concurrently
5. **Blockchain Immutability**: Once anchored, hash cannot be modified (append-only, owner-gated contract)

## Latency Profile (Local Development)

| Stage | Typical Latency | Notes |
|-------|-----------------|-------|
| Command API validation | < 10 ms | Command-service (sync) |
| Kafka publish | 10–50 ms | Local Kafka, 1 replica |
| Event store write | 50–200 ms | R2DBC + PostgreSQL network + disk |
| Blockchain anchor | 2–5 sec | Ganache mine time (deterministic) |
| Query API response | 10–50 ms | R2DBC dynamic SQL + network |

Total end-to-end (query after anchor): ~3 sec in dev, <100 ms for event-store only

## Related Documentation

- **Architecture:** `docs/ARCHITECTURE.md` — Component boundaries
- **Deployment:** `docs/DEPLOYMENT.md` — Local stack bring-up
- **Testing:** `docs/TESTING_SCENARIOS.md` — Curl commands and smoke tests
- **Infra Setup:** `deploy/README.md` — Docker Compose details
- **Backend Build:** `backend/README.md` — Maven module structure


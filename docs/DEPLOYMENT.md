# Deployment Guide

This guide describes how to deploy and run the Distributed Audit Ledger system. It ranges from local development to production considerations.

> **Current Focus**: Local development using Docker Compose and Maven.
> Production deployment (Kubernetes) is planned for Issue #19.

## Quick Start (5 minutes)

### 1. Prerequisites

- **Docker** ≥ 24.x and **Docker Compose** ≥ 2.x
- **Java** ≥ 25 and **Maven** ≥ 3.9
- **Node.js** ≥ 18 (for blockchain module, optional for initial startup)
- **jq** (used in health/API verification commands)
- **Git**

### 2. Clone Repository

```bash
git clone <repository-url>
cd distributed-audit-ledger
```

### 3. Start Infrastructure (Docker Compose)

```bash
cd deploy
cp .env.example .env
docker compose up -d
```

Verify all services are running:
```bash
docker compose ps
```

Expected output:
```
NAME          STATUS      PORTS
dal-postgres  healthy     5432->5432/tcp
dal-zookeeper running     2181->2181/tcp
dal-kafka     healthy     9092->29092/tcp
dal-ganache   healthy     8545->8545/tcp
dal-pgadmin   running     5050->5050/tcp
dal-kafka-ui  running     8080->8080/tcp
```

### 4. Deploy Smart Contract to Ganache

```bash
cd ../blockchain
npm install
cp .env.example .env
# Edit .env: set GANACHE_PRIVATE_KEY=0x<64-hex-private-key>
npm run deploy:ganache
```

Now capture the deployed contract address (printed to console):
```
AuditLedger deployed to: 0x1234567890abcdef...
```

Set this in your terminal:
```bash
# Linux / macOS
export AUDIT_LEDGER_CONTRACT_ADDRESS="0x1234567890abcdef..."
export GANACHE_PRIVATE_KEY="0x<64-hex-private-key>"

# PowerShell
$env:AUDIT_LEDGER_CONTRACT_ADDRESS = "0x1234567890abcdef..."
$env:GANACHE_PRIVATE_KEY = "0x<64-hex-private-key>"
```

If you run services in separate terminals, export the same variables in each terminal where you start `audit-writer-service` and `query-service`.

Important: `GANACHE_PRIVATE_KEY` for `audit-writer-service` must belong to the contract owner (or ownership must be transferred), because `appendAuditRecord` is `onlyOwner`.

### 5. Start Backend Services

```bash
cd ../backend

# Step 5a: Install shared modules (required on clean checkout)
mvn clean install -pl common/event-model,common/shared-contracts -DskipTests

# Step 5b: In separate terminals, start each service:
# Terminal 1:
mvn spring-boot:run -pl event-store-service -am

# Terminal 2 (requires AUDIT_LEDGER_CONTRACT_ADDRESS + GANACHE_PRIVATE_KEY):
mvn spring-boot:run -pl audit-writer-service -am

# Terminal 3:
mvn spring-boot:run -pl command-service -am

# Terminal 4 (requires AUDIT_LEDGER_CONTRACT_ADDRESS):
mvn spring-boot:run -pl query-service -am
```

Notes:
- `audit-writer-service` requires both `AUDIT_LEDGER_CONTRACT_ADDRESS` and `GANACHE_PRIVATE_KEY`.
- `query-service` requires `AUDIT_LEDGER_CONTRACT_ADDRESS`.
- `command-service` does not require blockchain env vars.

Expected startup messages:
```
command-service:       Started in ~5 sec on port 8081
event-store-service:   Started in ~5 sec on port 8082
audit-writer-service:  Started in ~5 sec on port 8083
query-service:         Started in ~5 sec on port 8084
```

### 6. Verify Services Are Healthy

```bash
# Command Service
curl -s http://localhost:8081/actuator/health | jq .

# Event Store Service
curl -s http://localhost:8082/actuator/health | jq .

# Audit Writer Service
curl -s http://localhost:8083/actuator/health | jq .

# Query Service
curl -s http://localhost:8084/actuator/health | jq .

# All should return: { "status": "UP" }
```

### 7. Run First Event

```bash
# Send a login command
curl -X POST http://localhost:8081/commands/user/login \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "alice@example.com",
    "ipAddress": "192.0.2.1",
    "userAgent": "curl/7.81.0"
  }'

# Note: body `ipAddress` / `userAgent` are fallback values; service prefers remote IP and `User-Agent` header when available.

# Expected response (HTTP 202 Accepted):
# {"success":true,"message":"Command accepted","eventId":"<uuid>"}
```

### 8. Query Audit Logs

```bash
# List all events
curl http://localhost:8084/api/audit-logs | jq .

# Expected: Array of AuditEventDto objects

# Check integrity (blockchain anchoring)
curl http://localhost:8084/api/audit-logs/1/integrity-check | jq .

# Expected response:
# {
#   "auditLogId": 1,
#   "eventId": "<uuid>",
#   "eventHash": "abc123...",
#   "status": "ON_CHAIN",  # or "MISMATCH", "PENDING"
#   "blockchainRecord": { ... }
# }
```

✅ **Congratulations!** Your local stack is ready.

---

## Local Stack (Docker Compose)

**Primary source:** `deploy/README.md`

See the deploy README for detailed documentation on:
- Service ports and container names
- Environment variables
- Database schema
- Troubleshooting Docker issues

### Quick Reference

| Service | Port | URL |
|---------|------|-----|
| PostgreSQL | 5432 | `psql -h localhost -U postgres -d audit_ledger` |
| Kafka | 9092 | `localhost:9092` |
| Zookeeper | 2181 | `localhost:2181` |
| Ganache (RPC) | 8545 | `http://localhost:8545` |
| pgAdmin | 5050 | http://localhost:5050 (`admin@example.com` / `admin`) |
| Kafka UI | 8080 | http://localhost:8080 |

### Start/Stop Commands

```bash
cd deploy

# Start all services
docker compose up -d

# View logs
docker compose logs -f kafka

# Stop services (keep volumes)
docker compose down

# Stop and remove volumes (clean reset)
docker compose down -v

# Check service health
docker compose ps
```

---

## Backend Services

**Primary source:** `backend/README.md`

### Build

```bash
cd backend

# Compile all modules
mvn clean package -DskipTests

# Run tests (requires Docker)
mvn clean verify

# Build only shared modules
mvn clean install -pl common/event-model,common/shared-contracts -DskipTests
```

### Run Individual Services

```bash
# From backend/ directory (critical: do not run from service subdirectory)

# Always install shared modules first on clean checkout:
mvn clean install -pl common/event-model,common/shared-contracts -DskipTests

# Then run any service with -am (also-make) flag:
mvn spring-boot:run -pl command-service -am      # Port 8081
mvn spring-boot:run -pl event-store-service -am  # Port 8082
mvn spring-boot:run -pl audit-writer-service -am # Port 8083
mvn spring-boot:run -pl query-service -am        # Port 8084
```

### Environment Variables

| Variable | Default | Service | Notes |
|----------|---------|---------|-------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | All | Kafka broker address |
| `R2DBC_URL` | `r2dbc:postgresql://localhost:5432/audit_ledger` | event-store, query | Reactive DB URL |
| `DB_URL` | `jdbc:postgresql://localhost:5432/audit_ledger` | event-store | JDBC URL for Flyway migrations |
| `DB_USERNAME` | `postgres` | Services using DB | — |
| `DB_PASSWORD` | `postgres` | Services using DB | — |
| `GANACHE_RPC_URL` | `http://localhost:8545` | audit-writer, query | Blockchain RPC endpoint |
| `AUDIT_LEDGER_CONTRACT_ADDRESS` | — (required) | audit-writer, query | Deployed contract address |
| `GANACHE_PRIVATE_KEY` | — (required) | audit-writer | Ethereum private key |
| `AUDIT_LEDGER_CONTRACT_DEPLOYMENT_BLOCK` | `0` | query | Contract deployment block for on-chain integrity checks on non-local RPC endpoints |

Set via environment or edit `application.yml` per service.

---

## Blockchain Module

**Primary source:** `blockchain/README.md`

### Compile & Test

```bash
cd blockchain
npm install
npm run compile  # Compile Solidity contracts
npm test         # Run Hardhat tests
```

### Deploy to Ganache

```bash
# Create .env with private key
cp .env.example .env
# Edit .env: GANACHE_PRIVATE_KEY=0x123abc...

# Deploy AuditLedger contract
npm run deploy:ganache

# Output includes:
# AuditLedger deployed to: 0xabcdef123456...
```

Ganache details:
- **Chain ID**: 1337 (fixed by docker-compose)
- **RPC URL**: `http://localhost:8545`
- **Mnemonic**: `test test test test test test test test test test test junk` (deterministic)
- **Accounts**: 10 pre-funded (100 ETH each)

---

## Frontend (Angular)

**Optional quick start** (not required for backend testing)

```bash
cd frontend/audit-ui
npm install
npm start
# Runs on http://localhost:4200
# Proxies API calls to query-service:8084
```

---

## Infrastructure Verification Checklist

After startup, verify each component:

### PostgreSQL

```bash
# Via psql (if installed)
psql -h localhost -U postgres -d audit_ledger -c "SELECT tablename FROM pg_tables WHERE schemaname='audit';"

# Expected output:
# tablename
# -----------
# events

# Or use pgAdmin at http://localhost:5050
```

### Kafka

```bash
# List topics
docker exec dal-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Expected output includes:
# user.login.events
# user.login.events.dlt (appears only after first DLT publish)

# Inspect topic
docker exec dal-kafka kafka-topics --bootstrap-server localhost:9092 \
  --describe --topic user.login.events
```

### Ganache (Blockchain)

```bash
# Check RPC connectivity
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}'

# Expected response: {"jsonrpc":"2.0","result":"0x...","id":1}  (hex block number, may be 0x0/0x1+)

# List accounts
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_accounts","params":[],"id":1}'
```

### Backend Services (Actuator endpoints)

```bash
for port in 8081 8082 8083 8084; do
  echo "Port $port:"
  curl -s http://localhost:$port/actuator/health | jq .status
done
```

---

## Recommended Bring-Up Order

1. **Infrastructure (Docker)**
   - `docker compose up -d` from `deploy/`
   - Wait 30 seconds for all services to be healthy

2. **Blockchain Deployment**
   - `npm run deploy:ganache` from `blockchain/`
   - Capture contract address
   - Set env var `AUDIT_LEDGER_CONTRACT_ADDRESS`
   - Set env var `GANACHE_PRIVATE_KEY` for `audit-writer-service`

3. **Backend Services**
   - Start **event-store-service** (owns database schema via Flyway)
   - Start **audit-writer-service** (depends on contract address)
   - Start **command-service**
   - Start **query-service**

4. **Smoke Test**
   - `curl -X POST http://localhost:8081/commands/user/login ...`
   - `curl http://localhost:8084/api/audit-logs`

5. **Frontend (Optional)**
   - `npm start` from `frontend/audit-ui/`
   - Navigate to http://localhost:4200

---

## Troubleshooting

### Services fail to start: "Cannot connect to Kafka"

- Docker compose stack not running: `docker compose ps` from `deploy/`
- Kafka needs 10–30 seconds to be healthy after container start
- Try: `docker compose logs kafka` for error messages

### Audit Writer anchoring fails: "GANACHE_PRIVATE_KEY not set"

- Service can still start, but blockchain anchoring fails at runtime when events are processed
- Set via environment: `export GANACHE_PRIVATE_KEY=0x...` (Linux/macOS) or `$env:...` (PowerShell)
- Or use a local, ignored override file / IDE run configuration; do **not** commit the private key into tracked `application.yml`

### Events not appearing in database after curl command

- Check event-store-service logs for Kafka consumer errors
- Verify Kafka topic exists: `docker exec dal-kafka kafka-topics --bootstrap-server localhost:9092 --list`
- Confirm database connection in logs

### "Port already in use" errors

- Kill process on port: `lsof -i :8081` (find process), `kill -9 <PID>` (terminate)
- Backend service ports are configured in each service `application.yml` (`server.port`) or can be overridden with `--server.port=<port>`

### Blockchain anchoring fails with "DuplicateHash" error

- Attempted to anchor the same hash twice
- This is by design (contract prevents duplicate hashes)
- Different event = different hash

---

## Common Development Tasks

### Add a new event type

1. Define domain event model in `common/event-model`
2. Define/extend API DTOs in `common/shared-contracts` (if command/query contract changes)
3. Add command handler in `command-service`
4. Add Kafka consumer in `event-store-service` and `audit-writer-service`
5. Add query filters in `query-service`
6. Rebuild and restart services

### View database contents

```bash
# psql shell
psql -h localhost -U postgres -d audit_ledger

# List events
SELECT id, event_id, event_type, user_id, event_hash FROM audit.events;

# View event payload
SELECT payload FROM audit.events WHERE id = 1 \gx
```

### Inspect Kafka messages

```bash
# Listen to topic
docker exec dal-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic user.login.events \
  --from-beginning \
  --property value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
```

### Reset everything (clean slate)

```bash
# Stop and remove Docker volumes
cd deploy
docker compose down -v

# Clean Maven builds
cd ../backend
mvn clean

# Restart
cd ../deploy
docker compose up -d

# Ganache chain state is reset after down -v, so redeploy contract and refresh env vars.
cd ../blockchain
npm run deploy:ganache

# Export refreshed values in each relevant terminal:
# AUDIT_LEDGER_CONTRACT_ADDRESS=<new address from deploy output>
# GANACHE_PRIVATE_KEY=<configured key>

cd ../backend
mvn spring-boot:run -pl event-store-service -am
# ... repeat for other services (including audit-writer/query with refreshed env vars)
```

---

## Next Steps

- See `docs/TESTING_SCENARIOS.md` for end-to-end test scripts
- See `docs/CQRS_FLOW.md` for architecture flow details
- See `backend/README.md` for Maven structure
- See Issue #13 in `GITHUB_ISSUES_PLAN.md` for CI/CD pipeline setup


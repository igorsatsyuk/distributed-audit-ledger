# Distributed Audit Ledger

A distributed, event‑sourced audit platform built on CQRS, Event Sourcing, Reactive microservices, and Blockchain anchoring. The system provides an immutable audit trail with on‑chain integrity verification.

---

## 🚀 Key Features

- CQRS + Event Sourcing architecture
- Immutable audit log stored in PostgreSQL
- Blockchain anchoring via Solidity smart contract
- Reactive stack: Spring WebFlux + R2DBC
- Kafka‑based event backbone
- Angular UI for audit exploration
- Integrity check API (DB hash vs blockchain hash)
- Full CI/CD pipeline (backend + frontend + blockchain + SonarQube)

---

## 🏛 High‑Level Architecture

```mermaid
graph LR
    Client["🌐 Client (Angular UI)"]
    
    Client -->|"POST /commands"| CmdService["📤 Command Service (8081)"]
    
    CmdService -->|"Kafka: user.login.events"| Kafka["🔄 Kafka"]
    
    Kafka -->|Consumer: event-store-consumer| EventStore["📊 Event Store Service (8082)<br/>- Canonical hash<br/>- PostgreSQL audit.events<br/>- Flyway migrations"]
    Kafka -->|Consumer: audit-writer-consumer| AuditWriter["⛓️ Audit Writer Service (8083)<br/>- Canonical hash<br/>- Web3j AuditLedger<br/>- DLT: user.login.events.dlt"]
    
    EventStore -->|Persisted events| Postgres["🐘 PostgreSQL<br/>audit.events"]
    AuditWriter -->|appendAuditRecord()| Blockchain
    
    QueryService["📖 Query Service (8084)<br/>- GET /api/audit-logs<br/>- GET /api/audit-logs/{id}<br/>- GET /api/audit-logs/{id}/integrity-check"]
    
    AuditWriter -.->|"Read for integrity"| Blockchain
    Postgres -->|Read Models| QueryService
    Blockchain -->|Hash Verification| QueryService
    
    Client -->|"GET /api/audit-logs"| QueryService
    
    style Client fill:#e1f5ff
    style CmdService fill:#fff3e0
    style EventStore fill:#f3e5f5
    style AuditWriter fill:#fce4ec
    style QueryService fill:#e8f5e9
    style Blockchain fill:#ffe0b2
    style Postgres fill:#e3f2fd
    style Kafka fill:#f0f4c3
```

Full architecture details are available in [**docs/ARCHITECTURE.md**](docs/ARCHITECTURE.md) and [**docs/CQRS_FLOW.md**](docs/CQRS_FLOW.md).

---

## ⚙️ Quickstart

### 1) Install prerequisites
- Docker Desktop
- Java 25+
- Node.js 20+
- Maven 3.9+

### 2) Start infrastructure

```bash
cd deploy
cp .env.example .env
docker compose up -d
```

Verify infrastructure is ready:
- Postgres: `localhost:5432` (accessible via pgAdmin at `localhost:5050`)
- Kafka: `localhost:9092`
- Ganache: `localhost:8545`

### 3) Deploy Smart Contract

```bash
cd blockchain
npm install
cp .env.example .env
# Set GANACHE_PRIVATE_KEY in .env
npm run deploy:ganache
```

### 4) Build and run backend services

```bash
cd backend
# Build and run tests
mvn clean verify
```

Optional troubleshooting on clean environments (if local Maven cache is missing shared modules):

```bash
mvn clean install -pl common/event-model,common/shared-contracts -DskipTests
```

Start services in separate terminals (from `backend/`):

```bash
mvn spring-boot:run -pl command-service -am
mvn spring-boot:run -pl event-store-service -am
mvn spring-boot:run -pl audit-writer-service -am
mvn spring-boot:run -pl query-service -am
```

Service ports:
- Command Service: `8081`
- Event Store Service: `8082`
- Audit Writer Service: `8083`
- Query Service: `8084`

### 5) Start frontend

```bash
cd frontend/audit-ui
npm install
npm start
```

Open [http://localhost:4200](http://localhost:4200) — proxies to query-service on `8084`

**Detailed deployment instructions:** [**docs/DEPLOYMENT.md**](docs/DEPLOYMENT.md)

---

## 🔄 CQRS Runtime Flow (short)

1. Client sends a command to Command Service (`POST /commands/user/login`)
2. Command Service publishes an event to Kafka topic
3. **Event Store Service** (consumer) persists the event to PostgreSQL with SHA-256 hash
4. **Audit Writer Service** (consumer) anchors the event hash on-chain via `AuditLedger.appendAuditRecord()`
5. **Query Service** exposes read models with integrity check status
   - `ON_CHAIN` — event hash verified on blockchain
   - `MISMATCH` — event exists in DB but not on-chain (indicates a failure)
   - `PENDING` — event not yet processed by Audit Writer

**See the detailed flow:** [**docs/CQRS_FLOW.md**](docs/CQRS_FLOW.md)

---

## 📂 Project Structure

| Folder | Purpose |
|--------|---------|
| `backend/` | Reactive microservices (CQRS + Event Sourcing, Spring WebFlux + R2DBC) |
| `blockchain/` | Solidity smart contract (Hardhat) + tests |
| `frontend/audit-ui/` | Angular 17+ UI dashboard |
| `deploy/` | Docker Compose, local infrastructure configs |
| `docs/` | Architecture, CQRS flow, deployment, testing, roadmap |

See [**START_HERE.md**](START_HERE.md) for guided navigation.

## 🗺 Roadmap

### ✅ Phase 1 (MVP) — Completed (Issues #1–#13)

- [x] **#1–#2** — Repository setup + Docker Compose infrastructure
- [x] **#3** — Solidity smart contract `AuditLedger` with Hardhat
- [x] **#4–#8** — Backend services: Command, Event Store, Audit Writer, Query
- [x] **#9** — Integrity check endpoint (DB vs blockchain hash verification)
- [x] **#10–#11** — Angular UI with API integration
- [x] **#12** — Architecture documentation
- [x] **#13** — CI/CD pipeline (GitHub Actions + SonarQube + Telegram notifications)

### 🚀 Phase 2 — Active/Planned (Issues #14–#20)

- [ ] **#14** — Support additional event types (`UserProfileChanged`, `EntityCreated`, `EntityUpdated`, `DataDeleted`)
- [ ] **#15** — Authentication & Authorization (JWT + Spring Security + RBAC)
- [ ] **#16** — Advanced filtering, search, date range picker, CSV export
- [ ] **#17** — Event timeline visualization
- [ ] **#18** — Reconciliation Service (batch integrity checking + Quartz scheduler)
- [ ] **#19** — Kubernetes manifests + Helm chart
- [ ] **#20** — Live demo scenario + Q&A documentation

**Full roadmap and issue tracking:** [**docs/ROADMAP.md**](docs/ROADMAP.md)

## 🤝 Contributing

Follow the contribution guidelines:

1. **Branch naming:** `<type>/#XX-description` where `type` = `feature|fix|docs|test`
2. **Commits:** `[#XX] Short description`
3. **PR description:** Must include `Closes #XX` (links to the issue)
4. **One feature per issue** — see [**docs/ROADMAP.md**](docs/ROADMAP.md) for issue list
5. **Testing:** Run `mvn clean verify` before submitting

Detailed guidelines: [**CONTRIBUTING.md**](CONTRIBUTING.md)

---

## 📚 Documentation

All documentation is located in the `docs/` directory:

| Document | Purpose |
|----------|---------|
| [**START_HERE.md**](START_HERE.md) | 👈 **Start here** — guided navigation for developers |
| [**docs/ARCHITECTURE.md**](docs/ARCHITECTURE.md) | System architecture, service boundaries, data flow |
| [**docs/CQRS_FLOW.md**](docs/CQRS_FLOW.md) | Detailed CQRS + Event Sourcing runtime flow |
| [**docs/DEPLOYMENT.md**](docs/DEPLOYMENT.md) | Quickstart + environment setup |
| [**docs/ROADMAP.md**](docs/ROADMAP.md) | Complete GitHub Issues roadmap (Phase 1–4) |
| [**docs/TESTING_SCENARIOS.md**](docs/TESTING_SCENARIOS.md) | Live demo scenarios + curl commands + screenshots |
| [**docs/EVENT_HASH_CANONICAL_MIGRATION.md**](docs/EVENT_HASH_CANONICAL_MIGRATION.md) | Event hash canonicalization + recovery procedures |
| [**CONTRIBUTING.md**](CONTRIBUTING.md) | Contribution workflow, PR guidelines |

## 📄 License

MIT License
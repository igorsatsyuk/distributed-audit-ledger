# Deployment Guide

This file is a deployment entrypoint map. Detailed runbooks are maintained
close to the corresponding modules to avoid duplicated instructions.

## Local Stack (Docker Compose)

Primary source: `deploy/README.md`

Includes:

- Services and ports (PostgreSQL, ZooKeeper, Kafka, Ganache, pgAdmin)
- `.env` setup
- Startup/shutdown commands
- Verification checks (Kafka topics, Ganache RPC, DB schema)
- Troubleshooting

## Backend Services

Primary source: `backend/README.md`

Includes:

- Multi-module build/test commands
- Per-service run commands
- Environment variable matrix

## Blockchain Module

Primary source: `blockchain/README.md`

Includes:

- Hardhat compile/test workflow
- Ganache deployment steps

## Recommended Local Bring-up Order

1. Start infra from `deploy/`.
2. Validate Kafka / Postgres / Ganache health.
3. Deploy smart contract to Ganache and capture `AUDIT_LEDGER_CONTRACT_ADDRESS`.
4. Start backend services in separate terminals.
5. Start/restart `audit-writer-service` only after contract address is configured.
6. Run smoke checks on service health endpoints.


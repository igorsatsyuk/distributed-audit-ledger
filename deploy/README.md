# Deploy Folder

This folder contains local infrastructure assets.

## Files

- `docker-compose.yml` - local stack definition
- `.env.example` - environment template
- `init-db.sql` - initial PostgreSQL schema

## Local Run

1. Copy `.env.example` to `.env`
2. Start services with Docker Compose

## Verification

- PostgreSQL: `localhost:5432`
- Kafka: `localhost:9092`
- Ganache RPC: `http://localhost:8545`
- pgAdmin: `http://localhost:5050`


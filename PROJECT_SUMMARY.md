# Project Summary

## Goal

Build a distributed audit ledger where business events are persisted as immutable event streams and cryptographic hashes are anchored to blockchain for integrity verification.

## Scope

- Backend microservices using CQRS and event sourcing
- Smart contract for audit hash anchoring
- Frontend UI for timeline and integrity checks
- Local infrastructure with Docker Compose

## Phase Plan

- Phase 1 (MVP): Issues #1-#13
- Phase 2 (Extensions): Issues #14-#20

## Core Components

- `backend/command-service`
- `backend/event-store-service`
- `backend/audit-writer-service`
- `backend/query-service`
- `blockchain/contracts`
- `frontend/audit-ui`

## Current State

- Documentation baseline exists
- Project structure is initialized
- Default branch is `main`
- Next delivery target: Issue #2 (`docker-compose` infrastructure)


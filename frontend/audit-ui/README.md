# Audit UI (Issue #10)

Angular 17 standalone UI skeleton for Distributed Audit Ledger.

## Implemented in this step

- Angular 17 app setup with routing and Material Design.
- Audit table with columns: ID, event type, user, time, integrity status.
- Filters by event type and user ID.
- Event detail panel via side drawer.
- HTTP service for Query Service endpoint (`http://localhost:8084/api/audit-logs`) with local fallback mock data.

## Run locally

```bash
npm install
npm start
```

Open `http://localhost:4200`.

## Tests

```bash
npm run test:headless
```

## Next issue alignment

This skeleton is prepared for Issue #11 (switch from fallback mock usage to full backend API integration and pagination).

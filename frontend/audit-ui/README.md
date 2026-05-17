# Audit UI (Issue #11)

Angular 17 standalone UI for Distributed Audit Ledger with live backend integration.

## Implemented in this step

- Live Query Service integration for list loading (`GET /api/audit-logs`) with filters.
- Integrity checks (`GET /api/audit-logs/{id}/integrity-check`) for drawer details.
- Paginator-based lazy loading (`limit` + `offset`, with `pageSize + 1` has-more strategy).
- Loading and error states with retry action.
- Live integrity status refresh for visible rows with hashes, plus on-demand drawer verification.
- Unit tests for service + dashboard component.

## Local API routing (no CORS issues in dev)

`ng serve` uses `proxy.conf.json` and forwards `/api/*` to `http://localhost:8084`.

## Production API routing

- `environment.prod.ts` uses a relative API path by default so production builds can be served behind the same origin as the API.
- If your deployment exposes Query Service on a different origin, set `queryServiceBaseUrl` at build/deploy time or wire a reverse proxy to `/api`.

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

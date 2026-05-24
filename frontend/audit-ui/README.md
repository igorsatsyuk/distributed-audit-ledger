# Audit UI

Angular 17 standalone UI for Distributed Audit Ledger with live backend integration.

## Current scope

- Table view for audit logs with filters, URL/local storage state, and CSV export.
- Timeline view grouped by day/hour for the current result set.
- Drawer-based integrity inspection for selected events.
- Unit tests for the dashboard, timeline grouping logic, and component interactions.
- Minimum coverage requirement for new frontend work: **80%+**.

## Implemented in this step

- Live Query Service integration for list loading (`GET /api/audit-logs`) with filters.
- Integrity checks (`GET /api/audit-logs/{id}/integrity-check`) for drawer details.
- Paginator-based lazy loading (`limit` + `offset`, with `pageSize + 1` has-more strategy).
- Loading and error states with retry action.
- Drawer-driven integrity verification for selected rows, with table status reflecting the latest known value.
- Unit tests for service + dashboard component.

## Local API routing (no CORS issues in dev)

`ng serve` uses `proxy.conf.json` and forwards `/api/*` to `http://localhost:8084`.

## Production API routing

- `environment.prod.ts` uses a relative API path by default so production builds can be served behind the same origin as the API.
- `queryServiceBaseUrl` from Angular environments is a build-time setting (baked into the bundle during `ng build`).
- For deploy-time API origin changes, use a reverse proxy for `/api` or add a runtime configuration mechanism.

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

For coverage reports:

```bash
npm run test:coverage
```


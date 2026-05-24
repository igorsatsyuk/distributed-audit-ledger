# Screenshot Checklist for Issue #12.4

This folder stores screenshots referenced by `docs/TESTING_SCENARIOS.md`.

## Naming Convention

Use lowercase kebab-case names and keep files in PNG format:

- `01-command-accepted.png`
- `02-audit-logs-list.png`
- `03-integrity-on-chain.png`
- `04-integrity-mismatch.png`
- `05-kafka-topics.png`
- `06-postgres-audit-events.png`
- `07-health-endpoints.png`
- `08-angular-dashboard.png`

## Capture Recommendations

- Resolution: 1366x768 or higher
- Hide secrets/private keys before capture
- Keep terminal width wide enough so commands/results are readable
- Prefer one screenshot per scenario outcome

## Runtime Regeneration

Prerequisites:

```pwsh
pip install Pillow
```

Requires **Python 3.9+**, a running local stack (`deploy/docker-compose.yml`) and all four backend services on ports `8081`–`8084`. Credentials are read from env vars `DEMO_USERNAME` (default: `admin`) and `DEMO_PASSWORD` (default: `admin123!`).

Use the generator to refresh screenshots from live local services:

```pwsh
python docs/screenshots/generate_runtime_screenshots.py
```

The script stores raw capture data in `docs/screenshots/runtime/capture.json` (gitignored).

## Current Status

The screenshot pack files are present in this folder:

- `01-command-accepted.png`
- `02-audit-logs-list.png`
- `03-integrity-on-chain.png`
- `04-integrity-mismatch.png`
- `05-kafka-topics.png`
- `06-postgres-audit-events.png`
- `07-health-endpoints.png`
- `08-angular-dashboard.png`

If needed for final demo polish, replace generated images with runtime-captured screenshots while keeping the same filenames.

Note for `08-angular-dashboard.png`:
- If Angular app on `http://localhost:4200` is running, the screenshot will include a live frontend probe result.
- If frontend is not running, the file captures the probe error so the demo pack still remains complete and reproducible.


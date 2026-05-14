# Blockchain Module

Hardhat workspace for issue #3 (`AuditLedger` smart contract).

## Implemented

- `contracts/AuditLedger.sol` - audit record storage contract
- `test/AuditLedger.test.js` - unit tests (Hardhat + Chai)
- `scripts/deploy.js` - deployment script
- `contracts/AuditLedger.md` - contract documentation

## Quick start

Compile and run tests — no `.env` needed for local in-memory tests:

```bash
npm install
npm run compile
npm test
```

## Deploy to Ganache

Create `blockchain/.env` from template, **replace `GANACHE_PRIVATE_KEY`** with a real funded account key (e.g. account #0 from Ganache's deterministic mnemonic), then deploy:

```bash
cp .env.example .env
# Edit .env: set GANACHE_PRIVATE_KEY=0x<64 hex chars>
npm run deploy:ganache
```

> ⚠️ The placeholder value in `.env.example` will be rejected — deploy requires a valid `0x`-prefixed 32-byte private key.

Optionally override RPC URL:

```bash
$env:GANACHE_RPC_URL="http://127.0.0.1:8545"
npm run deploy:ganache
```


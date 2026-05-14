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

> ⚠️ If `GANACHE_PRIVATE_KEY` is missing or does not match `0x` + 64 hex chars, the `accounts` field is omitted and Hardhat will use the node's unlocked RPC accounts. On a fresh Ganache container this works out of the box; on a remote/authenticated node an explicit key is required.

Optionally override the RPC URL at deploy time:

**bash / zsh**
```bash
export GANACHE_RPC_URL="http://127.0.0.1:8545"
npm run deploy:ganache
```

**PowerShell**
```powershell
$env:GANACHE_RPC_URL = "http://127.0.0.1:8545"
npm run deploy:ganache
```

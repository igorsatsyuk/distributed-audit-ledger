# Blockchain Module

Hardhat workspace for issue #3 (`AuditLedger` smart contract).

## Implemented

- `contracts/AuditLedger.sol` - audit record storage contract
- `test/AuditLedger.test.js` - unit tests (Hardhat + Chai)
- `scripts/deploy.js` - deployment script
- `contracts/AuditLedger.md` - contract documentation

## Quick start

```bash
npm install
cp .env.example .env
npm run compile
npm test
```

## Deploy to Ganache

Create `blockchain/.env` from template and set one funded Ganache account private key:

```bash
cp .env.example .env
```

Then deploy:

```bash
npm run deploy:ganache
```

Optionally override RPC URL:

```bash
$env:GANACHE_RPC_URL="http://127.0.0.1:8545"
npm run deploy:ganache
```


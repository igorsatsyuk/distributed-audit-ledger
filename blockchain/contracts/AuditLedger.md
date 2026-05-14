# AuditLedger Contract

`AuditLedger` stores immutable audit records on-chain and provides fast hash existence checks.

## Storage

- `records`: array of `AuditRecord` structs.
- `hashExists`: mapping for O(1) hash existence checks.
- `owner`: address allowed to append records.

## Struct

```solidity
struct AuditRecord {
    bytes32 eventHash;
    uint256 timestamp;
    string eventType;
    address source;
}
```

## Public API

### `appendAuditRecord(bytes32 _hash, uint256 _timestamp, string memory _eventType, address _source)`

Appends a new record to the ledger.

> **Design note:** The original Issue #3 spec marks this function as `public`, but it is intentionally restricted to `onlyOwner` to prevent unauthorized writes. The deploying address becomes the initial owner; use `transferOwnership` to grant write access to the Audit Writer service without redeploying.

Constraints:
- callable only by `owner`
- `eventType` must not be empty
- `source` must not be `address(0)`
- `_hash` must be unique

Event:
- `RecordAppended(bytes32 indexed eventHash, uint256 timestamp, string eventType, address indexed source)`

### `transferOwnership(address _newOwner)`

Transfers write access to a new owner address. Useful for rotating the Audit Writer service address.

Constraints:
- callable only by current `owner`
- `_newOwner` must not be `address(0)`

Event:
- `OwnershipTransferred(address indexed previousOwner, address indexed newOwner)`

### `getRecord(uint256 _index) -> AuditRecord`

Returns a record by index, otherwise reverts with `IndexOutOfBounds`.

### `getRecordsCount() -> uint256`

Returns total record count.

### `isHashExists(bytes32 _hash) -> bool`

Checks hash existence using mapping lookup.

## Errors

- `Unauthorized()`
- `EmptyEventType()`
- `ZeroSourceAddress()`
- `ZeroOwnerAddress()`
- `DuplicateHash(bytes32)`
- `IndexOutOfBounds(uint256)`

## Local Development

```bash
npm install
npm run compile
npm test
```

## Deploy

Ganache must be available at `http://127.0.0.1:8545`.

```bash
npm run deploy:ganache
```


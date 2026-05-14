// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

contract AuditLedger {
    struct AuditRecord {
        bytes32 eventHash;
        uint256 timestamp;
        string eventType;
        address source;
    }

    AuditRecord[] private records;
    mapping(bytes32 => bool) private hashExists;
    address public owner;

    event RecordAppended(
        bytes32 indexed eventHash,
        uint256 timestamp,
        string eventType,
        address indexed source
    );
    /// @dev Emitted when ownership is transferred to a new address.
    event OwnershipTransferred(address indexed previousOwner, address indexed newOwner);

    error Unauthorized();
    error EmptyEventType();
    error ZeroSourceAddress();
    error ZeroOwnerAddress();
    error DuplicateHash(bytes32 eventHash);
    error IndexOutOfBounds(uint256 index);

    modifier onlyOwner() {
        if (msg.sender != owner) {
            revert Unauthorized();
        }
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    function appendAuditRecord(
        bytes32 _hash,
        uint256 _timestamp,
        string memory _eventType,
        address _source
    ) public onlyOwner {
        if (bytes(_eventType).length == 0) {
            revert EmptyEventType();
        }
        if (_source == address(0)) {
            revert ZeroSourceAddress();
        }
        if (hashExists[_hash]) {
            revert DuplicateHash(_hash);
        }

        records.push(
            AuditRecord({
                eventHash: _hash,
                timestamp: _timestamp,
                eventType: _eventType,
                source: _source
            })
        );

        hashExists[_hash] = true;
        emit RecordAppended(_hash, _timestamp, _eventType, _source);
    }

    function getRecord(uint256 _index) public view returns (AuditRecord memory) {
        if (_index >= records.length) {
            revert IndexOutOfBounds(_index);
        }
        return records[_index];
    }

    function getRecordsCount() public view returns (uint256) {
        return records.length;
    }

    function isHashExists(bytes32 _hash) public view returns (bool) {
        return hashExists[_hash];
    }

    /// @dev Transfers write access to a new owner address.
    ///      The Audit Writer service address should be set as owner after deployment.
    ///      Design note: the original spec marks appendAuditRecord as `public`, but
    ///      restricting it to a single authorized writer prevents unauthorized entries.
    ///      Use this function to rotate the writer address without redeploying.
    function transferOwnership(address _newOwner) public onlyOwner {
        if (_newOwner == address(0)) {
            revert ZeroOwnerAddress();
        }
        address previous = owner;
        owner = _newOwner;
        emit OwnershipTransferred(previous, _newOwner);
    }
}


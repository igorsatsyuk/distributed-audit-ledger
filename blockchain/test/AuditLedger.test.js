const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("AuditLedger", function () {
  async function deployFixture() {
    const [owner, other] = await ethers.getSigners();
    const contract = await ethers.deployContract("AuditLedger");
    await contract.waitForDeployment();
    return { contract, owner, other };
  }

  it("sets owner on deployment", async function () {
    const { contract, owner } = await deployFixture();
    expect(await contract.owner()).to.equal(owner.address);
  });

  it("appends a record and increments count", async function () {
    const { contract, owner } = await deployFixture();
    const hash = ethers.keccak256(ethers.toUtf8Bytes("evt-1"));
    const ts = 1715700000n;  // bigint — uint256 comes back as bigint in ethers v6

    await expect(contract.appendAuditRecord(hash, ts, "UserLoggedIn", owner.address))
      .to.emit(contract, "RecordAppended")
      .withArgs(hash, ts, "UserLoggedIn", owner.address);

    expect(await contract.getRecordsCount()).to.equal(1n);
    expect(await contract.isHashExists(hash)).to.equal(true);
  });

  it("returns record by index", async function () {
    const { contract, owner } = await deployFixture();
    const hash = ethers.keccak256(ethers.toUtf8Bytes("evt-2"));
    const ts = 1715700001n;  // bigint — ethers v6 returns uint256 as bigint

    await contract.appendAuditRecord(hash, ts, "EntityUpdated", owner.address);
    const record = await contract.getRecord(0);

    expect(record.eventHash).to.equal(hash);
    expect(record.timestamp).to.equal(ts);
    expect(record.eventType).to.equal("EntityUpdated");
    expect(record.source).to.equal(owner.address);
  });

  it("prevents non-owner from appending", async function () {
    const { contract, owner, other } = await deployFixture();
    const hash = ethers.keccak256(ethers.toUtf8Bytes("evt-3"));

    await expect(
      contract.connect(other).appendAuditRecord(hash, 1715700002, "UserLoggedIn", owner.address)
    ).to.be.revertedWithCustomError(contract, "Unauthorized");
  });

  it("rejects duplicate hash", async function () {
    const { contract, owner } = await deployFixture();
    const hash = ethers.keccak256(ethers.toUtf8Bytes("evt-4"));

    await contract.appendAuditRecord(hash, 1715700003, "UserLoggedIn", owner.address);

    await expect(
      contract.appendAuditRecord(hash, 1715700004, "UserLoggedIn", owner.address)
    ).to.be.revertedWithCustomError(contract, "DuplicateHash");
  });

  it("reverts when index does not exist", async function () {
    const { contract } = await deployFixture();

    await expect(contract.getRecord(0)).to.be.revertedWithCustomError(
      contract,
      "IndexOutOfBounds"
    );
  });

  it("rejects empty eventType", async function () {
    const { contract, owner } = await deployFixture();
    const hash = ethers.keccak256(ethers.toUtf8Bytes("evt-empty-type"));

    await expect(
      contract.appendAuditRecord(hash, 1715700005, "", owner.address)
    ).to.be.revertedWithCustomError(contract, "EmptyEventType");
  });

  it("rejects zero source address", async function () {
    const { contract } = await deployFixture();
    const hash = ethers.keccak256(ethers.toUtf8Bytes("evt-zero-addr"));

    await expect(
      contract.appendAuditRecord(hash, 1715700006n, "UserLoggedIn", ethers.ZeroAddress)
    ).to.be.revertedWithCustomError(contract, "ZeroSourceAddress");
  });

  it("allows owner to transfer ownership", async function () {
    const { contract, owner, other } = await deployFixture();

    await expect(contract.transferOwnership(other.address))
      .to.emit(contract, "OwnershipTransferred")
      .withArgs(owner.address, other.address);

    expect(await contract.owner()).to.equal(other.address);
  });

  it("new owner can append after transfer", async function () {
    const { contract, owner, other } = await deployFixture();
    await contract.transferOwnership(other.address);

    const hash = ethers.keccak256(ethers.toUtf8Bytes("evt-after-transfer"));
    await contract.connect(other).appendAuditRecord(hash, 1715700007n, "UserLoggedIn", other.address);
    expect(await contract.getRecordsCount()).to.equal(1n);
  });

  it("non-owner cannot transfer ownership", async function () {
    const { contract, other } = await deployFixture();
    await expect(
      contract.connect(other).transferOwnership(other.address)
    ).to.be.revertedWithCustomError(contract, "Unauthorized");
  });

  it("reverts transferOwnership to zero address", async function () {
    const { contract } = await deployFixture();
    await expect(
      contract.transferOwnership(ethers.ZeroAddress)
    ).to.be.revertedWithCustomError(contract, "ZeroOwnerAddress");
  });
});


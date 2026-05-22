const hre = require("hardhat");
const { deployAuditLedger } = require("./deployAuditLedger");

async function main() {
  await deployAuditLedger(hre);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});


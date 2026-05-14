const hre = require("hardhat");

async function main() {
  const contract = await hre.ethers.deployContract("AuditLedger");
  await contract.waitForDeployment();

  const address = await contract.getAddress();
  console.log(`AuditLedger deployed to: ${address}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});


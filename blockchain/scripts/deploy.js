const hre = require("hardhat");

async function deployAuditLedger(runtimeEnvironment = hre) {
  const contract = await runtimeEnvironment.ethers.deployContract("AuditLedger");
  await contract.waitForDeployment();

  const address = await contract.getAddress();
  console.log(`AuditLedger deployed to: ${address}`);
  return address;
}

async function main() {
  await deployAuditLedger();
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}

module.exports = {
  deployAuditLedger,
  main
};


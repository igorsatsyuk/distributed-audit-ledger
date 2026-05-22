async function deployAuditLedger(runtimeEnvironment) {
  const contract = await runtimeEnvironment.ethers.deployContract("AuditLedger");
  await contract.waitForDeployment();

  const address = await contract.getAddress();
  console.log(`AuditLedger deployed to: ${address}`);
  return address;
}

module.exports = {
  deployAuditLedger
};


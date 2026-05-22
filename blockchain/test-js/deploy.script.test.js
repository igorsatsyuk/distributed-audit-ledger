const { expect } = require("chai");
const { deployAuditLedger } = require("../scripts/deployAuditLedger");

describe("deploy script", function () {
  it("deployAuditLedger deploys AuditLedger and returns address", async function () {
    const deployedAddress = "0x1234567890123456789012345678901234567890";
    let requestedContractName;
    let loggedMessage;

    const fakeRuntime = {
      ethers: {
        deployContract: async (contractName) => {
          requestedContractName = contractName;
          return {
            waitForDeployment: async () => {},
            getAddress: async () => deployedAddress
          };
        }
      }
    };

    const originalLog = console.log;
    console.log = (message) => {
      loggedMessage = message;
    };

    try {
      const result = await deployAuditLedger(fakeRuntime);

      expect(requestedContractName).to.equal("AuditLedger");
      expect(result).to.equal(deployedAddress);
      expect(loggedMessage).to.equal(`AuditLedger deployed to: ${deployedAddress}`);
    } finally {
      console.log = originalLog;
    }
  });
});


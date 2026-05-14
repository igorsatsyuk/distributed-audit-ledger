require("@nomicfoundation/hardhat-toolbox");
require("dotenv").config();

const GANACHE_RPC_URL = process.env.GANACHE_RPC_URL || "http://127.0.0.1:8545";
const GANACHE_PRIVATE_KEY = process.env.GANACHE_PRIVATE_KEY;

/** @type import('hardhat/config').HardhatUserConfig */
module.exports = {
  solidity: {
    version: "0.8.24",
    settings: {
      optimizer: {
        enabled: true,
        runs: 200
      }
    }
  },
  networks: {
    ganache: {
      url: GANACHE_RPC_URL,
      accounts: GANACHE_PRIVATE_KEY ? [GANACHE_PRIVATE_KEY] : []
    }
  }
};


require("@nomicfoundation/hardhat-toolbox");
require("dotenv").config();

const GANACHE_RPC_URL = process.env.GANACHE_RPC_URL || "http://127.0.0.1:8545";
const GANACHE_PRIVATE_KEY = process.env.GANACHE_PRIVATE_KEY || "";

// Accept only a valid 0x-prefixed 32-byte hex private key.
const PRIVATE_KEY_RE = /^0x[0-9a-fA-F]{64}$/;
const validKey = PRIVATE_KEY_RE.test(GANACHE_PRIVATE_KEY);

// When no valid key is provided, omit `accounts` entirely so Hardhat falls back
// to the RPC node's unlocked accounts (e.g. Ganache's pre-funded wallets).
// Setting accounts:[] would cause a confusing "no signer" error at deploy time.
const signerAccounts = validKey ? { accounts: [GANACHE_PRIVATE_KEY] } : {};

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
    // Connects to a node started with `npx hardhat node`
    localhost: {
      url: "http://127.0.0.1:8545",
      ...signerAccounts
    },
    // Connects to the Ganache container from deploy/docker-compose.yml
    ganache: {
      url: GANACHE_RPC_URL,
      ...signerAccounts
    }
  }
};


require("@nomicfoundation/hardhat-toolbox");
require("dotenv").config();

const GANACHE_RPC_URL = process.env.GANACHE_RPC_URL || "http://127.0.0.1:8545";
const GANACHE_PRIVATE_KEY = process.env.GANACHE_PRIVATE_KEY || "";

// Accept only a valid 0x-prefixed 32-byte hex private key.
const PRIVATE_KEY_RE = /^0x[0-9a-fA-F]{64}$/;
const validGanacheKey = PRIVATE_KEY_RE.test(GANACHE_PRIVATE_KEY);

// When no valid key is provided for Ganache, omit `accounts` so Hardhat falls
// back to the node's unlocked RPC accounts (pre-funded Ganache wallets).
// Setting accounts:[] would produce a confusing "no signer" error at deploy time.
const ganacheSignerAccounts = validGanacheKey ? { accounts: [GANACHE_PRIVATE_KEY] } : {};

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
    // Connects to a node started with `npx hardhat node`.
    // No `accounts` needed — Hardhat node provides its own pre-funded test accounts.
    localhost: {
      url: "http://127.0.0.1:8545"
    },
    // Connects to the Ganache container from deploy/docker-compose.yml.
    // Set GANACHE_PRIVATE_KEY in blockchain/.env to sign transactions.
    ganache: {
      url: GANACHE_RPC_URL,
      ...ganacheSignerAccounts
    }
  }
};

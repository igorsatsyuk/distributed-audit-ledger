package lt.satsyuk.distributed.audit.auditwriter.blockchain;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that deploys {@code AuditLedger.sol} to a real Ganache node started via
 * Testcontainers and verifies the {@link AuditLedgerContract} wrapper end-to-end.
 *
 * <p>Goals:
 * <ul>
 *   <li>Verify that {@link AuditLedgerContract#appendAuditRecord} produces a mined receipt
 *       with {@code status=0x1} and that the hash is subsequently readable via
 *       {@link AuditLedgerContract#isHashExists} — catching ABI encoding bugs that
 *       pass unit tests but fail on a real EVM.</li>
 *   <li>Verify that {@link AuditLedgerContract#owner} returns the deployer's address,
 *       confirming the {@code owner()} ABI wrapper is correctly encoded.</li>
 * </ul>
 *
 * <p>Bytecode is read from {@code test/resources/AuditLedger.bytecode}.  Update that file
 * (re-run {@code npm run compile} in {@code blockchain/} and copy the new {@code bytecode}
 * field from {@code artifacts/contracts/AuditLedger.sol/AuditLedger.json}) whenever the
 * Solidity contract changes.
 *
 * <p>Automatically skipped when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class AuditLedgerContractGanacheTest {

    /**
     * Deterministic account 0 private key for Ganache mnemonic
     * {@code "test test test test test test test test test test test junk"}
     * (HD path m/44'/60'/0'/0/0) — the same mnemonic used by {@code deploy/docker-compose.yml}.
     */
    private static final String OWNER_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> GANACHE = new GenericContainer<>(
            DockerImageName.parse("trufflesuite/ganache:v7.9.1"))
            .withCommand(
                    "--wallet.mnemonic",
                    "test test test test test test test test test test test junk",
                    "--chain.chainId", "1337",
                    "--server.host", "0.0.0.0",
                    "--server.port", "8545")
            .withExposedPorts(8545)
            .waitingFor(Wait.forListeningPort());

    private static Web3j web3j;
    private static Credentials owner;
    private static AuditLedgerContract contract;

    @BeforeAll
    static void deployContract() throws Exception {
        String rpcUrl = "http://" + GANACHE.getHost() + ":" + GANACHE.getMappedPort(8545);
        web3j   = Web3j.build(new HttpService(rpcUrl));
        owner   = Credentials.create(OWNER_PRIVATE_KEY);

        String contractAddress = deployAuditLedger(web3j, owner);
        contract = AuditLedgerContract.load(contractAddress, web3j, owner, new DefaultGasProvider());
    }

    @AfterAll
    static void shutdown() {
        if (web3j != null) {
            web3j.shutdown();
        }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Happy-path: {@code appendAuditRecord} anchors a new hash and {@code isHashExists}
     * confirms it is stored on-chain.  Exercises the full ABI encoding / EVM execution
     * path of both write and read wrapper methods.
     */
    @Test
    void appendAuditRecord_anchorsHashAndIsHashExistsConfirmsIt() throws Exception {
        byte[] hash          = randomHash();
        BigInteger timestamp = BigInteger.valueOf(Instant.now().getEpochSecond());

        assertThat(contract.isHashExists(hash))
                .as("hash must not exist before anchoring")
                .isFalse();

        TransactionReceipt receipt = contract.appendAuditRecord(
                hash, timestamp, "USER_LOGGED_IN", owner.getAddress());

        assertThat(receipt).isNotNull();
        assertThat(receipt.isStatusOK())
                .as("appendAuditRecord transaction must be mined with status 0x1")
                .isTrue();

        assertThat(contract.isHashExists(hash))
                .as("isHashExists must return true after the hash is anchored on-chain")
                .isTrue();
    }

    /** Read-only path: {@code isHashExists} returns {@code false} for an un-anchored hash. */
    @Test
    void isHashExists_returnsFalseForUnknownHash() throws Exception {
        assertThat(contract.isHashExists(randomHash()))
                .as("isHashExists must return false for a hash that was never anchored")
                .isFalse();
    }

    /**
     * Ownership check: {@code owner()} returns the deployer's address, confirming
     * {@code buildOwnerFunction()} is correctly encoded.
     */
    @Test
    void owner_returnsDeployerAddress() throws Exception {
        assertThat(contract.owner())
                .as("owner() must equal the deployer address")
                .isEqualToIgnoringCase(owner.getAddress());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static byte[] randomHash() {
        byte[] hash = new byte[32];
        new SecureRandom().nextBytes(hash);
        return hash;
    }

    /**
     * Deploys {@code AuditLedger} by reading its deployment bytecode from
     * {@code AuditLedger.bytecode} on the test classpath and submitting a raw signed
     * contract-creation transaction.  Returns the deployed contract address once mined.
     */
    private static String deployAuditLedger(Web3j web3j, Credentials credentials) throws Exception {
        String bytecode;
        try (InputStream in = AuditLedgerContractGanacheTest.class
                .getClassLoader().getResourceAsStream("AuditLedger.bytecode")) {
            if (in == null) {
                throw new IllegalStateException(
                        "AuditLedger.bytecode not found on test classpath. "
                        + "Copy the 'bytecode' field from "
                        + "blockchain/artifacts/contracts/AuditLedger.sol/AuditLedger.json "
                        + "to audit-writer-service/src/test/resources/AuditLedger.bytecode.");
            }
            bytecode = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        }

        long chainId = web3j.ethChainId().send().getChainId().longValue();

        BigInteger nonce = web3j
                .ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.LATEST)
                .send()
                .getTransactionCount();

        RawTransaction rawTx = RawTransaction.createContractTransaction(
                nonce,
                DefaultGasProvider.GAS_PRICE,
                BigInteger.valueOf(3_000_000L),
                BigInteger.ZERO,
                bytecode);

        byte[] signed = TransactionEncoder.signMessage(rawTx, chainId, credentials);
        EthSendTransaction response = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
        if (response.hasError()) {
            throw new IllegalStateException(
                    "AuditLedger deploy failed: " + response.getError().getMessage());
        }

        TransactionReceipt receipt = new PollingTransactionReceiptProcessor(web3j, 500L, 60)
                .waitForTransactionReceipt(response.getTransactionHash());

        String contractAddress = receipt.getContractAddress();
        if (contractAddress == null || contractAddress.isBlank()) {
            throw new IllegalStateException(
                    "Deploy tx mined but contractAddress is null; status=" + receipt.getStatus());
        }
        return contractAddress;
    }
}


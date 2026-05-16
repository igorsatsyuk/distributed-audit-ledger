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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * <p>Bytecode source: the committed classpath resource {@code /AuditLedger.bytecode},
 * which must be kept in sync with {@code AuditLedger.sol}.  Using a single deterministic
 * source ensures the test produces identical results on every machine and CI run.
 * Regenerate by running {@code npm run compile} in the {@code blockchain} module and
 * copying the {@code bytecode} field from
 * {@code blockchain/artifacts/contracts/AuditLedger.sol/AuditLedger.json}
 * into {@code src/test/resources/AuditLedger.bytecode}; update
 * {@code src/test/resources/AuditLedger.sol.sha256} in the same change.
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

    /**
     * Guards the committed bytecode snapshot against contract drift.
     *
     * <p>Compares the SHA-256 of {@code blockchain/contracts/AuditLedger.sol}
     * (normalized to LF line endings) against the committed
     * {@code /AuditLedger.sol.sha256} marker. This check runs on clean checkouts,
     * so stale {@code /AuditLedger.bytecode} snapshots are detected even when
     * Hardhat artifacts are absent.
     */
    @Test
    void auditLedgerBytecodeSnapshot_staysInSyncWithAuditLedgerSolSource() throws Exception {
        Path sourcePath = locateAuditLedgerSource();
        if (sourcePath == null) {
            throw new IllegalStateException("Cannot find blockchain/contracts/AuditLedger.sol from current working directory");
        }

        String expectedHash;
        try (InputStream stream = AuditLedgerContractGanacheTest.class.getResourceAsStream("/AuditLedger.sol.sha256")) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Classpath resource /AuditLedger.sol.sha256 not found. "
                                + "Update test resources when AuditLedger.sol changes.");
            }
            expectedHash = new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
        }

        String actualHash = sha256HexNormalized(Files.readString(sourcePath));
        assertThat(actualHash)
                .as("AuditLedger.sol hash marker must match current Solidity source")
                .isEqualTo(expectedHash);
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
     * Deploys {@code AuditLedger} by reading its deployment bytecode from the committed
     * {@code /AuditLedger.bytecode} classpath resource, then submitting a raw signed
     * contract-creation transaction.  Returns the deployed contract address once mined.
     */
    private static String deployAuditLedger(Web3j web3j, Credentials credentials) throws Exception {
        String bytecode = resolveAuditLedgerBytecode();

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

    /**
     * Reads the {@code AuditLedger} deployment bytecode from the committed classpath
     * resource {@code /AuditLedger.bytecode}.
     *
     * <p>Using the committed snapshot guarantees identical bytecode on every machine
     * and CI run.  Regenerate it when {@code AuditLedger.sol} changes by running
     * {@code npm run compile} in the blockchain module and copying the {@code bytecode}
     * field from {@code blockchain/artifacts/contracts/AuditLedger.sol/AuditLedger.json}.
     */
    private static String resolveAuditLedgerBytecode() throws IOException {
        try (InputStream stream =
                     AuditLedgerContractGanacheTest.class.getResourceAsStream("/AuditLedger.bytecode")) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Classpath resource /AuditLedger.bytecode not found. "
                                + "Regenerate it with `npm run compile` in the blockchain module and copy "
                                + "the bytecode field from "
                                + "blockchain/artifacts/contracts/AuditLedger.sol/AuditLedger.json.");
            }
            String bytecode = new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
            if (bytecode.isBlank() || "0x".equals(bytecode)) {
                throw new IllegalStateException(
                        "Classpath AuditLedger.bytecode resource is empty or contains only '0x'");
            }
            return bytecode;
        }
    }

    private static Path locateAuditLedgerSource() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && current != null; i++) {
            Path candidate = current
                    .resolve("blockchain")
                    .resolve("contracts")
                    .resolve("AuditLedger.sol");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String sha256HexNormalized(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String normalized = source.replace("\r\n", "\n");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}


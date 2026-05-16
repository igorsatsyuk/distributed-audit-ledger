package lt.satsyuk.distributed.audit.auditwriter.service;

import lt.satsyuk.distributed.audit.auditwriter.blockchain.AuditLedgerContract;
import lt.satsyuk.distributed.audit.auditwriter.config.Web3jValidationUtils;
import lt.satsyuk.distributed.audit.auditwriter.config.Web3jProperties;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

/**
 * Anchors event hashes on the AuditLedger smart contract.
 *
 * <p>Workflow for each incoming {@link AuditEvent}:
 * <ol>
 *   <li>Compute SHA-256 hash via {@link HashCalculationService}</li>
 *   <li>Check whether the hash is already recorded (idempotency guard)</li>
 *   <li>Call {@code appendAuditRecord} on the contract</li>
 * </ol>
 *
 * <p>A simple retry loop (up to the configured service-side write-retry attempts) handles
 * transient network / RPC failures.  Duplicate hashes are treated as successful
 * no-ops when detected either by the pre-flight {@code isHashExists} check or
 * by a post-failure {@code isHashExists} re-check.  The post-failure re-check is
 * the reliable path because the Solidity custom error {@code DuplicateHash} is
 * often surfaced only as encoded revert data by JSON-RPC clients rather than
 * as a literal text message.
 *
 * <p>If {@code web3j.private-key} or {@code web3j.contract-address} is missing or
 * malformed, or if the configured key does not own the contract ({@code Unauthorized}
 * revert from the {@code onlyOwner} modifier), or if the contract cannot be probed at the
 * configured address, the service throws {@link BlockchainNotConfiguredException}.  The
 * Kafka error handler lets that exception go through the configured fixed back-off and then
 * the recoverer re-throws it without publishing to the DLT, so the offset stays uncommitted
 * until the configuration is corrected.
 *
 * <p>The future-timestamp tolerance window is configurable via
 * {@code web3j.future-timestamp-tolerance-seconds} (default 300 s) so operators
 * can compensate for producer clock-skew or intentionally future-dated fixtures.
 *
 * <p>The service-side write retry budget is configurable via
 * {@code web3j.blockchain-write-retries} (default 3) so the combined service + Kafka
 * retry behavior stays explicit instead of relying on a hidden constant.
 */
@Service
public class BlockchainWriterService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainWriterService.class);
    private static final String UNAUTHORIZED_SELECTOR = selector("Unauthorized()");

    static final int MAX_RETRIES      = 3;
    static final long RETRY_DELAY_MS  = 1_000L;

    private final Web3j web3j;
    private final Optional<Credentials> credentials;
    private final Web3jProperties props;
    private final HashCalculationService hashService;
    private final long retryDelayMs;

    /**
     * {@code credentials} is optional — injected as empty Optional when the private-key
     * property is blank (see {@link lt.satsyuk.distributed.audit.auditwriter.config.Web3jConfig}).
     * Using Optional ensures the service will not wait for a non-existent Credentials bean
     * at startup; instead, the configuration check happens when anchorEvent() is called.
     */
    public BlockchainWriterService(Web3j web3j,
                                    Optional<Credentials> credentials,
                                    Web3jProperties props,
                                    HashCalculationService hashService) {
        this(web3j, credentials, props, hashService, RETRY_DELAY_MS);
    }

    BlockchainWriterService(Web3j web3j,
                            Optional<Credentials> credentials,
                            Web3jProperties props,
                            HashCalculationService hashService,
                            long retryDelayMs) {
        this.web3j       = web3j;
        this.credentials = credentials;
        this.props       = props;
        this.hashService = hashService;
        this.retryDelayMs = retryDelayMs;
    }

    /**
     * Anchors the SHA-256 hash of {@code event} on-chain.
     *
     * @param event the domain event to anchor
     * @throws BlockchainNotConfiguredException if {@code web3j.private-key} or
     *         {@code web3j.contract-address} is missing — non-retryable, offset uncommitted
     * @throws NonRecoverableEventException if the event is malformed — non-retryable, forwarded to DLT
     * @throws BlockchainWriteException after all retry attempts are exhausted
     */
    public void anchorEvent(AuditEvent event) {
        validateEvent(event);
        validateConfiguration();

        byte[] hash = hashService.computeHash(event);
        String hexHash = HashCalculationService.toHexString(hash);
        log.debug("[#7] Anchoring event {} with hash {}", event.getEventId(), hexHash);

        Exception lastException = null;
        int maxRetries = props.getBlockchainWriteRetries();
        // Service-side retries are explicit/configurable; Kafka's error handler applies the
        // outer redelivery/backoff layer around this loop.
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                writeToBlockchain(event, hash, hexHash, attempt);
                return; // success
            } catch (DuplicateHashException e) {
                log.info("[#7] Hash {} already on-chain — skipping (event {})", hexHash, event.getEventId());
                return; // idempotent — treat as success
            } catch (NonRecoverableEventException e) {
                throw e; // propagate immediately without retry
            } catch (BlockchainNotConfiguredException e) {
                throw e; // propagate immediately without retry/DLT
            } catch (Exception e) {
                lastException = e;
                log.warn("[#7] Attempt {}/{} failed for event {}: {}", attempt, maxRetries, event.getEventId(), e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        sleep(retryDelayMs);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new BlockchainWriteException("Retry interrupted while anchoring event " + event.getEventId(), interrupted);
                    }
                }
            }
        }
        log.error("[#7] All {} attempts exhausted for event {}", maxRetries, event.getEventId(), lastException);
        throw new BlockchainWriteException("Failed to anchor event " + event.getEventId() + " after " + maxRetries + " attempts", lastException);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void writeToBlockchain(AuditEvent event, byte[] hash, String hexHash, int attempt) throws Exception {
        AuditLedgerContract contract = AuditLedgerContract.load(
                props.getContractAddress(), web3j, credentials.get(), new DefaultGasProvider());

        // Pre-flight idempotency check — avoids paying gas for a tx that would revert.
        final boolean hashExists;
        try {
            hashExists = contract.isHashExists(hash);
        } catch (Exception probeEx) {
            throw new BlockchainNotConfiguredException(
                    "Cannot verify AuditLedger at configured contract address (isHashExists probe failed)", probeEx);
        }
        if (hashExists) {
            throw new DuplicateHashException(hexHash);
        }

        String signer = credentials.get().getAddress();
        String owner;
        try {
            owner = contract.owner();
        } catch (Exception probeEx) {
            throw new BlockchainNotConfiguredException(
                    "Cannot resolve AuditLedger owner at configured contract address", probeEx);
        }
        if (owner == null || owner.isBlank()) {
            throw new BlockchainNotConfiguredException(
                    "Cannot resolve AuditLedger owner at configured contract address (empty owner response)");
        }
        if (!owner.equalsIgnoreCase(signer)) {
            throw new BlockchainNotConfiguredException(
                    "Configured signer does not own AuditLedger contract (owner=" + owner + ", signer=" + signer + ")");
        }

        BigInteger timestamp = BigInteger.valueOf(event.getOccurredAt().getEpochSecond());
        String eventType = event.getEventType().name();
        String source    = signer;

        log.debug("[#7] Sending appendAuditRecord tx (attempt {}): hash={} eventType={}", attempt, hexHash, eventType);
        try {
            TransactionReceipt receipt = contract.appendAuditRecord(hash, timestamp, eventType, source);
            if (receipt == null) {
                throw new RuntimeException("appendAuditRecord returned null receipt");
            }
            if (receipt.getStatus() != null && !receipt.isStatusOK()) {
                String revertReason = receipt.getRevertReason();
                String reasonPart = (revertReason == null || revertReason.isBlank())
                        ? ""
                        : " (revertReason=" + revertReason + ")";
                throw new RuntimeException("appendAuditRecord mined with failed status " + receipt.getStatus()
                        + " for tx " + receipt.getTransactionHash() + reasonPart);
            }
            log.info("[#7] Hash {} anchored on-chain. Tx={} Block={}",
                    hexHash, receipt.getTransactionHash(), receipt.getBlockNumber());
        } catch (Exception appendEx) {
            // Legacy heuristic: some JSON-RPC implementations surface the Solidity custom-error
            // name in the message; use it as a fast-path check.
            if (isDuplicateHashRevert(appendEx)) {
                throw new DuplicateHashException(hexHash);
            }
            // Contract owner mismatch (onlyOwner modifier): treat as a configuration error so
            // the offset stays uncommitted rather than draining events to the DLT.
            if (isOwnershipRevert(appendEx)) {
                throw new BlockchainNotConfiguredException(
                        "appendAuditRecord reverted with Unauthorized — the configured key does not own the contract",
                        appendEx);
            }
            // Deterministic fallback: the DuplicateHash custom error is often returned only as
            // ABI-encoded revert data (not as literal text), so re-check isHashExists to decide
            // reliably whether this is an idempotent duplicate or a real failure.
            try {
                if (contract.isHashExists(hash)) {
                    log.debug("[#7] Post-failure isHashExists confirmed duplicate for hash {}", hexHash);
                    throw new DuplicateHashException(hexHash);
                }
            } catch (DuplicateHashException dupe) {
                throw dupe; // re-throw duplicate signal to caller
            } catch (Exception checkEx) {
                log.warn("[#7] Post-failure isHashExists check failed for hash {}: {}", hexHash, checkEx.getMessage());
            }
            throw appendEx; // original transient error; retry loop will handle it
        }
    }

    private void validateConfiguration() {
        if (credentials.isEmpty()) {
            String privateKey = props.getPrivateKey();
            if (privateKey != null && !privateKey.isBlank()) {
                throw new BlockchainNotConfiguredException(
                        "Blockchain writer is not configured: web3j.private-key is malformed (expected 64 hex chars, optional 0x prefix)");
            }
            throw new BlockchainNotConfiguredException(
                    "Blockchain writer is not configured: web3j.private-key is missing");
        }
        String privateKey = props.getPrivateKey();
        if (privateKey != null && !privateKey.isBlank() && !Web3jValidationUtils.isValidPrivateKey(privateKey)) {
            throw new BlockchainNotConfiguredException(
                    "Blockchain writer is not configured: web3j.private-key is malformed (expected 64 hex chars, optional 0x prefix)");
        }
        String addr = props.getContractAddress();
        if (addr == null || addr.isBlank()) {
            throw new BlockchainNotConfiguredException(
                    "Blockchain writer is not configured: web3j.contract-address is missing");
        }
        if (!Web3jValidationUtils.isValidContractAddress(addr)) {
            throw new BlockchainNotConfiguredException(
                    "Blockchain writer has a malformed contract address (expected 0x + 40 hex chars): " + addr);
        }
        if (Web3jValidationUtils.isZeroAddress(addr)) {
            throw new BlockchainNotConfiguredException(
                    "Blockchain writer has invalid contract address: zero-address is not allowed");
        }
        if (props.getBlockchainWriteRetries() <= 0) {
            throw new BlockchainNotConfiguredException(
                    "web3j.blockchain-write-retries must be > 0 but was " + props.getBlockchainWriteRetries());
        }
    }

    private void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private void validateEvent(AuditEvent event) {
        if (event == null) {
            throw new NonRecoverableEventException("Event is null");
        }
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            throw new NonRecoverableEventException("Event eventId is null or blank");
        }
        if (event.getOccurredAt() == null) {
            throw new NonRecoverableEventException("Event occurredAt is null for eventId=" + event.getEventId());
        }
        if (event.getEventType() == null) {
            throw new NonRecoverableEventException("Event eventType is null for eventId=" + event.getEventId());
        }
        long toleranceSeconds = props.getFutureTimestampToleranceSeconds();
        if (toleranceSeconds < 0) {
            throw new BlockchainNotConfiguredException(
                    "web3j.future-timestamp-tolerance-seconds must be >= 0 but was " + toleranceSeconds);
        }
        if (event.getOccurredAt().isAfter(Instant.now().plusSeconds(toleranceSeconds))) {
            throw new NonRecoverableEventException(
                    "Event occurredAt is more than " + toleranceSeconds
                            + "s in the future for eventId=" + event.getEventId());
        }
        if (event.getOccurredAt().getEpochSecond() < 0) {
            throw new NonRecoverableEventException(
                    "Event occurredAt is before Unix epoch for eventId=" + event.getEventId());
        }
    }

    private boolean isDuplicateHashRevert(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("DuplicateHash")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isOwnershipRevert(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("unauthorized")
                        || normalized.contains("onlyowner")
                        || normalized.contains(UNAUTHORIZED_SELECTOR)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static String selector(String signature) {
        return Hash.sha3String(signature).substring(0, 10).toLowerCase();
    }

    // -------------------------------------------------------------------------
    // Domain exceptions
    // -------------------------------------------------------------------------

    /** Thrown when the hash already exists on-chain (pre-flight or post-failure {@code isHashExists} check). */
    static class DuplicateHashException extends RuntimeException {
        DuplicateHashException(String hash) {
            super("Hash already exists on-chain: " + hash);
        }
    }

    /**
     * Thrown when an event is malformed and must not be retried.
     *
     * <p>This exception is registered as non-retryable in the Kafka
     * {@link org.springframework.kafka.listener.DefaultErrorHandler} so that
     * malformed records skip the backoff cycle and are forwarded immediately to the DLT.
     */
    public static class NonRecoverableEventException extends RuntimeException {
        public NonRecoverableEventException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when the blockchain writer is not configured (missing private key or contract address).
     *
     * <p>Unlike {@link BlockchainWriteException}, this exception is treated as a
     * <em>non-DLT</em> failure by the Kafka error handler: the consumer offset stays
     * uncommitted so the record is redelivered automatically once configuration is added,
     * rather than being silently drained to the dead-letter topic.
     */
    public static class BlockchainNotConfiguredException extends BlockchainWriteException {
        public BlockchainNotConfiguredException(String message) {
            super(message, null);
        }

        public BlockchainNotConfiguredException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Thrown after all retry attempts are exhausted. */
    public static class BlockchainWriteException extends RuntimeException {
        public BlockchainWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


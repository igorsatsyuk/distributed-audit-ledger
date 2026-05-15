package lt.satsyuk.distributed.audit.auditwriter.service;

import lt.satsyuk.distributed.audit.auditwriter.blockchain.AuditLedgerContract;
import lt.satsyuk.distributed.audit.auditwriter.config.Web3jProperties;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigInteger;
import java.time.Instant;

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
 * <p>A simple retry loop (up to {@value #MAX_RETRIES} attempts) handles
 * transient network / RPC failures.  Duplicate hashes are treated as successful
 * no-ops when detected either by the pre-flight {@code isHashExists} check or
 * when the contract revert message contains {@code "DuplicateHash"}.
 * Note: JSON-RPC revert message format is implementation-specific; detection via
 * message string matching is a best-effort fallback for the race-condition path.
 *
 * <p>If {@code web3j.private-key} or {@code web3j.contract-address} is not
 * configured, the service fails anchoring so Kafka can redeliver later rather
 * than silently dropping on-chain writes.
 */
@Service
public class BlockchainWriterService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainWriterService.class);

    static final int MAX_RETRIES      = 3;
    static final long RETRY_DELAY_MS  = 1_000L;

    private final Web3j web3j;
    private final Credentials credentials;
    private final Web3jProperties props;
    private final HashCalculationService hashService;
    private final long retryDelayMs;

    /**
     * {@code credentials} is optional — injected as {@code null} when the private-key
     * property is blank (see {@link lt.satsyuk.distributed.audit.auditwriter.config.Web3jConfig}).
     */
    public BlockchainWriterService(Web3j web3j,
                                    @Autowired(required = false) Credentials credentials,
                                    Web3jProperties props,
                                    HashCalculationService hashService) {
        this(web3j, credentials, props, hashService, RETRY_DELAY_MS);
    }

    BlockchainWriterService(Web3j web3j,
                            Credentials credentials,
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
     */
    public void anchorEvent(AuditEvent event) {
        if (!isConfigured()) {
            throw new BlockchainWriteException(
                    "Blockchain writer is not configured (missing private key or contract address)",
                    null
            );
        }

        validateEvent(event);

        byte[] hash = hashService.computeHash(event);
        String hexHash = HashCalculationService.toHexString(hash);
        log.debug("[#7] Anchoring event {} with hash {}", event.getEventId(), hexHash);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                writeToBlockchain(event, hash, hexHash, attempt);
                return; // success
            } catch (DuplicateHashException e) {
                log.info("[#7] Hash {} already on-chain — skipping (event {})", hexHash, event.getEventId());
                return; // idempotent — treat as success
            } catch (NonRecoverableEventException e) {
                throw new BlockchainWriteException("Non-recoverable event for anchoring: " + event.getEventId(), e);
            } catch (Exception e) {
                if (isDuplicateHashRevert(e)) {
                    log.info("[#7] Hash {} already on-chain (contract revert) — skipping (event {})", hexHash, event.getEventId());
                    return;
                }
                lastException = e;
                log.warn("[#7] Attempt {}/{} failed for event {}: {}", attempt, MAX_RETRIES, event.getEventId(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        sleep(retryDelayMs);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new BlockchainWriteException("Retry interrupted while anchoring event " + event.getEventId(), interrupted);
                    }
                }
            }
        }
        log.error("[#7] All {} attempts exhausted for event {}", MAX_RETRIES, event.getEventId(), lastException);
        throw new BlockchainWriteException("Failed to anchor event " + event.getEventId() + " after " + MAX_RETRIES + " attempts", lastException);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void writeToBlockchain(AuditEvent event, byte[] hash, String hexHash, int attempt) throws Exception {
        AuditLedgerContract contract = AuditLedgerContract.load(
                props.getContractAddress(), web3j, credentials, new DefaultGasProvider());

        // Pre-flight idempotency check — avoids paying gas for a tx that would revert.
        // Note: a race condition between this check and appendAuditRecord can still result
        // in a DuplicateHash revert from the contract, which is caught below.
        if (contract.isHashExists(hash)) {
            throw new DuplicateHashException(hexHash);
        }

        BigInteger timestamp = BigInteger.valueOf(event.getOccurredAt().getEpochSecond());
        String eventType = event.getEventType().name();
        String source    = credentials.getAddress();

        log.debug("[#7] Sending appendAuditRecord tx (attempt {}): hash={} eventType={}", attempt, hexHash, eventType);
        TransactionReceipt receipt = contract.appendAuditRecord(hash, timestamp, eventType, source);
        log.info("[#7] Hash {} anchored on-chain. Tx={} Block={}",
                hexHash, receipt.getTransactionHash(), receipt.getBlockNumber());
    }

    private boolean isConfigured() {
        return credentials != null
                && props.getContractAddress() != null
                && !props.getContractAddress().isBlank();
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
        if (event.getOccurredAt().isAfter(Instant.now().plusSeconds(300))) {
            throw new NonRecoverableEventException("Event occurredAt is unexpectedly in the future for eventId=" + event.getEventId());
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

    // -------------------------------------------------------------------------
    // Domain exceptions
    // -------------------------------------------------------------------------

    /** Thrown when the hash already exists on-chain (pre-flight {@code isHashExists} check). */
    static class DuplicateHashException extends RuntimeException {
        DuplicateHashException(String hash) {
            super("Hash already exists on-chain: " + hash);
        }
    }

    /** Thrown when an event is malformed and should not be retried. */
    static class NonRecoverableEventException extends RuntimeException {
        NonRecoverableEventException(String message) {
            super(message);
        }
    }

    /** Thrown after all retry attempts are exhausted. */
    public static class BlockchainWriteException extends RuntimeException {
        public BlockchainWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


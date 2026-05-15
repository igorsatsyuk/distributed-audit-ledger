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
 * transient network / RPC failures.  Duplicate-hash conflicts ({@code DuplicateHash}
 * error from the contract) are treated as successful no-ops.
 *
 * <p>If {@code web3j.private-key} or {@code web3j.contract-address} is not
 * configured the service logs a warning and skips the blockchain write, allowing
 * the application to start without a live Ganache node.
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

    /**
     * {@code credentials} is optional — injected as {@code null} when the private-key
     * property is blank (see {@link lt.satsyuk.distributed.audit.auditwriter.config.Web3jConfig}).
     */
    public BlockchainWriterService(Web3j web3j,
                                    @Autowired(required = false) Credentials credentials,
                                    Web3jProperties props,
                                    HashCalculationService hashService) {
        this.web3j       = web3j;
        this.credentials = credentials;
        this.props       = props;
        this.hashService = hashService;
    }

    /**
     * Anchors the SHA-256 hash of {@code event} on-chain.
     *
     * @param event the domain event to anchor
     */
    public void anchorEvent(AuditEvent event) {
        if (!isConfigured()) {
            log.warn("[#7] Blockchain write skipped for event {} — web3j.private-key or " +
                     "web3j.contract-address not configured", event.getEventId());
            return;
        }

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
            } catch (Exception e) {
                lastException = e;
                log.warn("[#7] Attempt {}/{} failed for event {}: {}", attempt, MAX_RETRIES, event.getEventId(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    sleep(RETRY_DELAY_MS);
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

        // Idempotency check — avoids paying gas for a guaranteed-to-revert tx
        if (contract.isHashExists(hash)) {
            throw new DuplicateHashException(hexHash);
        }

        BigInteger timestamp = BigInteger.valueOf(
                event.getOccurredAt() != null ? event.getOccurredAt().getEpochSecond() : 0L);
        String eventType = event.getEventType() != null ? event.getEventType().name() : "UNKNOWN";
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

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Domain exceptions
    // -------------------------------------------------------------------------

    /** Thrown when the contract returns a {@code DuplicateHash} revert. */
    static class DuplicateHashException extends RuntimeException {
        DuplicateHashException(String hash) {
            super("Hash already exists on-chain: " + hash);
        }
    }

    /** Thrown after all retry attempts are exhausted. */
    public static class BlockchainWriteException extends RuntimeException {
        public BlockchainWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


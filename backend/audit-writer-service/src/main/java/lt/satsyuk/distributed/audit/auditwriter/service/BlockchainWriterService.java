package lt.satsyuk.distributed.audit.auditwriter.service;

import lt.satsyuk.distributed.audit.auditwriter.blockchain.AuditLedgerContract;
import lt.satsyuk.distributed.audit.auditwriter.config.Web3jValidationUtils;
import lt.satsyuk.distributed.audit.auditwriter.config.Web3jProperties;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigInteger;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadPoolExecutor;
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
 * <p>A simple retry loop (up to configured service-side write retries + initial attempt) handles
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
 * {@code web3j.blockchain-write-retries} (default 0). This value is interpreted as
 * retry count; total attempts are {@code retries + 1}. Kafka listener redelivery/back-off is
 * the outer retry layer, so the default keeps the combined attempt budget conservative.
 */
@Service
public class BlockchainWriterService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainWriterService.class);
    private static final String UNAUTHORIZED_SELECTOR = selector("Unauthorized()");
    private static final int MAX_BLOCKCHAIN_WRITE_RETRIES = 10;
    private static final int EVENT_ID_MAX_LENGTH = 36;
    private static final int AGGREGATE_ID_MAX_LENGTH = 128;
    private static final int USER_ID_MAX_LENGTH = 255;
    private static final String USER_AGGREGATE_PREFIX = "user:";
    private static final int MAX_STALE_IN_FLIGHT_OBSERVATIONS = 3;
    private static final int RECEIPT_EXECUTOR_THREADS = 2;
    private static final int RECEIPT_EXECUTOR_QUEUE_CAPACITY = 16;

    static final long RETRY_DELAY_MS = 1_000L;

    private static final AtomicInteger WAITER_COUNTER = new AtomicInteger(0);

    private final Web3j web3j;
    private final Credentials credentials;
    private final Web3jProperties props;
    private final HashCalculationService hashService;
    private final long retryDelayMs;
    private final ExecutorService receiptExecutor;
    private final boolean ownsReceiptExecutor;
    private final ConcurrentHashMap<String, InFlightWrite> inFlightWritesByHash = new ConcurrentHashMap<>();

    /**
     * Credentials are requested lazily from Spring so the service can still start when the
     * private-key bean is absent (see {@link lt.satsyuk.distributed.audit.auditwriter.config.Web3jConfig}).
     * Runtime validation still happens in {@link #anchorEvent(AuditEvent)}.
     */
    @Autowired
    public BlockchainWriterService(Web3j web3j,
                                    ObjectProvider<Credentials> credentialsProvider,
                                    Web3jProperties props,
                                    HashCalculationService hashService) {
        this(web3j, credentialsProvider.getIfAvailable(), props, hashService, RETRY_DELAY_MS);
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
        this.receiptExecutor = newReceiptExecutor();
        this.ownsReceiptExecutor = true;
    }

    BlockchainWriterService(Web3j web3j,
                            Credentials credentials,
                            Web3jProperties props,
                            HashCalculationService hashService,
                            long retryDelayMs,
                            ExecutorService receiptExecutor) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.props = props;
        this.hashService = hashService;
        this.retryDelayMs = retryDelayMs;
        this.receiptExecutor = receiptExecutor;
        this.ownsReceiptExecutor = false;
    }

    private static ExecutorService newReceiptExecutor() {
        // Use a bounded pool so slow or stuck receipt waits cannot grow without limit.
        // Web3j call cancellation is best-effort, so we cap both the worker count and the
        // queue length to keep repeated redeliveries from exhausting resources.
        return new ThreadPoolExecutor(
                RECEIPT_EXECUTOR_THREADS,
                RECEIPT_EXECUTOR_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(RECEIPT_EXECUTOR_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "audit-writer-receipt-waiter-" + WAITER_COUNTER.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    void shutdownReceiptExecutor() {
        if (ownsReceiptExecutor) {
            receiptExecutor.shutdownNow();
        }
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

        int maxAttempts = Math.addExact(props.getBlockchainWriteRetries(), 1);
        Optional<RuntimeException> lastException = anchorWithRetries(event, hash, hexHash, maxAttempts);
        if (lastException.isEmpty()) {
            return;
        }
        log.error("[#7] All {} attempts exhausted for event {}", maxAttempts, event.getEventId(), lastException.get());
        throw new BlockchainWriteException(
                "Failed to anchor event " + event.getEventId() + " after " + maxAttempts + " attempts",
                lastException.get());
    }

    private Optional<RuntimeException> anchorWithRetries(AuditEvent event, byte[] hash, String hexHash, int maxAttempts) {
        RuntimeException lastException = null;

        // Service-side retries are explicit/configurable; Kafka's error handler applies the
        // outer redelivery/backoff layer around this loop, so keep this inner budget small.
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                writeToBlockchain(event, hash, hexHash, attempt);
                return Optional.empty();
            } catch (DuplicateHashException _) {
                log.info("[#7] Hash {} already on-chain — skipping (event {})", hexHash, event.getEventId());
                return Optional.empty();
            } catch (ReceiptTimeoutException | NonRecoverableEventException | BlockchainNotConfiguredException e) {
                throw e;
            } catch (RuntimeException e) {
                lastException = e;
                log.warn("[#7] Attempt {}/{} failed for event {}: {}", attempt, maxAttempts, event.getEventId(), e.getMessage());
                pauseBeforeNextAttemptIfNeeded(event.getEventId(), attempt, maxAttempts);
            }
        }

        return Optional.ofNullable(lastException);
    }

    private void pauseBeforeNextAttemptIfNeeded(String eventId, int attempt, int maxAttempts) {
        if (attempt >= maxAttempts) {
            return;
        }
        try {
            sleep(retryDelayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BlockchainWriteException("Retry interrupted while anchoring event " + eventId, interrupted);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void writeToBlockchain(AuditEvent event, byte[] hash, String hexHash, int attempt) {
        Credentials signerCredentials = requireCredentials();
        AuditLedgerContract contract = AuditLedgerContract.load(
                props.getContractAddress(), web3j, signerCredentials, new DefaultGasProvider());

        // If a previous write for this hash is still in-flight, inspect it first instead of
        // blindly sending a new tx.
        awaitInFlightWriteIfPresent(contract, hash, hexHash);

        // Pre-flight idempotency check — avoids paying gas for a tx that would revert.
        final boolean hashExists;
        try {
            hashExists = contract.isHashExists(hash);
        } catch (RuntimeException probeEx) {
            if (isPermanentContractProbeFailure(probeEx)) {
                throw new BlockchainNotConfiguredException(
                        "Cannot verify AuditLedger at configured contract address (isHashExists probe failed)", probeEx);
            }
            // Transient RPC / node availability failures should use normal retry + DLT path.
            throw probeEx;
        }
        if (hashExists) {
            throw new DuplicateHashException(hexHash);
        }

        String signer = signerCredentials.getAddress();
        ensureSignerOwnsContract(contract, signer);

        Instant occurredAt = resolveOccurredAt(event);
        BigInteger timestamp = toContractTimestamp(occurredAt);
        String eventType = event.getEventType().name();

        log.debug("[#7] Sending appendAuditRecord tx (attempt {}): hash={} eventType={}", attempt, hexHash, eventType);
        try {
            TransactionReceipt receipt = waitForReceipt(contract, hash, hexHash, timestamp, eventType, signer);
            if (receipt == null) {
                throw new BlockchainWriteException("appendAuditRecord returned null receipt", null);
            }
            if (receipt.getStatus() == null || !receipt.isStatusOK()) {
                String revertReason = receipt.getRevertReason();
                String reasonPart = (revertReason == null || revertReason.isBlank())
                        ? ""
                        : " (revertReason=" + revertReason + ")";
                throw new BlockchainWriteException("appendAuditRecord mined with failed status " + receipt.getStatus()
                        + " for tx " + receipt.getTransactionHash() + reasonPart, null);
            }
            log.info("[#7] Hash {} anchored on-chain. Tx={} Block={}",
                    hexHash, receipt.getTransactionHash(), receipt.getBlockNumber());
        } catch (RuntimeException appendEx) {
            throw classifyAppendFailure(contract, hash, hexHash, appendEx);
        }
    }

    private void ensureSignerOwnsContract(AuditLedgerContract contract, String signer) {
        String owner = resolveContractOwner(contract);
        if (owner.equalsIgnoreCase(signer)) {
            return;
        }

        throw new BlockchainNotConfiguredException(
                "Configured signer does not own AuditLedger contract (owner=" + owner + ", signer=" + signer + ")");
    }

    private String resolveContractOwner(AuditLedgerContract contract) {
        String owner;
        try {
            owner = contract.owner();
        } catch (RuntimeException probeEx) {
            if (isPermanentContractProbeFailure(probeEx)) {
                throw new BlockchainNotConfiguredException(
                        "Cannot resolve AuditLedger owner at configured contract address", probeEx);
            }
            throw probeEx;
        }
        if (owner == null || owner.isBlank()) {
            throw new BlockchainNotConfiguredException(
                    "Cannot resolve AuditLedger owner at configured contract address (empty owner response)");
        }
        return owner;
    }

    private void validateConfiguration() {
        validateClientAddress();
        validatePrivateKeyConfiguration();
        validateContractAddressConfiguration();
        validateRetryAndTimeoutBounds();
    }

    private void validateClientAddress() {
        String clientAddress = props.getClientAddress();
        if (clientAddress == null || clientAddress.isBlank()) {
            throw new BlockchainNotConfiguredException(
                    "Blockchain writer is not configured: web3j.client-address is missing");
        }
        if (!Web3jValidationUtils.isValidClientAddress(clientAddress)) {
            String redactedClientAddress = redactUrl(clientAddress);
            throw new BlockchainNotConfiguredException(
                    "Blockchain writer has malformed web3j.client-address (expected http(s) URL): "
                            + redactedClientAddress);
        }
    }

    private void validatePrivateKeyConfiguration() {
        String privateKey = props.getPrivateKey();
        if (credentials == null) {
            if (privateKey != null && !privateKey.isBlank()) {
                throw new BlockchainNotConfiguredException(
                        "Blockchain writer is not configured: web3j.private-key is malformed (expected 64 hex chars, optional 0x prefix)");
            }
            throw new BlockchainNotConfiguredException(
                    "Blockchain writer is not configured: web3j.private-key is missing");
        }
        if (privateKey != null && !privateKey.isBlank() && !Web3jValidationUtils.isValidPrivateKey(privateKey)) {
            throw new BlockchainNotConfiguredException(
                    "Blockchain writer is not configured: web3j.private-key is malformed (expected 64 hex chars, optional 0x prefix)");
        }
    }

    private Credentials requireCredentials() {
        if (credentials == null) {
            throw new BlockchainNotConfiguredException(
                    "Blockchain writer is not configured: web3j.private-key is missing");
        }
        return credentials;
    }

    private void validateContractAddressConfiguration() {
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
    }

    private void validateRetryAndTimeoutBounds() {
        int retries = props.getBlockchainWriteRetries();
        if (retries < 0) {
            throw new BlockchainNotConfiguredException(
                    "web3j.blockchain-write-retries must be >= 0 but was " + retries);
        }
        if (retries > MAX_BLOCKCHAIN_WRITE_RETRIES) {
            throw new BlockchainNotConfiguredException(
                    "web3j.blockchain-write-retries must be <= " + MAX_BLOCKCHAIN_WRITE_RETRIES
                            + " but was " + retries);
        }

        int receiptTimeoutSeconds = props.getReceiptWaitTimeoutSeconds();
        if (receiptTimeoutSeconds <= 0) {
            throw new BlockchainNotConfiguredException(
                    "web3j.receipt-wait-timeout-seconds must be > 0 but was " + receiptTimeoutSeconds);
        }
    }

    private void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private static String redactUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "<empty>";
        }
        String url = rawUrl.trim();
        int schemeIdx = url.indexOf("://");
        if (schemeIdx < 0) {
            return "<redacted>";
        }
        int authorityStart = schemeIdx + 3;
        int slashIdx = url.indexOf('/', authorityStart);
        int questionIdx = url.indexOf('?', authorityStart);

        // Find where authority ends (before path or query)
        int authorityEnd = resolveAuthorityEnd(url, slashIdx, questionIdx);
        String authority = url.substring(authorityStart, authorityEnd);

        // Redact userinfo (before '@')
        int atIdx = authority.indexOf('@');
        String safeAuthority = atIdx >= 0
                ? "<redacted>@" + authority.substring(atIdx + 1)
                : authority;

        // Redact path and query components (may contain API tokens)
        return url.substring(0, authorityStart) + safeAuthority + "/<redacted>";
    }

    private static int resolveAuthorityEnd(String url, int slashIdx, int questionIdx) {
        if (slashIdx >= 0) {
            return slashIdx;
        }
        if (questionIdx >= 0) {
            return questionIdx;
        }
        return url.length();
    }

    private TransactionReceipt waitForReceipt(AuditLedgerContract contract,
                                              byte[] hash,
                                              String hexHash,
                                              BigInteger timestamp,
                                              String eventType,
                                              String source) {
        int timeoutSeconds = props.getReceiptWaitTimeoutSeconds();

        // Use computeIfAbsent to atomically check and submit for this hash,
        // ensuring only one concurrent transaction per hash is in-flight.
        InFlightWrite inFlightWrite = inFlightWritesByHash.computeIfAbsent(hexHash, hashKey ->
                new InFlightWrite(
                        submitAppendAuditRecord(contract, hash, timestamp, eventType, source),
                        System.nanoTime()
                )
        );

        return awaitInFlightResult(hexHash, inFlightWrite, timeoutSeconds);
    }

    private Future<TransactionReceipt> submitAppendAuditRecord(
            AuditLedgerContract contract,
            byte[] hash,
            BigInteger timestamp,
            String eventType,
            String source) {
        try {
            return receiptExecutor.submit(() -> contract.appendAuditRecord(hash, timestamp, eventType, source));
        } catch (RejectedExecutionException ex) {
            // Return a failed future that will surface as ReceiptTimeoutException to the caller
            CompletableFuture<TransactionReceipt> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new ReceiptTimeoutException(
                    "Receipt wait executor is saturated; transaction outcome is unknown and will be re-checked on Kafka redelivery",
                    ex));
            return failedFuture;
        }
    }

    private TransactionReceipt awaitInFlightResult(String hexHash,
                                                   InFlightWrite inFlightWrite,
                                                   int timeoutSeconds) {
        Future<TransactionReceipt> future = inFlightWrite.future();
        try {
            TransactionReceipt receipt = future.get(timeoutSeconds, TimeUnit.SECONDS);
            inFlightWritesByHash.remove(hexHash, inFlightWrite);
            return receipt;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ReceiptTimeoutException(
                    "Interrupted while waiting for appendAuditRecord receipt; transaction outcome is unknown",
                    ex);
        } catch (TimeoutException ex) {
            throw new ReceiptTimeoutException(
                    "Timed out waiting " + timeoutSeconds + "s for appendAuditRecord receipt", ex);
        } catch (ExecutionException ex) {
            inFlightWritesByHash.remove(hexHash, inFlightWrite);
            Throwable cause = ex.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ReceiptTimeoutException(
                        "Interrupted while executing appendAuditRecord asynchronously; transaction outcome is unknown",
                        interrupted);
            }
            throw new BlockchainWriteException("appendAuditRecord execution failed", cause);
        }
    }

    private void awaitInFlightWriteIfPresent(AuditLedgerContract contract, byte[] hash, String hexHash) {
        InFlightWrite inFlight = inFlightWritesByHash.get(hexHash);
        if (inFlight == null) {
            return;
        }

        int timeoutSeconds = props.getReceiptWaitTimeoutSeconds();
        if (!inFlight.future().isDone() && isInFlightWriteStale(inFlight, timeoutSeconds)) {
            final boolean hashExists;
            try {
                hashExists = contract.isHashExists(hash);
            } catch (RuntimeException probeEx) {
                throw new ReceiptTimeoutException(
                        "Timed out waiting " + timeoutSeconds + "s for appendAuditRecord receipt; "
                                + "existing in-flight write is still pending and chain re-check failed",
                        probeEx);
            }
            if (hashExists) {
                inFlightWritesByHash.remove(hexHash, inFlight);
                log.info("[#7] Stale in-flight appendAuditRecord already reflected on-chain for hash {}; "
                                + "treating redelivery as duplicate",
                        hexHash);
                throw new DuplicateHashException(hexHash);
            }

            int staleObservations = inFlight.incrementStaleObservations();
            if (staleObservations >= MAX_STALE_IN_FLIGHT_OBSERVATIONS && inFlightWritesByHash.remove(hexHash, inFlight)) {
                // Best-effort cleanup: if a write is repeatedly stale and still has no hash on-chain,
                // expire local tracking so a later redelivery can attempt a fresh submission.
                inFlight.future().cancel(true);
                throw new ReceiptTimeoutException(
                        "Timed out waiting " + timeoutSeconds + "s for appendAuditRecord receipt; "
                                + "stale in-flight write exceeded observation limit and tracking was expired for recovery",
                        null);
            }

            throw new ReceiptTimeoutException(
                    "Timed out waiting " + timeoutSeconds + "s for appendAuditRecord receipt; "
                            + "existing in-flight write is still pending and remains tracked",
                    null);
        }

        final TransactionReceipt receipt;
        try {
            receipt = awaitInFlightResult(hexHash, inFlight, timeoutSeconds);
        } catch (ReceiptTimeoutException timeout) {
            throw timeout;
        } catch (RuntimeException appendEx) {
            throw classifyAppendFailure(contract, hash, hexHash, appendEx);
        }

        if (receipt == null) {
            throw classifyAppendFailure(contract, hash, hexHash,
                    new RuntimeException("appendAuditRecord returned null receipt"));
        }
        if (receipt.getStatus() == null || !receipt.isStatusOK()) {
            throw classifyAppendFailure(contract, hash, hexHash, failedReceiptException(receipt));
        }
        log.info("[#7] Hash {} already has an in-flight tx that is now mined successfully. Tx={} Block={}",
                hexHash, receipt.getTransactionHash(), receipt.getBlockNumber());
        throw new DuplicateHashException(hexHash);
    }

    private boolean isInFlightWriteStale(InFlightWrite inFlightWrite, int timeoutSeconds) {
        long staleThresholdSeconds = Math.max(1L, Math.multiplyExact(timeoutSeconds, 2L));
        long ageNanos = System.nanoTime() - inFlightWrite.submittedAtNanos();
        return ageNanos >= TimeUnit.SECONDS.toNanos(staleThresholdSeconds);
    }

    private RuntimeException classifyAppendFailure(AuditLedgerContract contract,
                                            byte[] hash,
                                            String hexHash,
                                            RuntimeException appendEx) {
        // Legacy heuristic: some JSON-RPC implementations surface the Solidity custom-error
        // name in the message; use it as a fast-path check.
        if (isDuplicateHashRevert(appendEx)) {
            return new DuplicateHashException(hexHash);
        }
        // Contract owner mismatch (onlyOwner modifier): treat as a configuration error so
        // the offset stays uncommitted rather than draining events to the DLT.
        if (isOwnershipRevert(appendEx)) {
            return new BlockchainNotConfiguredException(
                    "appendAuditRecord reverted with Unauthorized — the configured key does not own the contract",
                    appendEx);
        }
        // Deterministic fallback: the DuplicateHash custom error is often returned only as
        // ABI-encoded revert data (not as literal text), so re-check isHashExists to decide
        // reliably whether this is an idempotent duplicate or a real failure.
        try {
            if (contract.isHashExists(hash)) {
                log.debug("[#7] Post-failure isHashExists confirmed duplicate for hash {}", hexHash);
                return new DuplicateHashException(hexHash);
            }
        } catch (RuntimeException checkEx) {
            log.warn("[#7] Post-failure isHashExists check failed for hash {}: {}", hexHash, checkEx.getMessage());
        }
        return appendEx;
    }

    private RuntimeException failedReceiptException(TransactionReceipt receipt) {
        String revertReason = receipt.getRevertReason();
        String reasonPart = (revertReason == null || revertReason.isBlank())
                ? ""
                : " (revertReason=" + revertReason + ")";
        return new RuntimeException("appendAuditRecord mined with failed status " + receipt.getStatus()
                + " for tx " + receipt.getTransactionHash() + reasonPart);
    }

    private static final class InFlightWrite {
        private final Future<TransactionReceipt> future;
        private final long submittedAtNanos;
        private final AtomicInteger staleObservations = new AtomicInteger(0);

        private InFlightWrite(Future<TransactionReceipt> future, long submittedAtNanos) {
            this.future = future;
            this.submittedAtNanos = submittedAtNanos;
        }

        Future<TransactionReceipt> future() {
            return future;
        }

        long submittedAtNanos() {
            return submittedAtNanos;
        }

        int incrementStaleObservations() {
            return staleObservations.incrementAndGet();
        }
    }

    private void validateEvent(AuditEvent event) {
        if (event == null) {
            throw new NonRecoverableEventException("Event is null");
        }
        if (event.getEventId() == null) {
            throw new NonRecoverableEventException("Event eventId is null");
        }
        if (event.getEventId().length() > EVENT_ID_MAX_LENGTH) {
            throw new NonRecoverableEventException("Event eventId exceeds " + EVENT_ID_MAX_LENGTH + " chars for eventId=" + event.getEventId());
        }
        if (event.getEventType() == null) {
            throw new NonRecoverableEventException("Event eventType is null for eventId=" + event.getEventId());
        }

        Instant occurredAt = resolveOccurredAt(event);
        long toleranceSeconds = props.getFutureTimestampToleranceSeconds();
        if (toleranceSeconds < 0) {
            throw new BlockchainNotConfiguredException(
                    "web3j.future-timestamp-tolerance-seconds must be >= 0 but was " + toleranceSeconds);
        }
        if (occurredAt.isAfter(Instant.now().plusSeconds(toleranceSeconds))) {
            log.warn("[#7] Event {} occurredAt={} exceeds configured future tolerance {}s; "
                            + "continuing to stay aligned with event-store acceptance",
                    event.getEventId(), occurredAt, toleranceSeconds);
        }
        if (event instanceof UserLoggedInEvent loginEvent) {
            validateUserLoggedInEvent(loginEvent, event.getEventId());
        }
    }

    private void validateUserLoggedInEvent(UserLoggedInEvent event, String eventId) {
        String userId = event.getUserId();
        if (userId != null && userId.length() > USER_ID_MAX_LENGTH) {
            throw new NonRecoverableEventException("UserLoggedInEvent userId exceeds "
                    + USER_ID_MAX_LENGTH + " chars for eventId=" + eventId);
        }
        int aggregateIdLength = userId != null
                ? USER_AGGREGATE_PREFIX.length() + userId.length()
                : eventId.length();
        if (aggregateIdLength > AGGREGATE_ID_MAX_LENGTH) {
            throw new NonRecoverableEventException("UserLoggedInEvent aggregate_id length exceeds "
                    + AGGREGATE_ID_MAX_LENGTH + " chars for eventId=" + eventId);
        }
    }

    private Instant resolveOccurredAt(AuditEvent event) {
        return event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now();
    }

    private BigInteger toContractTimestamp(Instant occurredAt) {
        long epochSeconds = occurredAt.getEpochSecond();
        if (epochSeconds < 0) {
            log.warn("[#7] occurredAt={} is before Unix epoch; normalizing on-chain timestamp to 0", occurredAt);
            return BigInteger.ZERO;
        }
        return BigInteger.valueOf(epochSeconds);
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

    private boolean isPermanentContractProbeFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                // Heuristic for deterministic misconfiguration (wrong/non-contract address).
                if (normalized.contains("no contract")
                        || normalized.contains("empty value")
                        || normalized.contains("code at address")) {
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

    /**
     * Thrown when receipt polling times out but the transaction outcome is still unknown.
     *
     * <p>This is deliberately not retried inside {@link #anchorEvent(AuditEvent)} because the
     * transaction may already be in flight; the Kafka redelivery path will re-check the
     * on-chain hash after the configured back-off instead of submitting another write immediately.
     */
    public static class ReceiptTimeoutException extends BlockchainWriteException {
        public ReceiptTimeoutException(String message, Throwable cause) {
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


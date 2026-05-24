package lt.satsyuk.distributed.audit.query.service;

import lt.satsyuk.distributed.audit.query.api.AuditIntegrityCheckResponse;
import lt.satsyuk.distributed.audit.query.blockchain.AuditLedgerBlockchainClient;
import lt.satsyuk.distributed.audit.query.model.AuditEventRecord;
import lt.satsyuk.distributed.audit.query.repository.AuditLogQueryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class BatchIntegrityChecker {

    private final AuditLogQueryRepository auditLogQueryRepository;
    private final AuditLedgerBlockchainClient blockchainClient;

    public BatchIntegrityChecker(AuditLogQueryRepository auditLogQueryRepository,
                                 AuditLedgerBlockchainClient blockchainClient) {
        this.auditLogQueryRepository = auditLogQueryRepository;
        this.blockchainClient = blockchainClient;
    }

    public Mono<BatchIntegrityCheckResult> runCheck(int batchSize) {
        if (batchSize <= 0) {
            return Mono.error(new QueryValidationException("reconciliation.batch-size must be > 0"));
        }

        return runBatch(batchSize, 0L, new BatchAccumulator());
    }

    private Mono<BatchIntegrityCheckResult> runBatch(int batchSize, long offset, BatchAccumulator accumulator) {
        return auditLogQueryRepository.findReconciliationBatch(batchSize, offset)
                .collectList()
                .flatMap(records -> {
                    if (records.isEmpty()) {
                        return Mono.just(accumulator.toResult());
                    }

                    return Flux.fromIterable(records)
                            .concatMap(this::evaluateRecord)
                            .doOnNext(accumulator::add)
                            .then(Mono.defer(() -> {
                                if (records.size() < batchSize) {
                                    return Mono.just(accumulator.toResult());
                                }
                                return runBatch(batchSize, offset + records.size(), accumulator);
                            }));
                });
    }

    private Mono<RecordOutcome> evaluateRecord(AuditEventRecord eventRecord) {
        String eventHash = normalizeHash(eventRecord.getEventHash());
        if (eventHash == null) {
            return Mono.just(RecordOutcome.pending());
        }

        return blockchainClient.inspectEventHash(eventHash)
                .map(blockchainRecord -> mapBlockchainResult(eventRecord, eventHash, blockchainRecord))
                .onErrorResume(error -> Mono.just(RecordOutcome.mismatch(new ReconciliationMismatch(
                        eventRecord.getId(),
                        eventRecord.getEventId(),
                        eventHash,
                        "MISMATCH",
                        "BLOCKCHAIN_CHECK_FAILED: " + error.getMessage()
                ))));
    }

    private RecordOutcome mapBlockchainResult(AuditEventRecord eventRecord,
                                              String eventHash,
                                              AuditIntegrityCheckResponse.BlockchainRecord blockchainRecord) {
        if (blockchainRecord.exists()) {
            return RecordOutcome.onChain();
        }
        return RecordOutcome.mismatch(new ReconciliationMismatch(
                eventRecord.getId(),
                eventRecord.getEventId(),
                eventHash,
                "MISMATCH",
                "HASH_NOT_FOUND_ON_CHAIN"
        ));
    }

    private String normalizeHash(String eventHash) {
        if (eventHash == null || eventHash.isBlank()) {
            return null;
        }
        return eventHash.trim();
    }

    private static final class BatchAccumulator {

        private long checkedEvents;
        private long onChainEvents;
        private long pendingEvents;
        private long mismatchEvents;
        private final List<ReconciliationMismatch> mismatches = new ArrayList<>();

        void add(RecordOutcome outcome) {
            checkedEvents++;
            switch (outcome.status) {
                case ON_CHAIN -> onChainEvents++;
                case PENDING -> pendingEvents++;
                case MISMATCH -> {
                    mismatchEvents++;
                    if (outcome.mismatch != null) {
                        mismatches.add(outcome.mismatch);
                    }
                }
            }
        }

        BatchIntegrityCheckResult toResult() {
            return new BatchIntegrityCheckResult(
                    checkedEvents,
                    onChainEvents,
                    pendingEvents,
                    mismatchEvents,
                    List.copyOf(mismatches)
            );
        }
    }

    private enum OutcomeStatus {
        ON_CHAIN,
        PENDING,
        MISMATCH
    }

    private static final class RecordOutcome {
        private final OutcomeStatus status;
        private final ReconciliationMismatch mismatch;

        private RecordOutcome(OutcomeStatus status, ReconciliationMismatch mismatch) {
            this.status = status;
            this.mismatch = mismatch;
        }

        static RecordOutcome onChain() {
            return new RecordOutcome(OutcomeStatus.ON_CHAIN, null);
        }

        static RecordOutcome pending() {
            return new RecordOutcome(OutcomeStatus.PENDING, null);
        }

        static RecordOutcome mismatch(ReconciliationMismatch mismatch) {
            return new RecordOutcome(OutcomeStatus.MISMATCH, mismatch);
        }
    }
}


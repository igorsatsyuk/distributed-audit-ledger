package lt.satsyuk.distributed.audit.query.service;

import lt.satsyuk.distributed.audit.query.api.AuditIntegrityCheckResponse;
import lt.satsyuk.distributed.audit.query.blockchain.AuditLedgerBlockchainClient;
import lt.satsyuk.distributed.audit.query.model.AuditEventRecord;
import lt.satsyuk.distributed.audit.query.repository.AuditLogQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchIntegrityCheckerTest {

    private static final String HASH_1 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String HASH_2 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private AuditLogQueryRepository auditLogQueryRepository;

    @Mock
    private AuditLedgerBlockchainClient blockchainClient;

    private BatchIntegrityChecker checker;

    @BeforeEach
    void setUp() {
        checker = new BatchIntegrityChecker(auditLogQueryRepository, blockchainClient);
    }

    @Test
    void runCheckAggregatesOnChainPendingAndMismatchAcrossBatches() {
        AuditEventRecord onChain = buildEventRecord(1L, "evt-1", HASH_1);
        AuditEventRecord pending = buildEventRecord(2L, "evt-2", " ");
        AuditEventRecord mismatch = buildEventRecord(3L, "evt-3", HASH_2);

        when(auditLogQueryRepository.findReconciliationBatch(2, 0L)).thenReturn(Flux.just(onChain, pending));
        when(auditLogQueryRepository.findReconciliationBatch(2, 2L)).thenReturn(Flux.just(mismatch));

        when(blockchainClient.inspectEventHash(HASH_1))
                .thenReturn(Mono.just(new AuditIntegrityCheckResponse.BlockchainRecord(true, "0xabc", 1L, 2L)));
        when(blockchainClient.inspectEventHash(HASH_2))
                .thenReturn(Mono.just(new AuditIntegrityCheckResponse.BlockchainRecord(false, null, null, null)));

        BatchIntegrityCheckResult result = checker.runCheck(2).block();

        assertThat(result).isNotNull();
        assertThat(result.checkedEvents()).isEqualTo(3);
        assertThat(result.onChainEvents()).isEqualTo(1);
        assertThat(result.pendingEvents()).isEqualTo(1);
        assertThat(result.mismatchEvents()).isEqualTo(1);
        assertThat(result.mismatches()).hasSize(1);
        assertThat(result.mismatches().getFirst().reason()).isEqualTo("HASH_NOT_FOUND_ON_CHAIN");
        verify(blockchainClient).inspectEventHash(HASH_1);
        verify(blockchainClient).inspectEventHash(HASH_2);
    }

    @Test
    void runCheckMarksBlockchainErrorsAsMismatch() {
        AuditEventRecord event = buildEventRecord(9L, "evt-9", HASH_1);

        when(auditLogQueryRepository.findReconciliationBatch(5, 0L)).thenReturn(Flux.just(event));
        when(blockchainClient.inspectEventHash(HASH_1)).thenReturn(Mono.error(new IllegalStateException("rpc down")));

        BatchIntegrityCheckResult result = checker.runCheck(5).block();

        assertThat(result).isNotNull();
        assertThat(result.checkedEvents()).isEqualTo(1);
        assertThat(result.mismatchEvents()).isEqualTo(1);
        assertThat(result.mismatches()).hasSize(1);
        assertThat(result.mismatches().getFirst().reason()).contains("BLOCKCHAIN_CHECK_FAILED");
    }

    @Test
    void runCheckRejectsInvalidBatchSize() {
        QueryValidationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                QueryValidationException.class,
                this::blockInvalidBatchRun
        );

        assertThat(exception.getMessage()).isEqualTo("reconciliation.batch-size must be > 0");
        verifyNoInteractions(auditLogQueryRepository);
        verifyNoInteractions(blockchainClient);
    }

    @Test
    void runCheckStopsWhenFirstBatchIsEmpty() {
        when(auditLogQueryRepository.findReconciliationBatch(10, 0L)).thenReturn(Flux.empty());

        BatchIntegrityCheckResult result = checker.runCheck(10).block();

        assertThat(result).isNotNull();
        assertThat(result.checkedEvents()).isZero();
        assertThat(result.mismatches()).isEmpty();
        verify(auditLogQueryRepository).findReconciliationBatch(10, 0L);
        verifyNoInteractions(blockchainClient);
    }

    private AuditEventRecord buildEventRecord(Long id, String eventId, String hash) {
        AuditEventRecord eventRecord = new AuditEventRecord();
        eventRecord.setId(id);
        eventRecord.setEventId(eventId);
        eventRecord.setEventHash(hash);
        return eventRecord;
    }

    private void blockInvalidBatchRun() {
        checker.runCheck(0).block();
    }
}


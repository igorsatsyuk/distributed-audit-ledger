package lt.satsyuk.distributed.audit.query.service;

import lt.satsyuk.distributed.audit.query.api.ReconciliationReportResponse;
import lt.satsyuk.distributed.audit.query.config.ReconciliationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationReportServiceTest {

    @Mock
    private BatchIntegrityChecker batchIntegrityChecker;

    private ReconciliationReportService reconciliationReportService;

    @BeforeEach
    void setUp() {
        ReconciliationProperties properties = new ReconciliationProperties();
        properties.setBatchSize(5);
        reconciliationReportService = new ReconciliationReportService(batchIntegrityChecker, properties);
    }

    @Test
    void runManualBuildsAndStoresLatestReport() {
        BatchIntegrityCheckResult result = new BatchIntegrityCheckResult(
                3,
                1,
                1,
                1,
                List.of(new ReconciliationMismatch(10L, "evt-10", "hash", "MISMATCH", "HASH_NOT_FOUND_ON_CHAIN"))
        );
        when(batchIntegrityChecker.runCheck(5)).thenReturn(Mono.just(result));

        ReconciliationReportResponse report = reconciliationReportService.runManual().block();

        assertThat(report).isNotNull();
        assertThat(report.trigger()).isEqualTo("MANUAL");
        assertThat(report.checkedEvents()).isEqualTo(3);
        assertThat(report.mismatchEvents()).isEqualTo(1);
        assertThat(report.mismatches()).hasSize(1);

        ReconciliationReportResponse latest = reconciliationReportService.latestReport().block();
        assertThat(latest).isEqualTo(report);
        verify(batchIntegrityChecker).runCheck(5);
    }

    @Test
    void runScheduledUsesScheduledTrigger() {
        when(batchIntegrityChecker.runCheck(5)).thenReturn(Mono.just(new BatchIntegrityCheckResult(0, 0, 0, 0, List.of())));

        ReconciliationReportResponse report = reconciliationReportService.runScheduled().block();

        assertThat(report).isNotNull();
        assertThat(report.trigger()).isEqualTo("SCHEDULED");
    }

    @Test
    void latestReportIsEmptyBeforeAnyRun() {
        assertThat(reconciliationReportService.latestReport().blockOptional()).isEmpty();
    }

    @Test
    void runReturnsConflictWhenAnotherRunInProgress() {
        Sinks.One<BatchIntegrityCheckResult> delayedResult = Sinks.one();
        when(batchIntegrityChecker.runCheck(5)).thenReturn(delayedResult.asMono());

        Mono<ReconciliationReportResponse> firstRun = reconciliationReportService.runManual();
        firstRun.subscribe();

        ReconciliationAlreadyRunningException exception = assertThrows(
                ReconciliationAlreadyRunningException.class,
                () -> reconciliationReportService.runManual().block()
        );

        assertThat(exception.getMessage()).isEqualTo("Reconciliation run is already in progress");
        delayedResult.tryEmitValue(new BatchIntegrityCheckResult(0, 0, 0, 0, List.of()));
    }
}


package lt.satsyuk.distributed.audit.query.jobs;

import lt.satsyuk.distributed.audit.query.api.ReconciliationReportResponse;
import lt.satsyuk.distributed.audit.query.service.ReconciliationReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationQuartzJobTest {

    @Mock
    private ReconciliationReportService reconciliationReportService;

    @Mock
    private JobExecutionContext jobExecutionContext;

    @Test
    void executeInternalRunsScheduledReconciliation() {
        ReconciliationQuartzJob job = new ReconciliationQuartzJob(reconciliationReportService);
        when(reconciliationReportService.runScheduled()).thenReturn(Mono.just(new ReconciliationReportResponse(
                "SCHEDULED",
                Instant.parse("2026-05-24T10:00:00Z"),
                Instant.parse("2026-05-24T10:00:01Z"),
                0,
                0,
                0,
                0,
                List.of()
        )));

        assertDoesNotThrow(() -> job.executeInternal(jobExecutionContext));
        verify(reconciliationReportService).runScheduled();
    }

    @Test
    void executeInternalAllowsEmptyScheduledResponse() {
        ReconciliationQuartzJob job = new ReconciliationQuartzJob(reconciliationReportService);
        when(reconciliationReportService.runScheduled()).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> job.executeInternal(jobExecutionContext));
        verify(reconciliationReportService).runScheduled();
    }

    @Test
    void executeInternalWrapsRuntimeExceptionIntoJobExecutionException() {
        ReconciliationQuartzJob job = new ReconciliationQuartzJob(reconciliationReportService);
        when(reconciliationReportService.runScheduled()).thenReturn(Mono.error(new IllegalStateException("boom")));

        assertThrows(JobExecutionException.class, () -> job.executeInternal(jobExecutionContext));
        verify(reconciliationReportService).runScheduled();
    }
}


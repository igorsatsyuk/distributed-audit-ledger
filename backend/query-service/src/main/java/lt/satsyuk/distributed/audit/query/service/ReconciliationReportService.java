package lt.satsyuk.distributed.audit.query.service;

import lt.satsyuk.distributed.audit.query.api.ReconciliationReportResponse;
import lt.satsyuk.distributed.audit.query.config.ReconciliationProperties;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ReconciliationReportService {

    private final BatchIntegrityChecker batchIntegrityChecker;
    private final ReconciliationProperties reconciliationProperties;
    private final AtomicBoolean runInProgress = new AtomicBoolean(false);

    private volatile ReconciliationReportResponse latestReport;

    public ReconciliationReportService(BatchIntegrityChecker batchIntegrityChecker,
                                       ReconciliationProperties reconciliationProperties) {
        this.batchIntegrityChecker = batchIntegrityChecker;
        this.reconciliationProperties = reconciliationProperties;
    }

    public Mono<ReconciliationReportResponse> runManual() {
        return run("MANUAL");
    }

    public Mono<ReconciliationReportResponse> runScheduled() {
        return run("SCHEDULED");
    }

    public Mono<ReconciliationReportResponse> latestReport() {
        return Mono.justOrEmpty(latestReport);
    }

    private Mono<ReconciliationReportResponse> run(String trigger) {
        if (!runInProgress.compareAndSet(false, true)) {
            return Mono.error(new ReconciliationAlreadyRunningException());
        }

        Instant startedAt = Instant.now();
        return batchIntegrityChecker.runCheck(reconciliationProperties.getBatchSize())
                .map(result -> toReport(trigger, startedAt, Instant.now(), result))
                .doOnNext(report -> latestReport = report)
                .doFinally(ignored -> runInProgress.set(false));
    }

    private ReconciliationReportResponse toReport(String trigger,
                                                  Instant startedAt,
                                                  Instant finishedAt,
                                                  BatchIntegrityCheckResult result) {
        return new ReconciliationReportResponse(
                trigger,
                startedAt,
                finishedAt,
                result.checkedEvents(),
                result.onChainEvents(),
                result.pendingEvents(),
                result.mismatchEvents(),
                result.mismatches().stream()
                        .map(mismatch -> new ReconciliationReportResponse.MismatchItem(
                                mismatch.auditLogId(),
                                mismatch.eventId(),
                                mismatch.eventHash(),
                                mismatch.status(),
                                mismatch.reason()
                        ))
                        .toList()
        );
    }
}


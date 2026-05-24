package lt.satsyuk.distributed.audit.query.jobs;

import lt.satsyuk.distributed.audit.query.api.ReconciliationReportResponse;
import lt.satsyuk.distributed.audit.query.service.ReconciliationReportService;
import org.jspecify.annotations.NonNull;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ReconciliationQuartzJob extends QuartzJobBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReconciliationQuartzJob.class);

    private final ReconciliationReportService reconciliationReportService;

    public ReconciliationQuartzJob(ReconciliationReportService reconciliationReportService) {
        this.reconciliationReportService = reconciliationReportService;
    }

    @Override
    protected void executeInternal(@NonNull JobExecutionContext context) throws JobExecutionException {
        try {
            ReconciliationReportResponse report = reconciliationReportService.runScheduled()
                    .block(Duration.ofMinutes(15));
            if (report != null) {
                LOGGER.info("Scheduled reconciliation completed: checked={}, mismatches={}, pending={}",
                        report.checkedEvents(), report.mismatchEvents(), report.pendingEvents());
            }
        } catch (RuntimeException exception) {
            throw new JobExecutionException("Scheduled reconciliation run failed", exception);
        }
    }
}


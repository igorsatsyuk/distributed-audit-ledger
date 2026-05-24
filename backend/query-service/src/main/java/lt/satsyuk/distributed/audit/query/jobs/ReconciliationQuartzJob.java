package lt.satsyuk.distributed.audit.query.jobs;

import lt.satsyuk.distributed.audit.query.api.ReconciliationReportResponse;
import lt.satsyuk.distributed.audit.query.config.ReconciliationProperties;
import lt.satsyuk.distributed.audit.query.service.ReconciliationAlreadyRunningException;
import lt.satsyuk.distributed.audit.query.service.ReconciliationReportService;
import org.jspecify.annotations.NonNull;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@DisallowConcurrentExecution
public class ReconciliationQuartzJob extends QuartzJobBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReconciliationQuartzJob.class);

    private ReconciliationReportService reconciliationReportService;
    private ReconciliationProperties reconciliationProperties;

    public ReconciliationQuartzJob() {
    }

    @Autowired
    public ReconciliationQuartzJob(ReconciliationReportService reconciliationReportService,
                                   ReconciliationProperties reconciliationProperties) {
        this.reconciliationReportService = reconciliationReportService;
        this.reconciliationProperties = reconciliationProperties;
    }

    @Override
    protected void executeInternal(@NonNull JobExecutionContext context) throws JobExecutionException {
        if (reconciliationReportService == null || reconciliationProperties == null) {
            throw new JobExecutionException("ReconciliationQuartzJob dependencies are not initialized");
        }
        try {
            ReconciliationReportResponse report = reconciliationReportService.runScheduled()
                    .block(resolveTimeout());
            if (report != null) {
                LOGGER.info("Scheduled reconciliation completed: checked={}, mismatches={}, pending={}",
                        report.checkedEvents(), report.mismatchEvents(), report.pendingEvents());
            }
        } catch (ReconciliationAlreadyRunningException _) {
            LOGGER.info("Scheduled reconciliation skipped: another run is already in progress");
        } catch (RuntimeException exception) {
            throw new JobExecutionException("Scheduled reconciliation run failed", exception);
        }
    }

    private Duration resolveTimeout() {
        Duration timeout = reconciliationProperties.getSchedule().getTimeout();
        return timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofMinutes(15)
                : timeout;
    }
}


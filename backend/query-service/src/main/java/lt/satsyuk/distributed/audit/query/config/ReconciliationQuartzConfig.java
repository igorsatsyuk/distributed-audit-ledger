package lt.satsyuk.distributed.audit.query.config;

import lt.satsyuk.distributed.audit.query.jobs.ReconciliationQuartzJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReconciliationQuartzConfig {

    @Bean
    @ConditionalOnProperty(prefix = "reconciliation.schedule", name = "enabled", havingValue = "true", matchIfMissing = true)
    JobDetail reconciliationJobDetail() {
        return JobBuilder.newJob(ReconciliationQuartzJob.class)
                .withIdentity("reconciliationJob")
                .storeDurably()
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "reconciliation.schedule", name = "enabled", havingValue = "true", matchIfMissing = true)
    Trigger reconciliationTrigger(JobDetail reconciliationJobDetail, ReconciliationProperties properties) {
        return TriggerBuilder.newTrigger()
                .forJob(reconciliationJobDetail)
                .withIdentity("reconciliationTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(properties.getSchedule().getCron()))
                .build();
    }
}


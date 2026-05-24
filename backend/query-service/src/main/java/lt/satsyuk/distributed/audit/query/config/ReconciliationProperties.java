package lt.satsyuk.distributed.audit.query.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reconciliation")
public class ReconciliationProperties {

    private int batchSize = 200;
    private final Schedule schedule = new Schedule();

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public static class Schedule {
        private boolean enabled = true;
        private String cron = "0 0/30 * * * ?";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }
}


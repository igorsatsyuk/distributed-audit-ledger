package lt.satsyuk.distributed.audit.command.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized Kafka topic names used by command-service.
 */
@ConfigurationProperties(prefix = "kafka.topics")
public class KafkaTopicsProperties {

    private String auditEvents;
    private String userLoginEvents;

    public String getAuditEvents() {
        return hasText(auditEvents) ? auditEvents : userLoginEvents;
    }

    public void setAuditEvents(String auditEvents) {
        this.auditEvents = auditEvents;
    }

    public String getUserLoginEvents() {
        return hasText(userLoginEvents) ? userLoginEvents : auditEvents;
    }

    public void setUserLoginEvents(String userLoginEvents) {
        this.userLoginEvents = userLoginEvents;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}


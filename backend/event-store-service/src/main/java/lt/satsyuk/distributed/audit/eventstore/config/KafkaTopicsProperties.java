package lt.satsyuk.distributed.audit.eventstore.config;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.topics")
@Setter
public class KafkaTopicsProperties {

    private String auditEvents;
    private String userLoginEvents = "user.login.events";

    public String getAuditEvents() {
        return hasText(auditEvents) ? auditEvents : userLoginEvents;
    }

    public String getUserLoginEvents() {
        return hasText(userLoginEvents) ? userLoginEvents : auditEvents;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}


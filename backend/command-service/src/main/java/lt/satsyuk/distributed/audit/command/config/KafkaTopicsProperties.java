package lt.satsyuk.distributed.audit.command.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized Kafka topic names used by command-service.
 */
@ConfigurationProperties(prefix = "kafka.topics")
public class KafkaTopicsProperties {

    private String userLoginEvents;

    public String getUserLoginEvents() {
        return userLoginEvents;
    }

    public void setUserLoginEvents(String userLoginEvents) {
        this.userLoginEvents = userLoginEvents;
    }
}


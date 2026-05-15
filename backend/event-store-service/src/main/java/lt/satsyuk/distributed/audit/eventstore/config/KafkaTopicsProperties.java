package lt.satsyuk.distributed.audit.eventstore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.topics")
@Getter
@Setter
@SuppressWarnings("unused")
public class KafkaTopicsProperties {

    private String userLoginEvents = "user.login.events";
}


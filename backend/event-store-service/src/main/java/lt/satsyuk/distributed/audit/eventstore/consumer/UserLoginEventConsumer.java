package lt.satsyuk.distributed.audit.eventstore.consumer;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import lt.satsyuk.distributed.audit.eventstore.config.KafkaTopicsProperties;
import lt.satsyuk.distributed.audit.eventstore.service.EventPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import static org.springframework.kafka.support.KafkaHeaders.RECEIVED_KEY;

@Component
public class UserLoginEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserLoginEventConsumer.class);

    private final EventPersistenceService eventPersistenceService;
    private final KafkaTopicsProperties kafkaTopicsProperties;

    public UserLoginEventConsumer(
            EventPersistenceService eventPersistenceService,
            KafkaTopicsProperties kafkaTopicsProperties
    ) {
        this.eventPersistenceService = eventPersistenceService;
        this.kafkaTopicsProperties = kafkaTopicsProperties;
    }

    @KafkaListener(topics = "${kafka.topics.user-login-events}")
    public void consume(AuditEvent event, @Header(value = RECEIVED_KEY, required = false) String key) {
        if (!(event instanceof UserLoggedInEvent userLoggedInEvent)) {
            log.warn("Skipping unsupported event type from topic [{}], key=[{}]", kafkaTopicsProperties.getUserLoginEvents(), key);
            return;
        }

        try {
            // Keep Kafka offset handling aligned with DB write result.
            eventPersistenceService.persist(userLoggedInEvent).block();
        } catch (RuntimeException ex) {
            log.error("Failed to persist event key=[{}]", key, ex);
            throw new IllegalStateException("Failed to persist event key=[" + key + "]", ex);
        }
    }
}


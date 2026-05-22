package lt.satsyuk.distributed.audit.eventstore.consumer;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.eventstore.config.KafkaTopicsProperties;
import lt.satsyuk.distributed.audit.eventstore.service.EventPersistenceService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final EventPersistenceService eventPersistenceService;
    private final KafkaTopicsProperties kafkaTopicsProperties;

    public AuditEventConsumer(
            EventPersistenceService eventPersistenceService,
            KafkaTopicsProperties kafkaTopicsProperties
    ) {
        this.eventPersistenceService = eventPersistenceService;
        this.kafkaTopicsProperties = kafkaTopicsProperties;
    }

    @KafkaListener(topics = "${kafka.topics.user-login-events}")
    public void consume(ConsumerRecord<String, AuditEvent> record) {
        AuditEvent event = record.value();
        String key = record.key();

        if (event == null) {
            throw new IllegalStateException(
                    "Received null event for key=[" + key + "] from topic=[" + kafkaTopicsProperties.getUserLoginEvents() + "]"
            );
        }

        try {
            // Keep Kafka offset handling aligned with DB write result.
            eventPersistenceService.persist(event).block();
        } catch (RuntimeException ex) {
            log.error("Failed to persist event key=[{}]", key, ex);
            throw new IllegalStateException("Failed to persist event key=[" + key + "]", ex);
        }
    }
}



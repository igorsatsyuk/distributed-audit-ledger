package lt.satsyuk.distributed.audit.eventstore.consumer;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.eventstore.service.EventPersistenceService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final EventPersistenceService eventPersistenceService;

    public AuditEventConsumer(EventPersistenceService eventPersistenceService) {
        this.eventPersistenceService = eventPersistenceService;
    }

    @KafkaListener(topics = "${kafka.topics.audit-events:${kafka.topics.user-login-events}}")
    public void consume(ConsumerRecord<String, AuditEvent> record) {
        AuditEvent event = record.value();
        String key = record.key();

        if (event == null) {
            throw new IllegalStateException(buildNullEventMessage(record));
        }

        try {
            // Keep Kafka offset handling aligned with DB write result.
            eventPersistenceService.persist(event).block();
        } catch (RuntimeException ex) {
            log.error("Failed to persist event key=[{}]", key, ex);
            throw new IllegalStateException("Failed to persist event key=[" + key + "]", ex);
        }
    }

    private String buildNullEventMessage(ConsumerRecord<String, AuditEvent> record) {
        StringBuilder message = new StringBuilder("Received null event")
                .append(" topic=[").append(record.topic()).append(']')
                .append(" partition=[").append(record.partition()).append(']')
                .append(" offset=[").append(record.offset()).append(']')
                .append(" key=[").append(record.key()).append(']');

        Header deserializationHeader =
                record.headers().lastHeader(ErrorHandlingDeserializer.VALUE_DESERIALIZER_EXCEPTION_HEADER);
        if (deserializationHeader != null) {
            message.append(" deserializationError=[").append(deserializeHeaderMessage(deserializationHeader)).append(']');
        }

        return message.toString();
    }

    private String deserializeHeaderMessage(Header deserializationHeader) {
        try {
            Object decoded = SerializationUtils.deserialize(deserializationHeader.value());
            if (decoded instanceof Throwable throwable && throwable.getMessage() != null) {
                return throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            }
            return "present";
        } catch (RuntimeException ex) {
            return "present (failed to decode header: " + ex.getClass().getSimpleName() + ")";
        }
    }
}



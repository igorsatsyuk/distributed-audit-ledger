package lt.satsyuk.distributed.audit.eventstore.consumer;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.eventstore.service.EventPersistenceService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);
    private static final LogAccessor logAccessor = new LogAccessor(AuditEventConsumer.class);
    /** Maximum time to wait for the DB write to complete before aborting the Kafka poll. */
    private static final Duration PERSIST_TIMEOUT = Duration.ofSeconds(30);

    private final EventPersistenceService eventPersistenceService;

    public AuditEventConsumer(EventPersistenceService eventPersistenceService) {
        this.eventPersistenceService = eventPersistenceService;
    }

    @KafkaListener(topics = "${kafka.topics.audit-events:${kafka.topics.user-login-events}}")
    public void consume(ConsumerRecord<String, AuditEvent> consumerRecord) {
        AuditEvent event = consumerRecord.value();
        String key = consumerRecord.key();

        if (event == null) {
            throw new SkippableDeserializationException(buildNullEventMessage(consumerRecord));
        }

        try {
            // block() is intentional: this runs on a Kafka thread (not a Reactor scheduler),
            // so blocking is safe here. timeout(...) adds fail-fast behavior for slow DB writes
            // and prevents indefinite blocking of the Kafka consumer thread.
            eventPersistenceService.persist(event)
                    .timeout(PERSIST_TIMEOUT)
                    .block();
        } catch (RuntimeException ex) {
            log.error("Failed to persist event key=[{}]", key, ex);
            throw new IllegalStateException("Failed to persist event key=[" + key + "]", ex);
        }
    }

    private String buildNullEventMessage(ConsumerRecord<String, AuditEvent> consumerRecord) {
        StringBuilder message = new StringBuilder("Received null event")
                .append(" topic=[").append(consumerRecord.topic()).append(']')
                .append(" partition=[").append(consumerRecord.partition()).append(']')
                .append(" offset=[").append(consumerRecord.offset()).append(']')
                .append(" key=[").append(consumerRecord.key()).append(']');

        Header deserializationHeader = findDeserializationHeader(consumerRecord);
        if (deserializationHeader != null) {
            message.append(" deserializationError=[").append(deserializeHeaderMessage(deserializationHeader)).append(']');
        }

        return message.toString();
    }

    private String deserializeHeaderMessage(Header deserializationHeader) {
        try {
            DeserializationException decoded =
                    SerializationUtils.byteArrayToDeserializationException(logAccessor, deserializationHeader);
            if (decoded != null && decoded.getMessage() != null) {
                return decoded.getClass().getSimpleName() + ": " + decoded.getMessage();
            }
            return "present";
        } catch (RuntimeException ex) {
            return "present (failed to decode header: " + ex.getClass().getSimpleName() + ")";
        }
    }

    private Header findDeserializationHeader(ConsumerRecord<String, AuditEvent> consumerRecord) {
        for (Header header : consumerRecord.headers()) {
            String key = header.key().toLowerCase(Locale.ROOT);
            if (key.contains("deserializer") && key.contains("exception")) {
                return header;
            }
        }
        return null;
    }

    public static final class SkippableDeserializationException extends IllegalStateException {
        public SkippableDeserializationException(String message) {
            super(message);
        }
    }
}



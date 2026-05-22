package lt.satsyuk.distributed.audit.eventstore.consumer;

import lt.satsyuk.distributed.audit.eventstore.config.KafkaTopicsProperties;
import lt.satsyuk.distributed.audit.eventstore.service.EventPersistenceService;

import java.util.Objects;

/**
 * @deprecated Prefer {@link AuditEventConsumer}. This class is not a Spring bean and is kept
 * only for source/binary compatibility with historical references.
 */
@Deprecated(since = "1.0")
public class UserLoginEventConsumer extends AuditEventConsumer {

    /**
     * Keeps the historical constructor signature for source/binary compatibility.
     */
    public UserLoginEventConsumer(
            EventPersistenceService eventPersistenceService,
            KafkaTopicsProperties kafkaTopicsProperties
    ) {
        Objects.requireNonNull(kafkaTopicsProperties, "kafkaTopicsProperties");
        super(eventPersistenceService);
    }
}


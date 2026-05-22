package lt.satsyuk.distributed.audit.eventstore.consumer;

import lt.satsyuk.distributed.audit.eventstore.config.KafkaTopicsProperties;
import lt.satsyuk.distributed.audit.eventstore.service.EventPersistenceService;

/**
 * @deprecated Prefer {@link AuditEventConsumer}. This class is not a Spring bean and is kept
 * only for source/binary compatibility with historical references.
 */
@Deprecated
public class UserLoginEventConsumer extends AuditEventConsumer {

    /**
     * Keeps the historical constructor signature for source/binary compatibility.
     */
    public UserLoginEventConsumer(
            EventPersistenceService eventPersistenceService,
            @SuppressWarnings("unused")
            KafkaTopicsProperties kafkaTopicsProperties
    ) {
        super(eventPersistenceService);
    }
}


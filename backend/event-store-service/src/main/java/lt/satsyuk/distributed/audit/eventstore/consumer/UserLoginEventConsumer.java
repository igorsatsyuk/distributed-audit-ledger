package lt.satsyuk.distributed.audit.eventstore.consumer;

import lt.satsyuk.distributed.audit.eventstore.config.KafkaTopicsProperties;
import lt.satsyuk.distributed.audit.eventstore.service.EventPersistenceService;

/**
 * @deprecated Prefer {@link AuditEventConsumer}; kept only for backward-compatible references.
 */
@Deprecated
public class UserLoginEventConsumer extends AuditEventConsumer {

    /**
     * Keeps the historical constructor signature for backward-compatible wiring.
     */
    public UserLoginEventConsumer(
            EventPersistenceService eventPersistenceService,
            @SuppressWarnings("unused")
            KafkaTopicsProperties kafkaTopicsProperties
    ) {
        super(eventPersistenceService);
    }
}


package lt.satsyuk.distributed.audit.eventstore.consumer;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
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

    public UserLoginEventConsumer(EventPersistenceService eventPersistenceService) {
        this.eventPersistenceService = eventPersistenceService;
    }

    @KafkaListener(topics = "${kafka.topics.user-login-events}")
    public void consume(AuditEvent event, @Header(value = RECEIVED_KEY, required = false) String key) {
        if (!(event instanceof UserLoggedInEvent userLoggedInEvent)) {
            log.warn("Skipping unsupported event type from topic user.login.events, key=[{}]", key);
            return;
        }

        eventPersistenceService.persist(userLoggedInEvent)
                .subscribe(
                        ignored -> {
                        },
                        error -> log.error("Failed to persist event key=[{}]", key, error)
                );
    }
}


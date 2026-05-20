package lt.satsyuk.distributed.audit.eventstore.consumer;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserProfileChangedEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import lt.satsyuk.distributed.audit.eventstore.config.KafkaTopicsProperties;
import lt.satsyuk.distributed.audit.eventstore.service.EventPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLoginEventConsumerTest {

    private static final KafkaTopicsProperties TOPICS = new KafkaTopicsProperties();

    @Mock
    private EventPersistenceService persistenceService;

    @Test
    void consumeDelegatesToPersistenceServiceForUserLoggedInEvent() {
        when(persistenceService.persist(any(AuditEvent.class))).thenReturn(Mono.empty());

        UserLoginEventConsumer consumer = new UserLoginEventConsumer(persistenceService, TOPICS);
        consumer.consume(UserLoggedInEvent.of("user-9", "198.51.100.22", "JUnit"), "event-key");

        verify(persistenceService).persist(any(UserLoggedInEvent.class));
    }

    @Test
    void consumeDelegatesToPersistenceServiceForAnotherSupportedSubtype() {
        when(persistenceService.persist(any(AuditEvent.class))).thenReturn(Mono.empty());

        UserLoginEventConsumer consumer = new UserLoginEventConsumer(persistenceService, TOPICS);
        AuditEvent profileChanged = UserProfileChangedEvent.of("user-9", java.util.Map.of("email", "u@example.com"));

        consumer.consume(profileChanged, "event-key");

        verify(persistenceService).persist(profileChanged);
    }

    @Test
    void consumeSkipsNullEvent() {
        UserLoginEventConsumer consumer = new UserLoginEventConsumer(persistenceService, TOPICS);

        consumer.consume(null, "event-key");

        verify(persistenceService, never()).persist(any(AuditEvent.class));
    }
}


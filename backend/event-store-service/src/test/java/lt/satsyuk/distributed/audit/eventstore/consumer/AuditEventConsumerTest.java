package lt.satsyuk.distributed.audit.eventstore.consumer;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserProfileChangedEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import lt.satsyuk.distributed.audit.eventstore.service.EventPersistenceService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private EventPersistenceService persistenceService;

    @Test
    void consumeDelegatesToPersistenceServiceForUserLoggedInEvent() {
        when(persistenceService.persist(any(AuditEvent.class))).thenReturn(Mono.empty());

        AuditEventConsumer consumer = new AuditEventConsumer(persistenceService);
        consumer.consume(recordOf(UserLoggedInEvent.of("user-9", "198.51.100.22", "JUnit")));

        verify(persistenceService).persist(any(UserLoggedInEvent.class));
    }

    @Test
    void consumeDelegatesToPersistenceServiceForAnotherSupportedSubtype() {
        when(persistenceService.persist(any(AuditEvent.class))).thenReturn(Mono.empty());

        AuditEventConsumer consumer = new AuditEventConsumer(persistenceService);
        AuditEvent profileChanged = UserProfileChangedEvent.of("user-9", java.util.Map.of("email", "u@example.com"));

        consumer.consume(recordOf(profileChanged));

        verify(persistenceService).persist(profileChanged);
    }

    @Test
    void consumeThrowsOnNullEvent() {
        AuditEventConsumer consumer = new AuditEventConsumer(persistenceService);
        ConsumerRecord<String, AuditEvent> nullRecord = recordOf(null);

        assertThrows(IllegalStateException.class, () -> consumer.consume(nullRecord));

        verify(persistenceService, never()).persist(any(AuditEvent.class));
    }

    private static ConsumerRecord<String, AuditEvent> recordOf(AuditEvent event) {
        return new ConsumerRecord<>("user.login.events", 0, 0L, "event-key", event);
    }
}



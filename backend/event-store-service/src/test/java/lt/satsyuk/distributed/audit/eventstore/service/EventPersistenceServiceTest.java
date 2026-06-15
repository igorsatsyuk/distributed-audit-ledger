package lt.satsyuk.distributed.audit.eventstore.service;

import io.r2dbc.postgresql.codec.Json;
import lt.satsyuk.distributed.audit.contracts.config.CanonicalObjectMapperFactory;
import lt.satsyuk.distributed.audit.event.EventType;
import lt.satsyuk.distributed.audit.event.EntityUpdatedEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import lt.satsyuk.distributed.audit.eventstore.model.StoredAuditEvent;
import lt.satsyuk.distributed.audit.eventstore.repository.StoredAuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventPersistenceServiceTest {

    @Mock
    private StoredAuditEventRepository repository;

    private EventPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new EventPersistenceService(
                CanonicalObjectMapperFactory.create(),
                new EventHashService(),
                repository
        );
    }

    @Test
    void persistMapsUserLoginEventToEntityAndStoresHash() {
        when(repository.save(any(StoredAuditEvent.class))).thenAnswer(EventPersistenceServiceTest::echoSavedEntity);

        UserLoggedInEvent event = UserLoggedInEvent.builder()
                .eventId("00000000-0000-0000-0000-000000000042")
                .eventType(EventType.USER_LOGGED_IN)
                .occurredAt(Instant.parse("2026-05-15T10:15:30Z"))
                .sourceService("command-service")
                .userId("user-42")
                .ipAddress("192.0.2.10")
                .userAgent("JUnit")
                .build();

        StoredAuditEvent saved = service.persist(event).block();

        assertNotNull(saved);
        assertEquals(event.getEventId(), saved.getEventId());
        assertEquals("user:user-42", saved.getAggregateId());
        assertEquals("USER_LOGGED_IN", saved.getEventType());
        assertEquals("user-42", saved.getUserId());
        assertNotNull(saved.getPayload());
        assertNotNull(saved.getEventHash());
        assertEquals(64, saved.getEventHash().length());
        assertEquals("661fa908d63fb896bf2f5b8aca9b0db207a73e99d04af2216005bdb98237607b", saved.getEventHash());
        assertNotNull(saved.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, Month.MAY, 15, 10, 15, 30), saved.getCreatedAt());

        ArgumentCaptor<StoredAuditEvent> captor = ArgumentCaptor.forClass(StoredAuditEvent.class);
        verify(repository).save(captor.capture());
        Json payload = captor.getValue().getPayload();
        assertTrue(payload.asString().contains("\"userId\":\"user-42\""));
    }

    @Test
    void persistReturnsEmptyWhenDuplicateEventIdDetected() {
        when(repository.save(any(StoredAuditEvent.class))).thenReturn(Mono.error(new DuplicateKeyException("duplicate")));

        UserLoggedInEvent event = UserLoggedInEvent.of("user-1", null, null);

        boolean hasResult = Boolean.TRUE.equals(service.persist(event).hasElement().block());

        assertFalse(hasResult);
    }

    @Test
    void persistMapsEntityUpdatedEventToEntityAggregate() {
        when(repository.save(any(StoredAuditEvent.class))).thenAnswer(EventPersistenceServiceTest::echoSavedEntity);

        EntityUpdatedEvent event = EntityUpdatedEvent.builder()
                .eventId("00000000-0000-0000-0000-000000000077")
                .eventType(EventType.ENTITY_UPDATED)
                .occurredAt(Instant.parse("2026-05-15T11:15:30Z"))
                .sourceService("command-service")
                .userId("auditor-1")
                .entityType("invoice")
                .entityId("inv-7")
                .changedFields(java.util.Map.of("status", "PAID"))
                .build();

        StoredAuditEvent saved = service.persist(event).block();

        assertNotNull(saved);
        assertEquals("entity:invoice:inv-7", saved.getAggregateId());
        assertEquals("auditor-1", saved.getUserId());
        assertEquals("ENTITY_UPDATED", saved.getEventType());
    }

    @Test
    void persistRejectsNullEventTypeWithClearError() {
        UserLoggedInEvent event = UserLoggedInEvent.builder()
                .eventId("00000000-0000-0000-0000-000000000099")
                .occurredAt(Instant.parse("2026-05-15T12:15:30Z"))
                .sourceService("command-service")
                .userId("user-99")
                .build();

        Mono<StoredAuditEvent> persisted = service.persist(event);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, persisted::block);

        assertEquals("AuditEvent.eventType must not be null", exception.getMessage());
    }

    private static Mono<StoredAuditEvent> echoSavedEntity(InvocationOnMock invocation) {
        return Mono.just(invocation.getArgument(0));
    }
}


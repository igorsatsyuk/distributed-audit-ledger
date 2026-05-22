package lt.satsyuk.distributed.audit.command.service;

import lt.satsyuk.distributed.audit.command.config.KafkaTopicsProperties;
import lt.satsyuk.distributed.audit.command.repository.InMemoryEventStorage;
import lt.satsyuk.distributed.audit.contracts.command.DataDeletedCommand;
import lt.satsyuk.distributed.audit.contracts.command.EntityCreatedCommand;
import lt.satsyuk.distributed.audit.contracts.command.EntityUpdatedCommand;
import lt.satsyuk.distributed.audit.contracts.command.UserProfileChangeCommand;
import lt.satsyuk.distributed.audit.contracts.dto.CommandResponse;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.DataDeletedEvent;
import lt.satsyuk.distributed.audit.event.EntityCreatedEvent;
import lt.satsyuk.distributed.audit.event.EntityUpdatedEvent;
import lt.satsyuk.distributed.audit.event.UserProfileChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdditionalCommandServiceTest {

    @Mock
    private KafkaTemplate<String, AuditEvent> kafkaTemplate;

    @Mock
    private KafkaTopicsProperties kafkaTopicsProperties;

    private InMemoryEventStorage inMemoryEventStorage;
    private AuditCommandPublisher auditCommandPublisher;
    private AdditionalCommandService additionalCommandService;

    @BeforeEach
    void setUp() {
        inMemoryEventStorage = new InMemoryEventStorage();
        auditCommandPublisher = new AuditCommandPublisher(kafkaTemplate, kafkaTopicsProperties, inMemoryEventStorage);
        additionalCommandService = new AdditionalCommandService(auditCommandPublisher);
    }

    @Test
    void handleUserProfileChangePublishesUserProfileChangedEvent() {
        when(kafkaTopicsProperties.getAuditEvents()).thenReturn("user.login.events");
        when(kafkaTemplate.send(eq("user.login.events"), anyString(), any(AuditEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        UserProfileChangeCommand command = UserProfileChangeCommand.builder()
                .userId("user-14")
                .changedFields(Map.of("email", "new@example.com"))
                .build();

        CommandResponse response = additionalCommandService.handleUserProfileChange(command).block();

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(1, inMemoryEventStorage.count());

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(kafkaTemplate).send(eq("user.login.events"), eq(response.getEventId()), eventCaptor.capture());
        assertInstanceOf(UserProfileChangedEvent.class, eventCaptor.getValue());
    }

    @Test
    void handleEntityCreatedPublishesEntityCreatedEvent() {
        when(kafkaTopicsProperties.getAuditEvents()).thenReturn("user.login.events");
        when(kafkaTemplate.send(eq("user.login.events"), anyString(), any(AuditEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        EntityCreatedCommand command = EntityCreatedCommand.builder()
                .userId("admin")
                .entityType("order")
                .entityId("ord-42")
                .entityData(Map.of("status", "NEW"))
                .build();

        CommandResponse response = additionalCommandService.handleEntityCreated(command).block();

        assertNotNull(response);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(kafkaTemplate).send(eq("user.login.events"), eq(response.getEventId()), eventCaptor.capture());
        assertInstanceOf(EntityCreatedEvent.class, eventCaptor.getValue());

        EntityCreatedEvent event = (EntityCreatedEvent) eventCaptor.getValue();
        assertEquals("order", event.getEntityType());
        assertEquals("ord-42", event.getEntityId());
        assertEquals("admin", event.getUserId());
    }

    @Test
    void handleEntityUpdatedPublishesEntityUpdatedEvent() {
        when(kafkaTopicsProperties.getAuditEvents()).thenReturn("user.login.events");
        when(kafkaTemplate.send(eq("user.login.events"), anyString(), any(AuditEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        EntityUpdatedCommand command = EntityUpdatedCommand.builder()
                .userId("auditor")
                .entityType("invoice")
                .entityId("inv-42")
                .changedFields(Map.of("status", "PAID"))
                .build();

        CommandResponse response = additionalCommandService.handleEntityUpdated(command).block();

        assertNotNull(response);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(kafkaTemplate).send(eq("user.login.events"), eq(response.getEventId()), eventCaptor.capture());
        assertInstanceOf(EntityUpdatedEvent.class, eventCaptor.getValue());

        EntityUpdatedEvent event = (EntityUpdatedEvent) eventCaptor.getValue();
        assertEquals("invoice", event.getEntityType());
        assertEquals("inv-42", event.getEntityId());
        assertEquals("auditor", event.getUserId());
    }

    @Test
    void handleDataDeletedPublishesDataDeletedEvent() {
        when(kafkaTopicsProperties.getAuditEvents()).thenReturn("user.login.events");
        when(kafkaTemplate.send(eq("user.login.events"), anyString(), any(AuditEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        DataDeletedCommand command = DataDeletedCommand.builder()
                .userId("auditor")
                .entityType("invoice")
                .entityId("inv-42")
                .reason("cleanup")
                .build();

        CommandResponse response = additionalCommandService.handleDataDeleted(command).block();

        assertNotNull(response);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(kafkaTemplate).send(eq("user.login.events"), eq(response.getEventId()), eventCaptor.capture());
        assertInstanceOf(DataDeletedEvent.class, eventCaptor.getValue());

        DataDeletedEvent event = (DataDeletedEvent) eventCaptor.getValue();
        assertEquals("invoice", event.getEntityType());
        assertEquals("inv-42", event.getEntityId());
        assertEquals("auditor", event.getUserId());
        assertEquals("cleanup", event.getReason());
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, AuditEvent> mockSendResult() {
        return (SendResult<String, AuditEvent>) Mockito.mock(SendResult.class);
    }
}

package lt.satsyuk.distributed.audit.command.service;

import lt.satsyuk.distributed.audit.command.config.KafkaTopicsProperties;
import lt.satsyuk.distributed.audit.command.repository.InMemoryEventStorage;
import lt.satsyuk.distributed.audit.contracts.command.UserLoginCommand;
import lt.satsyuk.distributed.audit.contracts.dto.CommandResponse;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLoginCommandServiceTest {

    @Mock
    private KafkaTemplate<String, AuditEvent> kafkaTemplate;

    @Mock
    private KafkaTopicsProperties kafkaTopicsProperties;

    private InMemoryEventStorage inMemoryEventStorage;
    private AuditCommandPublisher auditCommandPublisher;
    private UserLoginCommandService userLoginCommandService;

    @BeforeEach
    void setUp() {
        inMemoryEventStorage = new InMemoryEventStorage();
        auditCommandPublisher = new AuditCommandPublisher(kafkaTemplate, kafkaTopicsProperties, inMemoryEventStorage);
        userLoginCommandService = new UserLoginCommandService(auditCommandPublisher);
    }

    @Test
    void handleUserLoginPublishesEventAndStoresItInMemory() {
        when(kafkaTopicsProperties.getUserLoginEvents()).thenReturn("user.login.events");
        when(kafkaTemplate.send(eq("user.login.events"), anyString(), any(AuditEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        UserLoginCommand command = UserLoginCommand.builder().userId("user1").build();

        CommandResponse response = userLoginCommandService
                .handleUserLogin(command, "10.1.1.9", "JUnit")
                .block();

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getEventId());
        assertEquals(1, inMemoryEventStorage.count());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(kafkaTemplate).send(eq("user.login.events"), keyCaptor.capture(), eventCaptor.capture());

        assertEquals(response.getEventId(), keyCaptor.getValue());
        assertInstanceOf(UserLoggedInEvent.class, eventCaptor.getValue());

        AuditEvent storedEvent = inMemoryEventStorage.findAll().getFirst();
        assertInstanceOf(UserLoggedInEvent.class, storedEvent);

        UserLoggedInEvent userLoggedInEvent = (UserLoggedInEvent) storedEvent;
        assertEquals("user1", userLoggedInEvent.getUserId());
        assertEquals("10.1.1.9", userLoggedInEvent.getIpAddress());
        assertEquals("JUnit", userLoggedInEvent.getUserAgent());
    }

    @Test
    void handleUserLoginPropagatesPublishFailure() {
        when(kafkaTopicsProperties.getUserLoginEvents()).thenReturn("user.login.events");
        when(kafkaTemplate.send(anyString(), anyString(), any(AuditEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));

        UserLoginCommand command = UserLoginCommand.builder().userId("user1").build();

        CommandPublishException exception = assertThrows(
                CommandPublishException.class,
                () -> userLoginCommandService.handleUserLogin(command, null, null).block()
        );

        assertTrue(exception.getMessage().contains("Failed to publish event"));
        assertEquals(0, inMemoryEventStorage.count());
    }

    @Test
    void handleUserLoginMapsSynchronousSendException() {
        when(kafkaTopicsProperties.getUserLoginEvents()).thenReturn("user.login.events");
        when(kafkaTemplate.send(anyString(), anyString(), any(AuditEvent.class)))
                .thenThrow(new IllegalStateException("invalid producer state"));

        UserLoginCommand command = UserLoginCommand.builder().userId("user1").build();

        CommandPublishException exception = assertThrows(
                CommandPublishException.class,
                () -> userLoginCommandService.handleUserLogin(command, null, null).block()
        );

        assertTrue(exception.getMessage().contains("Failed to publish event"));
        assertEquals(0, inMemoryEventStorage.count());
    }

    @Test
    void handleUserLoginDoesNotPublishBeforeSubscription() {

        UserLoginCommand command = UserLoginCommand.builder().userId("user1").build();

        userLoginCommandService.handleUserLogin(command, null, null);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(AuditEvent.class));
    }

    @Test
    void handleUserLoginUsesCommandMetadataWhenRequestMetadataMissing() {
        when(kafkaTopicsProperties.getUserLoginEvents()).thenReturn("user.login.events");
        when(kafkaTemplate.send(eq("user.login.events"), anyString(), any(AuditEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        UserLoginCommand command = UserLoginCommand.builder()
                .userId("user1")
                .ipAddress("203.0.113.77")
                .userAgent("PostmanRuntime")
                .build();

        userLoginCommandService.handleUserLogin(command, null, null).block();

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(kafkaTemplate).send(eq("user.login.events"), anyString(), eventCaptor.capture());

        assertInstanceOf(UserLoggedInEvent.class, eventCaptor.getValue());
        UserLoggedInEvent event = (UserLoggedInEvent) eventCaptor.getValue();
        assertEquals("203.0.113.77", event.getIpAddress());
        assertEquals("PostmanRuntime", event.getUserAgent());
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, AuditEvent> mockSendResult() {
        return (SendResult<String, AuditEvent>) Mockito.mock(SendResult.class);
    }
}


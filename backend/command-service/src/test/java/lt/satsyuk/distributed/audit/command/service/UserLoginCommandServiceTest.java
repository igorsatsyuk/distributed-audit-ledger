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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLoginCommandServiceTest {

    @Mock
    private KafkaTemplate<String, AuditEvent> kafkaTemplate;

    @Mock
    private KafkaTopicsProperties kafkaTopicsProperties;

    private InMemoryEventStorage inMemoryEventStorage;
    private UserLoginCommandService userLoginCommandService;

    @BeforeEach
    void setUp() {
        inMemoryEventStorage = new InMemoryEventStorage();
        userLoginCommandService = new UserLoginCommandService(kafkaTemplate, kafkaTopicsProperties, inMemoryEventStorage);
        when(kafkaTopicsProperties.getUserLoginEvents()).thenReturn("user.login.events");
    }

    @Test
    void handleUserLoginPublishesEventAndStoresItInMemory() {
        when(kafkaTemplate.send(anyString(), anyString(), any(AuditEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(Mockito.mock(SendResult.class)));

        UserLoginCommand command = UserLoginCommand.builder().userId("user1").build();

        CommandResponse response = userLoginCommandService
                .handleUserLogin(command, "10.1.1.9", "JUnit")
                .block();

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getEventId());
        assertEquals(1, inMemoryEventStorage.count());

        AuditEvent storedEvent = inMemoryEventStorage.findAll().getFirst();
        assertInstanceOf(UserLoggedInEvent.class, storedEvent);

        UserLoggedInEvent userLoggedInEvent = (UserLoggedInEvent) storedEvent;
        assertEquals("user1", userLoggedInEvent.getUserId());
        assertEquals("10.1.1.9", userLoggedInEvent.getIpAddress());
        assertEquals("JUnit", userLoggedInEvent.getUserAgent());
    }

    @Test
    void handleUserLoginPropagatesPublishFailure() {
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
}


package lt.satsyuk.distributed.audit.command.api;

import lt.satsyuk.distributed.audit.command.service.AdditionalCommandService;
import lt.satsyuk.distributed.audit.command.service.UserLoginCommandService;
import lt.satsyuk.distributed.audit.contracts.command.UserProfileChangeCommand;
import lt.satsyuk.distributed.audit.contracts.command.UserLoginCommand;
import lt.satsyuk.distributed.audit.contracts.dto.CommandResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandControllerTest {

    @Test
    void userLoginReturnsAcceptedResponse() {
        UserLoginCommandService userLoginCommandService = mock(UserLoginCommandService.class);
        AdditionalCommandService additionalCommandService = mock(AdditionalCommandService.class);
        CommandController controller = new CommandController(userLoginCommandService, additionalCommandService);

        when(userLoginCommandService.handleUserLogin(any(UserLoginCommand.class), anyString(), anyString()))
                .thenReturn(Mono.just(CommandResponse.accepted("event-123")));

        UserLoginCommand command = UserLoginCommand.builder().userId("user1").build();
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/commands/user/login")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 5050))
                .build();

        ResponseEntity<CommandResponse> response = controller.userLogin(command, "JUnit", request).block();
        Assertions.assertNotNull(response);

        CommandResponse body = response.getBody();
        Assertions.assertNotNull(body);
        Assertions.assertEquals(202, response.getStatusCode().value());
        Assertions.assertTrue(body.isSuccess());
        Assertions.assertEquals("event-123", body.getEventId());

        ArgumentCaptor<UserLoginCommand> commandCaptor = ArgumentCaptor.forClass(UserLoginCommand.class);
        verify(userLoginCommandService).handleUserLogin(commandCaptor.capture(), eq("127.0.0.1"), eq("JUnit"));
        Assertions.assertEquals("user1", commandCaptor.getValue().getUserId());
    }

    @Test
    void userProfileChangeReturnsAcceptedResponse() {
        UserLoginCommandService userLoginCommandService = mock(UserLoginCommandService.class);
        AdditionalCommandService additionalCommandService = mock(AdditionalCommandService.class);
        CommandController controller = new CommandController(userLoginCommandService, additionalCommandService);

        when(additionalCommandService.handleUserProfileChange(any(UserProfileChangeCommand.class)))
                .thenReturn(Mono.just(CommandResponse.accepted("event-999")));

        ResponseEntity<CommandResponse> response = controller.userProfileChange(
                UserProfileChangeCommand.builder().userId("u1").changedFields(java.util.Map.of("name", "New")).build()).block();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(202, response.getStatusCode().value());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertEquals("event-999", response.getBody().getEventId());
    }
}


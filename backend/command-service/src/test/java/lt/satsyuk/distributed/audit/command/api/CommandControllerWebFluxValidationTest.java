package lt.satsyuk.distributed.audit.command.api;

import lt.satsyuk.distributed.audit.command.service.CommandPublishException;
import lt.satsyuk.distributed.audit.command.service.AdditionalCommandService;
import lt.satsyuk.distributed.audit.command.service.UserLoginCommandService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@WebFluxTest(controllers = CommandController.class)
@Import(GlobalExceptionHandler.class)
class CommandControllerWebFluxValidationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private UserLoginCommandService userLoginCommandService;

    @MockitoBean
    private AdditionalCommandService additionalCommandService;

    @Test
    void blankUserIdReturnsCommandResponseEnvelope() {
        webTestClient.post()
                .uri("/commands/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("userId must not be blank");

        verifyNoInteractions(userLoginCommandService);
    }

    @Test
    void malformedJsonReturnsCommandResponseEnvelope() {
        webTestClient.post()
                .uri("/commands/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").value(message -> {
                    Assertions.assertNotNull(message);
                    String msg = (String) message;
                    Assertions.assertTrue(msg.contains("Failed to read HTTP message") || msg.contains("JSON"),
                            "Expected message to contain HTTP read error, got: " + msg);
                });

        verifyNoInteractions(userLoginCommandService);
    }

    @Test
    void publishFailureReturnsServiceUnavailableEnvelope() {
        when(userLoginCommandService.handleUserLogin(any(), any(), any()))
                .thenReturn(Mono.error(new CommandPublishException("Failed to publish event to Kafka", null)));

        webTestClient.post()
                .uri("/commands/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":\"user1\"}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Failed to publish event to Kafka");
    }

    @Test
    void missingRequestBodyReturnsCommandResponseEnvelope() {
        webTestClient.post()
                .uri("/commands/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").value(message -> {
                    Assertions.assertNotNull(message);
                    String msg = (String) message;
                    Assertions.assertTrue(msg.toLowerCase().contains("request body"),
                            "Expected missing request body error, got: " + msg);
                });

        verifyNoInteractions(userLoginCommandService);
    }

    @Test
    void entityCreateValidationReturnsCommandResponseEnvelope() {
        webTestClient.post()
                .uri("/commands/entity/create")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":\"\",\"entityType\":\"\",\"entityId\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").value(message -> {
                    Assertions.assertNotNull(message);
                    String msg = (String) message;
                    Assertions.assertTrue(msg.contains("userId must not be blank"));
                    Assertions.assertTrue(msg.contains("entityType must not be blank"));
                    Assertions.assertTrue(msg.contains("entityId must not be blank"));
                    Assertions.assertTrue(msg.contains("entityData must not be null"));
                });

        verifyNoInteractions(additionalCommandService);
    }

    @Test
    void entityUpdateMissingBodyReturnsCommandResponseEnvelope() {
        webTestClient.post()
                .uri("/commands/entity/update")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").value(message -> {
                    Assertions.assertNotNull(message);
                    String msg = (String) message;
                    Assertions.assertTrue(msg.toLowerCase().contains("request body"),
                            "Expected missing request body error, got: " + msg);
                });

        verifyNoInteractions(additionalCommandService);
    }

    @Test
    void dataDeleteValidationReturnsCommandResponseEnvelope() {
        webTestClient.post()
                .uri("/commands/data/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":\"\",\"entityType\":\"\",\"entityId\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").value(message -> {
                    Assertions.assertNotNull(message);
                    String msg = (String) message;
                    Assertions.assertTrue(msg.contains("userId must not be blank"));
                    Assertions.assertTrue(msg.contains("entityType must not be blank"));
                    Assertions.assertTrue(msg.contains("entityId must not be blank"));
                });

        verifyNoInteractions(additionalCommandService);
    }

    @Test
    void additionalEndpointPublishFailureReturnsServiceUnavailableEnvelope() {
        when(additionalCommandService.handleUserProfileChange(any()))
                .thenReturn(Mono.error(new CommandPublishException("Failed to publish event to Kafka", null)));

        webTestClient.post()
                .uri("/commands/user/profile-change")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":\"user1\",\"changedFields\":{\"email\":\"new@example.com\"}}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Failed to publish event to Kafka");
    }
}


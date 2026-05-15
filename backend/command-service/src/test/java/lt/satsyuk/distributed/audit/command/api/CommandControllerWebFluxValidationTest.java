package lt.satsyuk.distributed.audit.command.api;

import lt.satsyuk.distributed.audit.command.service.UserLoginCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.verifyNoInteractions;

@WebFluxTest(controllers = CommandController.class)
@Import(GlobalExceptionHandler.class)
class CommandControllerWebFluxValidationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private UserLoginCommandService userLoginCommandService;

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
                .jsonPath("$.message").value(message -> ((String) message).contains("Invalid request payload"));

        verifyNoInteractions(userLoginCommandService);
    }
}


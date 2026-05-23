package lt.satsyuk.distributed.audit.command.api;

import lt.satsyuk.distributed.audit.command.service.AuthenticationService;
import lt.satsyuk.distributed.audit.command.service.InvalidCredentialsException;
import lt.satsyuk.distributed.audit.contracts.auth.AuthTokenResponse;
import lt.satsyuk.distributed.audit.contracts.auth.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerWebFluxTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AuthenticationService authenticationService;

    @Test
    void loginReturnsBearerTokenEnvelope() {
        when(authenticationService.login(any())).thenReturn(Mono.just(AuthTokenResponse.builder()
                .accessToken("jwt-token")
                .tokenType("Bearer")
                .expiresAt(Instant.parse("2026-05-23T12:00:00Z"))
                .username("auditor")
                .roles(Set.of(UserRole.AUDITOR))
                .build()));

        webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"auditor\",\"password\":\"auditor123!\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("jwt-token")
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.username").isEqualTo("auditor")
                .jsonPath("$.roles[0]").isEqualTo("AUDITOR");
    }

    @Test
    void loginReturnsUnauthorizedForInvalidCredentials() {
        when(authenticationService.login(any())).thenReturn(Mono.error(new InvalidCredentialsException("Invalid username or password")));

        webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"auditor\",\"password\":\"wrong\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("INVALID_CREDENTIALS")
                .jsonPath("$.message").isEqualTo("Invalid username or password");
    }

    @Test
    void loginValidationReturnsBadRequest() {
        webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"\",\"password\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").value(message -> {
                    String value = String.valueOf(message);
                    org.junit.jupiter.api.Assertions.assertTrue(value.contains("username must not be blank"));
                    org.junit.jupiter.api.Assertions.assertTrue(value.contains("password must not be blank"));
                });
    }
}


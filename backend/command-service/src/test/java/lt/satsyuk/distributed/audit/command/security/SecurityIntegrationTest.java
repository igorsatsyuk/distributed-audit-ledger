package lt.satsyuk.distributed.audit.command.security;

import lt.satsyuk.distributed.audit.command.service.AdditionalCommandService;
import lt.satsyuk.distributed.audit.command.service.UserLoginCommandService;
import lt.satsyuk.distributed.audit.contracts.dto.CommandResponse;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import com.jayway.jsonpath.JsonPath;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest {

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @MockitoBean
    private UserLoginCommandService userLoginCommandService;

    @MockitoBean
    private AdditionalCommandService additionalCommandService;

    @MockitoBean
    private KafkaTemplate<String, AuditEvent> kafkaTemplate;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void securedCommandEndpointRejectsAnonymousRequests() {
        webTestClient.post()
                .uri("/commands/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":\"user1\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Authentication is required");
    }

    @Test
    void securedCommandEndpointRejectsAuditorRole() {
        String token = loginAndExtractToken("auditor", "auditor123!");

        webTestClient.post()
                .uri("/commands/user/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":\"user1\"}")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Access denied");
    }

    @Test
    void securedCommandEndpointAcceptsUserRole() {
        when(userLoginCommandService.handleUserLogin(any(), any(), anyString()))
                .thenReturn(Mono.just(CommandResponse.accepted("evt-auth-1")));

        String token = loginAndExtractToken("user", "user123!");

        webTestClient.post()
                .uri("/commands/user/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":\"user1\"}")
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.eventId").isEqualTo("evt-auth-1");
    }

    @Test
    void loginEndpointReturnsJwtForConfiguredUser() {
        webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"admin\",\"password\":\"admin123!\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .jsonPath("$.roles.length()").isEqualTo(3)
                .jsonPath("$.roles[0]").exists();
    }

    private String loginAndExtractToken(String username, String password) {
        byte[] responseBody = webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBodyContent();
        String response = responseBody == null ? "" : new String(responseBody, StandardCharsets.UTF_8);
        return JsonPath.read(response, "$.accessToken");
    }
}


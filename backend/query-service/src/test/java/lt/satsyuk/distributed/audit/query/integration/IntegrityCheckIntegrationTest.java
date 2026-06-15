package lt.satsyuk.distributed.audit.query.integration;

import lt.satsyuk.distributed.audit.contracts.auth.JwtService;
import lt.satsyuk.distributed.audit.contracts.auth.UserRole;
import lt.satsyuk.distributed.audit.query.api.AuditIntegrityCheckResponse;
import lt.satsyuk.distributed.audit.query.api.BlockchainIntegrityException;
import lt.satsyuk.distributed.audit.query.blockchain.AuditLedgerBlockchainClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.web3j.protocol.Web3j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the integrity-check endpoint (#9.4).
 *
 * <p>Uses a real PostgreSQL container (Testcontainers) and mocks the blockchain client to
 * verify the full HTTP → service → repository → DB path, as well as correct HTTP status
 * codes for the various blockchain response/error scenarios.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Testcontainers
class IntegrityCheckIntegrationTest {

    /** Valid 64-char hex hash used across multiple test cases. */
    private static final String HASH_64 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant FIXED_ISSUED_AT = Instant.parse("2099-05-15T10:15:30Z");
    private final JwtService jwtService;

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withInitScript("db/schema.sql");

    @DynamicPropertySource
    static void r2dbcProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + POSTGRES.getHost() + ":"
                        + POSTGRES.getFirstMappedPort() + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    /** Mock Web3j to prevent background scheduler threads from attempting
     * real connections to localhost:8545 during tests.
     */
    @MockitoBean
    Web3j web3j;

    /** Real subject of integration testing — mocked only for blockchain I/O. */
    @MockitoBean
    AuditLedgerBlockchainClient blockchainClient;

    private final DatabaseClient databaseClient;

    @LocalServerPort
    int port;

    WebTestClient webTestClient;

    IntegrityCheckIntegrationTest(JwtService jwtService, DatabaseClient databaseClient) {
        this.jwtService = jwtService;
        this.databaseClient = databaseClient;
    }

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.AUDITOR))
                .responseTimeout(Duration.ofSeconds(15))
                .build();
        databaseClient.sql("DELETE FROM audit.events")
                .fetch().rowsUpdated().block(Duration.ofSeconds(5));
    }

    @Test
    void queryEndpointsReturn401WithoutToken() {
        WebTestClient anonymousClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(15))
                .build();

        anonymousClient.get()
                .uri("/api/audit-logs")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Authentication is required");
    }

    @Test
    void queryEndpointsReturn403ForUserRoleWithoutAuditorAccess() {
        WebTestClient userClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, bearerToken(UserRole.USER))
                .responseTimeout(Duration.ofSeconds(15))
                .build();

        userClient.get()
                .uri("/api/audit-logs")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Access denied");
    }

    // ── Integrity-check endpoint ──────────────────────────────────────────────

    @Test
    void integrityCheck_returnsOnChain_whenHashAnchoredInBlockchain() {
        Long id = insertEvent("evt-ic-001", "USER_LOGGED_IN", "user-1", HASH_64);
        AuditIntegrityCheckResponse.BlockchainRecord bcRecord =
                new AuditIntegrityCheckResponse.BlockchainRecord(true, "0xdeadbeef", 100L, 1715774400L);
        when(blockchainClient.inspectEventHash(HASH_64)).thenReturn(Mono.just(bcRecord));

        webTestClient.get()
                .uri("/api/audit-logs/{id}/integrity-check", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.auditLogId").isEqualTo(id.intValue())
                .jsonPath("$.eventHash").isEqualTo(HASH_64)
                .jsonPath("$.status").isEqualTo("ON_CHAIN")
                .jsonPath("$.blockchainRecord.exists").isEqualTo(true)
                .jsonPath("$.blockchainRecord.transactionHash").isEqualTo("0xdeadbeef")
                .jsonPath("$.blockchainRecord.blockNumber").isEqualTo(100);
    }

    @Test
    void integrityCheck_returnsMismatch_whenHashNotFoundOnChain() {
        Long id = insertEvent("evt-ic-002", "USER_LOGGED_OUT", "user-2", HASH_64);
        when(blockchainClient.inspectEventHash(HASH_64)).thenReturn(
                Mono.just(new AuditIntegrityCheckResponse.BlockchainRecord(false, null, null, null)));

        webTestClient.get()
                .uri("/api/audit-logs/{id}/integrity-check", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.auditLogId").isEqualTo(id.intValue())
                .jsonPath("$.status").isEqualTo("MISMATCH")
                .jsonPath("$.blockchainRecord.exists").isEqualTo(false);
    }

    @Test
    void integrityCheck_returnsPending_whenEventHashAbsentInDb() {
        Long id = insertEvent("evt-ic-003", "USER_LOGGED_IN", "user-3", null);

        webTestClient.get()
                .uri("/api/audit-logs/{id}/integrity-check", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.auditLogId").isEqualTo(id.intValue())
                .jsonPath("$.eventHash").doesNotExist()
                .jsonPath("$.status").isEqualTo("PENDING")
                .jsonPath("$.blockchainRecord.exists").isEqualTo(false);
    }

    @Test
    void integrityCheck_returns404_whenRecordNotInDatabase() {
        webTestClient.get()
                .uri("/api/audit-logs/999999/integrity-check")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").value(message -> org.hamcrest.MatcherAssert.assertThat(
                        String.valueOf(message), containsString("999999")));
    }

    @Test
    void integrityCheck_returns503_whenBlockchainRpcFails() {
        Long id = insertEvent("evt-ic-004", "USER_LOGGED_IN", "user-4", HASH_64);
        when(blockchainClient.inspectEventHash(HASH_64)).thenReturn(Mono.error(
                new BlockchainIntegrityException("Blockchain RPC timeout",
                        BlockchainIntegrityException.ErrorType.RPC_FAILURE)));

        webTestClient.get()
                .uri("/api/audit-logs/{id}/integrity-check", id)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Blockchain RPC timeout");
    }

    @Test
    void integrityCheck_returns500_whenBlockchainConfigurationInvalid() {
        Long id = insertEvent("evt-ic-005", "USER_LOGGED_IN", "user-5", HASH_64);
        when(blockchainClient.inspectEventHash(HASH_64)).thenReturn(Mono.error(
                new BlockchainIntegrityException("web3j.contract-address is malformed",
                        BlockchainIntegrityException.ErrorType.CONFIGURATION)));

        webTestClient.get()
                .uri("/api/audit-logs/{id}/integrity-check", id)
                .exchange()
                .expectStatus().isEqualTo(500)
                .expectBody()
                .jsonPath("$.message").isEqualTo("web3j.contract-address is malformed");
    }

    // ── Audit-log query endpoints (full-stack smoke) ──────────────────────────

    @Test
    void getAuditLogs_returnsAllInsertedEvents() {
        insertEvent("evt-q-001", "USER_LOGGED_IN", "user-a", null);
        insertEvent("evt-q-002", "USER_LOGGED_OUT", "user-b", null);

        webTestClient.get()
                .uri("/api/audit-logs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);
    }

    @Test
    void getAuditLogs_filterByUserId_returnsOnlyMatchingEvents() {
        insertEvent("evt-q-003", "USER_LOGGED_IN", "target-user", null);
        insertEvent("evt-q-004", "USER_LOGGED_IN", "other-user", null);

        webTestClient.get()
                .uri("/api/audit-logs?userId=target-user")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].userId").isEqualTo("target-user");
    }

    @Test
    void getAuditLogs_filterByEventType_returnsOnlyMatchingEvents() {
        insertEvent("evt-q-005", "USER_LOGGED_IN", "user-x", null);
        insertEvent("evt-q-006", "USER_LOGGED_OUT", "user-x", null);

        webTestClient.get()
                .uri("/api/audit-logs?eventType=USER_LOGGED_IN")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].eventType").isEqualTo("USER_LOGGED_IN");
    }

    @Test
    void getAuditLogById_returnsEventDetails() {
        Long id = insertEvent("evt-q-007", "USER_LOGGED_IN", "user-y", null);

        webTestClient.get()
                .uri("/api/audit-logs/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.intValue())
                .jsonPath("$.eventId").isEqualTo("evt-q-007")
                .jsonPath("$.userId").isEqualTo("user-y")
                .jsonPath("$.eventType").isEqualTo("USER_LOGGED_IN");
    }

    @Test
    void getAuditLogById_returns404_whenRecordMissing() {
        webTestClient.get()
                .uri("/api/audit-logs/999998")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").value(message -> org.hamcrest.MatcherAssert.assertThat(
                        String.valueOf(message), containsString("999998")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Inserts a row into {@code audit.events} and returns the generated PK.
     */
    private Long insertEvent(String eventId, String eventType, String userId, String eventHash) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO audit.events (event_id, aggregate_id, event_type, user_id, payload, event_hash)
                        VALUES (:eventId, :aggregateId, :eventType, :userId, CAST(:payload AS JSONB), :eventHash)
                        RETURNING id
                        """)
                .bind("eventId", eventId)
                .bind("aggregateId", "agg-" + UUID.randomUUID())
                .bind("eventType", eventType)
                .bind("userId", userId)
                .bind("payload", "{}");

        if (eventHash != null) {
            spec = spec.bind("eventHash", eventHash);
        } else {
            spec = spec.bindNull("eventHash", String.class);
        }

        return spec.fetch()
                .one()
                .map(row -> ((Number) row.get("id")).longValue())
                .block(Duration.ofSeconds(5));
    }

    private String bearerToken(UserRole... roles) {
        return "Bearer " + jwtService.generateToken(
                "integration-user",
                Set.of(roles),
                FIXED_ISSUED_AT
        );
    }
}


package lt.satsyuk.distributed.audit.query.api;

import lt.satsyuk.distributed.audit.contracts.dto.AuditEventDto;
import lt.satsyuk.distributed.audit.event.EventType;
import lt.satsyuk.distributed.audit.query.service.AuditIntegrityCheckService;
import lt.satsyuk.distributed.audit.query.service.AuditLogNotFoundException;
import lt.satsyuk.distributed.audit.query.service.AuditLogQueryService;
import lt.satsyuk.distributed.audit.query.service.QueryValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerWebFluxTest {

    @Mock
    private AuditLogQueryService auditLogQueryService;

    @Mock
    private AuditIntegrityCheckService auditIntegrityCheckService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        AuditLogController controller = new AuditLogController(auditLogQueryService, auditIntegrityCheckService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAuditLogsReturnsFilteredList() {
        AuditEventDto dto = AuditEventDto.builder()
                .id(1L)
                .eventId("evt-1")
                .eventType(EventType.USER_LOGGED_IN)
                .userId("user-1")
                .occurredAt(Instant.parse("2026-05-15T12:00:00Z"))
                .integrityStatus("PENDING")
                .build();

        when(auditLogQueryService.findAuditLogs(
                "user-1",
                EventType.USER_LOGGED_IN,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-31T23:59:59Z"),
                "10.0.0.5",
                50,
                10L
        )).thenReturn(Flux.just(dto));

        webTestClient.get()
                .uri("/api/audit-logs?userId=user-1&eventType=USER_LOGGED_IN&from=2026-05-01T00:00:00Z&to=2026-05-31T23:59:59Z&search=10.0.0.5&limit=50&offset=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].eventId").isEqualTo("evt-1")
                .jsonPath("$[0].userId").isEqualTo("user-1")
                .jsonPath("$[0].eventType").isEqualTo("USER_LOGGED_IN");
    }

    @Test
    void getAuditLogByIdReturnsNotFoundWhenMissing() {
        when(auditLogQueryService.findById(99L)).thenReturn(Mono.error(new AuditLogNotFoundException(99L)));

        webTestClient.get()
                .uri("/api/audit-logs/99")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Audit log with id=99 was not found");
    }

    @Test
    void invalidDateRangeReturnsBadRequest() {
        when(auditLogQueryService.findAuditLogs(
                null,
                null,
                Instant.parse("2026-05-20T00:00:00Z"),
                Instant.parse("2026-05-10T00:00:00Z"),
                null,
                null,
                null
        )).thenReturn(Flux.error(new QueryValidationException("Query parameter 'from' must be before or equal to 'to'")));

        webTestClient.get()
                .uri("/api/audit-logs?from=2026-05-20T00:00:00Z&to=2026-05-10T00:00:00Z")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Query parameter 'from' must be before or equal to 'to'");
    }

    @Test
    void integrityCheckReturnsBlockchainStatus() {
        AuditIntegrityCheckResponse.BlockchainRecord blockchainRecord =
                new AuditIntegrityCheckResponse.BlockchainRecord(true, "0xabc", 12345L, 1715774400L);
        AuditIntegrityCheckResponse response = new AuditIntegrityCheckResponse(
                1L,
                "evt-1",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                blockchainRecord,
                "ON_CHAIN"
        );

        when(auditIntegrityCheckService.checkIntegrity(1L)).thenReturn(Mono.just(response));

        webTestClient.get()
                .uri("/api/audit-logs/1/integrity-check")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.auditLogId").isEqualTo(1)
                .jsonPath("$.eventId").isEqualTo("evt-1")
                .jsonPath("$.eventHash").isEqualTo("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .jsonPath("$.blockchainRecord.exists").isEqualTo(true)
                .jsonPath("$.blockchainRecord.transactionHash").isEqualTo("0xabc")
                .jsonPath("$.status").isEqualTo("ON_CHAIN");
    }

    @Test
    void integrityCheckReturnsNotFoundWhenMissing() {
        when(auditIntegrityCheckService.checkIntegrity(404L)).thenReturn(Mono.error(new AuditLogNotFoundException(404L)));

        webTestClient.get()
                .uri("/api/audit-logs/404/integrity-check")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Audit log with id=404 was not found");
    }

    @Test
    void integrityCheckReturnsServiceUnavailableWhenBlockchainFails() {
        when(auditIntegrityCheckService.checkIntegrity(1L)).thenReturn(Mono.error(
                new BlockchainIntegrityException("Blockchain RPC is unavailable",
                        BlockchainIntegrityException.ErrorType.RPC_FAILURE)));

        webTestClient.get()
                .uri("/api/audit-logs/1/integrity-check")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Blockchain RPC is unavailable");
    }

    @Test
    void integrityCheckReturnsInternalServerErrorWhenConfigurationFails() {
        when(auditIntegrityCheckService.checkIntegrity(1L)).thenReturn(Mono.error(
                new BlockchainIntegrityException("web3j.contract-address is malformed",
                        BlockchainIntegrityException.ErrorType.CONFIGURATION)));

        webTestClient.get()
                .uri("/api/audit-logs/1/integrity-check")
                .exchange()
                .expectStatus().isEqualTo(500)
                .expectBody()
                .jsonPath("$.message").isEqualTo("web3j.contract-address is malformed");
    }
}

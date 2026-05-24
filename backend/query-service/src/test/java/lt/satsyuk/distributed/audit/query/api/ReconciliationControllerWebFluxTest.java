package lt.satsyuk.distributed.audit.query.api;

import lt.satsyuk.distributed.audit.query.service.ReconciliationAlreadyRunningException;
import lt.satsyuk.distributed.audit.query.service.ReconciliationReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationControllerWebFluxTest {

    @Mock
    private ReconciliationReportService reconciliationReportService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        ReconciliationController controller = new ReconciliationController(reconciliationReportService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void runReconciliationReturnsReport() {
        ReconciliationReportResponse report = sampleReport("MANUAL");
        when(reconciliationReportService.runManual()).thenReturn(Mono.just(report));

        webTestClient.post()
                .uri("/api/reconciliation/run")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.trigger").isEqualTo("MANUAL")
                .jsonPath("$.checkedEvents").isEqualTo(3)
                .jsonPath("$.mismatchEvents").isEqualTo(1);
    }

    @Test
    void runReconciliationReturnsConflictWhenAlreadyRunning() {
        when(reconciliationReportService.runManual()).thenReturn(Mono.error(new ReconciliationAlreadyRunningException()));

        webTestClient.post()
                .uri("/api/reconciliation/run")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Reconciliation run is already in progress");
    }

    @Test
    void latestReturnsNoContentWhenNoReportYet() {
        when(reconciliationReportService.latestReport()).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/reconciliation/latest")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void latestReturnsReportWhenExists() {
        ReconciliationReportResponse report = sampleReport("SCHEDULED");
        when(reconciliationReportService.latestReport()).thenReturn(Mono.just(report));

        webTestClient.get()
                .uri("/api/reconciliation/latest")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.trigger").isEqualTo("SCHEDULED")
                .jsonPath("$.mismatches[0].reason").isEqualTo("HASH_NOT_FOUND_ON_CHAIN");
    }

    private ReconciliationReportResponse sampleReport(String trigger) {
        return new ReconciliationReportResponse(
                trigger,
                Instant.parse("2026-05-24T10:00:00Z"),
                Instant.parse("2026-05-24T10:00:01Z"),
                3,
                1,
                1,
                1,
                List.of(new ReconciliationReportResponse.MismatchItem(
                        11L,
                        "evt-11",
                        "hash",
                        "MISMATCH",
                        "HASH_NOT_FOUND_ON_CHAIN"
                ))
        );
    }
}


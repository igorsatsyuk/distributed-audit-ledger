package lt.satsyuk.distributed.audit.query.api;

import lt.satsyuk.distributed.audit.query.service.ReconciliationReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    private final ReconciliationReportService reconciliationReportService;

    public ReconciliationController(ReconciliationReportService reconciliationReportService) {
        this.reconciliationReportService = reconciliationReportService;
    }

    @PostMapping("/run")
    public Mono<ReconciliationReportResponse> runReconciliation() {
        return reconciliationReportService.runManual();
    }

    @GetMapping("/latest")
    public Mono<ResponseEntity<ReconciliationReportResponse>> getLatestReport() {
        return reconciliationReportService.latestReport()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.noContent().build());
    }
}


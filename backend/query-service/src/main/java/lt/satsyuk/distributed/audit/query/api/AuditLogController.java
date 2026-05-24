package lt.satsyuk.distributed.audit.query.api;

import lt.satsyuk.distributed.audit.contracts.dto.AuditEventDto;
import lt.satsyuk.distributed.audit.event.EventType;
import lt.satsyuk.distributed.audit.query.service.AuditIntegrityCheckService;
import lt.satsyuk.distributed.audit.query.service.AuditLogQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;
    private final AuditIntegrityCheckService auditIntegrityCheckService;

    public AuditLogController(AuditLogQueryService auditLogQueryService,
                              AuditIntegrityCheckService auditIntegrityCheckService) {
        this.auditLogQueryService = auditLogQueryService;
        this.auditIntegrityCheckService = auditIntegrityCheckService;
    }

    @GetMapping
    public Flux<AuditEventDto> getAuditLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long offset
    ) {
        return auditLogQueryService.findAuditLogs(userId, eventType, from, to, search, limit, offset);
    }

    @GetMapping("/{id}")
    public Mono<AuditEventDto> getAuditLogById(@PathVariable Long id) {
        return auditLogQueryService.findById(id);
    }

    @GetMapping("/{id}/integrity-check")
    public Mono<AuditIntegrityCheckResponse> checkIntegrity(@PathVariable Long id) {
        return auditIntegrityCheckService.checkIntegrity(id);
    }
}

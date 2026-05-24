package lt.satsyuk.distributed.audit.query.service;

public record ReconciliationMismatch(
        Long auditLogId,
        String eventId,
        String eventHash,
        String status,
        String reason
) {
}


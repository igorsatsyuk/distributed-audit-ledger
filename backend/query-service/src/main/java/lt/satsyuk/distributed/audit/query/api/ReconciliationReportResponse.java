package lt.satsyuk.distributed.audit.query.api;

import java.time.Instant;
import java.util.List;

public record ReconciliationReportResponse(
        String trigger,
        Instant startedAt,
        Instant finishedAt,
        long checkedEvents,
        long onChainEvents,
        long pendingEvents,
        long mismatchEvents,
        List<MismatchItem> mismatches
) {

    public record MismatchItem(
            Long auditLogId,
            String eventId,
            String eventHash,
            String status,
            String reason
    ) {
    }
}


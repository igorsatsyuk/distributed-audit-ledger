package lt.satsyuk.distributed.audit.query.service;

import java.util.List;

public record BatchIntegrityCheckResult(
        long checkedEvents,
        long onChainEvents,
        long pendingEvents,
        long mismatchEvents,
        List<ReconciliationMismatch> mismatches
) {
}


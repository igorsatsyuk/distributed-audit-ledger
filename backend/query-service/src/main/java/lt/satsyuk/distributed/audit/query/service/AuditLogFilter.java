package lt.satsyuk.distributed.audit.query.service;

import lt.satsyuk.distributed.audit.event.EventType;

import java.time.Instant;

public record AuditLogFilter(
        String userId,
        EventType eventType,
        Instant from,
        Instant to
) {
}


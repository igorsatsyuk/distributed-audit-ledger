package lt.satsyuk.distributed.audit.query.service;

import lt.satsyuk.distributed.audit.contracts.dto.AuditEventDto;
import lt.satsyuk.distributed.audit.event.EventType;
import lt.satsyuk.distributed.audit.query.mapper.AuditEventDtoMapper;
import lt.satsyuk.distributed.audit.query.repository.AuditLogQueryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class AuditLogQueryService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final long DEFAULT_OFFSET = 0L;

    private final AuditLogQueryRepository auditLogQueryRepository;
    private final AuditEventDtoMapper mapper;

    public AuditLogQueryService(
            AuditLogQueryRepository auditLogQueryRepository,
            AuditEventDtoMapper mapper
    ) {
        this.auditLogQueryRepository = auditLogQueryRepository;
        this.mapper = mapper;
    }

    public Flux<AuditEventDto> findAuditLogs(
            String userId,
            EventType eventType,
            Instant from,
            Instant to,
            Integer limit,
            Long offset
    ) {
        validateRange(from, to);

        int resolvedLimit = resolveLimit(limit);
        long resolvedOffset = resolveOffset(offset);

        AuditLogFilter filter = new AuditLogFilter(userId, eventType, from, to, resolvedLimit, resolvedOffset);
        return auditLogQueryRepository.findByFilter(filter)
                .map(mapper::toDto);
    }

    public Mono<AuditEventDto> findById(Long id) {
        return auditLogQueryRepository.findById(id)
                .map(mapper::toDto)
                .switchIfEmpty(Mono.error(new AuditLogNotFoundException(id)));
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Query parameter 'from' must be before or equal to 'to'");
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Query parameter 'limit' must be greater than 0");
        }
        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Query parameter 'limit' must be less than or equal to " + MAX_LIMIT);
        }
        return limit;
    }

    private long resolveOffset(Long offset) {
        if (offset == null) {
            return DEFAULT_OFFSET;
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Query parameter 'offset' must be greater than or equal to 0");
        }
        return offset;
    }
}

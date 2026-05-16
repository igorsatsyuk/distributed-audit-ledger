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

    private final AuditLogQueryRepository auditLogQueryRepository;
    private final AuditEventDtoMapper mapper;

    public AuditLogQueryService(
            AuditLogQueryRepository auditLogQueryRepository,
            AuditEventDtoMapper mapper
    ) {
        this.auditLogQueryRepository = auditLogQueryRepository;
        this.mapper = mapper;
    }

    public Flux<AuditEventDto> findAuditLogs(String userId, EventType eventType, Instant from, Instant to) {
        validateRange(from, to);
        AuditLogFilter filter = new AuditLogFilter(userId, eventType, from, to);
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
}

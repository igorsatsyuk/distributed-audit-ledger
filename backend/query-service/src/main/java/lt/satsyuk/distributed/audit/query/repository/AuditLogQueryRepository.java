package lt.satsyuk.distributed.audit.query.repository;

import lt.satsyuk.distributed.audit.query.model.AuditEventRecord;
import lt.satsyuk.distributed.audit.query.service.AuditLogFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuditLogQueryRepository {

    Flux<AuditEventRecord> findByFilter(AuditLogFilter filter);

    Mono<AuditEventRecord> findById(Long id);
}

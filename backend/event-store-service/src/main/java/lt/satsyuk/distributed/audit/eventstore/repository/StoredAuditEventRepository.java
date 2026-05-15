package lt.satsyuk.distributed.audit.eventstore.repository;

import lt.satsyuk.distributed.audit.eventstore.model.StoredAuditEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface StoredAuditEventRepository extends ReactiveCrudRepository<StoredAuditEvent, Long> {
}


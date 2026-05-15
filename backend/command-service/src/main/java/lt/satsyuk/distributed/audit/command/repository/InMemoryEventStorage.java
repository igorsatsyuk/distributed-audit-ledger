package lt.satsyuk.distributed.audit.command.repository;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Temporary in-memory storage for accepted events (MVP stage for issue #5).
 */
@Component
public class InMemoryEventStorage {

    private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

    public void save(AuditEvent event) {
        events.add(event);
    }

    public List<AuditEvent> findAll() {
        return List.copyOf(events);
    }

    public int count() {
        return events.size();
    }
}


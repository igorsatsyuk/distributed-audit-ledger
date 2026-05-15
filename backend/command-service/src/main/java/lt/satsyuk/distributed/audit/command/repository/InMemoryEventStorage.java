package lt.satsyuk.distributed.audit.command.repository;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Temporary in-memory storage for accepted events (MVP stage for issue #5).
 */
@Component
public class InMemoryEventStorage {

    private final ConcurrentLinkedQueue<AuditEvent> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger eventsCount = new AtomicInteger(0);

    public void save(AuditEvent event) {
        events.add(event);
        eventsCount.incrementAndGet();
    }

    public List<AuditEvent> findAll() {
        return List.copyOf(new ArrayList<>(events));
    }

    public int count() {
        return eventsCount.get();
    }
}


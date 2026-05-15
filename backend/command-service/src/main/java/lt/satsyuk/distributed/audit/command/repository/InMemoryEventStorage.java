package lt.satsyuk.distributed.audit.command.repository;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Temporary in-memory storage for accepted events (MVP stage for issue #5).
 * Bounded to 10,000 events to prevent unbounded heap growth.
 */
@Component
public class InMemoryEventStorage {

    private static final int MAX_EVENTS = 10_000;

    private final ConcurrentLinkedQueue<AuditEvent> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger eventsCount = new AtomicInteger(0);

    public void save(AuditEvent event) {
        if (eventsCount.get() < MAX_EVENTS) {
            events.add(event);
            eventsCount.incrementAndGet();
        }
        // Silently drop events beyond capacity for MVP (upgrade path: use eviction policy)
    }

    public List<AuditEvent> findAll() {
        return List.copyOf(new ArrayList<>(events));
    }

    public int count() {
        return eventsCount.get();
    }
}


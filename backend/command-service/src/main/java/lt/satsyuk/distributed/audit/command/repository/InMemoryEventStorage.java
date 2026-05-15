package lt.satsyuk.distributed.audit.command.repository;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Temporary in-memory storage for accepted events (MVP stage for issue #5).
 * Bounded to 10,000 events to prevent unbounded heap growth.
 */
@Component
public class InMemoryEventStorage {

    private static final int MAX_EVENTS = 10_000;

    private final ArrayDeque<AuditEvent> events = new ArrayDeque<>();

    public synchronized void save(AuditEvent event) {
        if (events.size() >= MAX_EVENTS) {
            // Keep latest accepted events and evict oldest records once capacity is reached.
            events.pollFirst();
        }
        events.addLast(event);
    }

    public synchronized List<AuditEvent> findAll() {
        return List.copyOf(new ArrayList<>(events));
    }

    public synchronized int count() {
        return events.size();
    }
}


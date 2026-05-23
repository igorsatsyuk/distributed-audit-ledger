package lt.satsyuk.distributed.audit.command.repository;

import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventStorageTest {

    @Test
    void saveEvictsOldestWhenCapacityIsReached() {
        InMemoryEventStorage storage = new InMemoryEventStorage();

        for (int i = 0; i < 10_001; i++) {
            storage.save(UserLoggedInEvent.of("user-" + i, null, null));
        }

        List<AuditEvent> events = storage.findAll();

        assertThat(storage.count()).isEqualTo(10_000);
        assertThat(events).hasSize(10_000);
        assertThat(((UserLoggedInEvent) events.getFirst()).getUserId()).isEqualTo("user-1");
        assertThat(((UserLoggedInEvent) events.getLast()).getUserId()).isEqualTo("user-10000");
    }

    @Test
    void findAllReturnsImmutableSnapshot() {
        InMemoryEventStorage storage = new InMemoryEventStorage();
        storage.save(UserLoggedInEvent.of("user-1", "127.0.0.1", "ua"));

        List<AuditEvent> snapshot = storage.findAll();

        assertThat(snapshot).hasSize(1).isUnmodifiable();
    }
}

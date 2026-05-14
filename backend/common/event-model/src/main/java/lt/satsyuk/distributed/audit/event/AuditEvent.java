package lt.satsyuk.distributed.audit.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all domain events in the Distributed Audit Ledger.
 *
 * <p>Every event carries a stable {@code eventId}, the {@code EventType} discriminator,
 * the {@code occurredAt} timestamp, and the {@code sourceService} that produced it.
 *
 * <p>Jackson polymorphism is wired via {@code eventType} so events can be
 * serialised / deserialised transparently across service boundaries over Kafka.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = UserLoggedInEvent.class, name = "USER_LOGGED_IN")
})
public abstract class AuditEvent {

    /** Globally unique event identifier (UUID v4). */
    private String eventId;

    /** Discriminator field — matches {@link EventType}. */
    private EventType eventType;

    /** Wall-clock time when the event occurred (UTC). */
    private Instant occurredAt;

    /** Logical name of the service/component that produced this event. */
    private String sourceService;

    /**
     * Convenience factory: populate the common fields with sensible defaults.
     */
    protected void initDefaults(EventType type, String source) {
        this.eventId      = UUID.randomUUID().toString();
        this.eventType    = type;
        this.occurredAt   = Instant.now();
        this.sourceService = source;
    }
}


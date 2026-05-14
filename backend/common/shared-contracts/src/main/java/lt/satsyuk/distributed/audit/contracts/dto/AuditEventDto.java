package lt.satsyuk.distributed.audit.contracts.dto;

import lt.satsyuk.distributed.audit.event.EventType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Read-model DTO representing a single audit event record.
 *
 * <p>Returned by the query-service REST API and used by the frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditEventDto {

    /** Surrogate primary key from the event-store database. */
    private Long id;

    /** Globally unique event identifier (UUID). */
    private String eventId;

    /** Discriminator type of this event. */
    private EventType eventType;

    /** ID of the user associated with the event (nullable). */
    private String userId;

    /** Wall-clock time when the event was recorded (UTC). */
    private Instant occurredAt;

    /** Raw JSON payload of the event. */
    private String eventDataJson;

    /** SHA-256 hash of the event payload (hex string, nullable until anchored). */
    private String eventHash;

    /**
     * Blockchain integrity status.
     * {@code ON_CHAIN} — hash confirmed on Ganache.
     * {@code PENDING}  — not yet anchored.
     * {@code MISMATCH} — hash exists in DB but differs on-chain.
     */
    private String integrityStatus;
}


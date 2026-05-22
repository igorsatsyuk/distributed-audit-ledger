package lt.satsyuk.distributed.audit.eventstore.model;

import io.r2dbc.postgresql.codec.Json;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * R2DBC entity mapped to the {@code audit.events} table — owned by {@code event-store-service}.
 *
 * <p>The {@code payload} field uses {@link io.r2dbc.postgresql.codec.Json} for native JSONB writes.
 * {@code query-service} has a structurally similar read-only entity ({@code AuditEventRecord}) that
 * reads {@code payload} as plain {@code String} ({@code payload::text}).  The two classes intentionally
 * live in separate bounded contexts and cannot share a common base class without pulling
 * {@code spring-data-relational} into the shared-contracts module.
 */
@Table(schema = "audit", name = "events")
@Getter
@Setter
public class StoredAuditEvent {

    @Id
    private Long id;

    @Column("event_id")
    private String eventId;

    @Column("aggregate_id")
    private String aggregateId;

    @Column("event_type")
    private String eventType;

    @Column("user_id")
    private String userId;

    @Column("payload")
    private Json payload;

    @Column("event_hash")
    private String eventHash;

    @Column("created_at")
    private LocalDateTime createdAt;

}


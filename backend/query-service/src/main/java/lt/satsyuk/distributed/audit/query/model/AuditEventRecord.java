package lt.satsyuk.distributed.audit.query.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table(schema = "audit", name = "events")
@Getter
@Setter
public class AuditEventRecord {

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
    private String payload;

    @Column("event_hash")
    private String eventHash;

    @Column("created_at")
    private LocalDateTime createdAt;
}

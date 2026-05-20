package lt.satsyuk.distributed.audit.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Domain event raised when data is deleted.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DataDeletedEvent extends AuditEvent {

    private String userId;
    private String entityType;
    private String entityId;
    private String reason;

    public static DataDeletedEvent of(String userId, String entityType, String entityId, String reason) {
        DataDeletedEvent event = DataDeletedEvent.builder()
                .userId(userId)
                .entityType(entityType)
                .entityId(entityId)
                .reason(reason)
                .build();
        event.initDefaults(EventType.DATA_DELETED, "command-service");
        return event;
    }
}


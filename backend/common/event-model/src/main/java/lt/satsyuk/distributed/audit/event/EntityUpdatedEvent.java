package lt.satsyuk.distributed.audit.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Domain event raised when an existing entity is updated.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EntityUpdatedEvent extends AuditEvent {

    private String userId;
    private String entityType;
    private String entityId;
    private Map<String, Object> changedFields;

    public static EntityUpdatedEvent of(String userId, String entityType, String entityId, Map<String, Object> changedFields) {
        EntityUpdatedEvent event = EntityUpdatedEvent.builder()
                .userId(userId)
                .entityType(entityType)
                .entityId(entityId)
                .changedFields(changedFields)
                .build();
        event.initDefaults(EventType.ENTITY_UPDATED, "command-service");
        return event;
    }
}


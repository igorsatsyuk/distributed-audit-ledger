package lt.satsyuk.distributed.audit.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Domain event raised when a new entity is created.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EntityCreatedEvent extends AuditEvent {

    private String userId;
    private String entityType;
    private String entityId;
    private Map<String, Object> entityData;

    public static EntityCreatedEvent of(String userId, String entityType, String entityId, Map<String, Object> entityData) {
        EntityCreatedEvent event = EntityCreatedEvent.builder()
                .userId(userId)
                .entityType(entityType)
                .entityId(entityId)
                .entityData(entityData)
                .build();
        event.initDefaults(EventType.ENTITY_CREATED, "command-service");
        return event;
    }
}


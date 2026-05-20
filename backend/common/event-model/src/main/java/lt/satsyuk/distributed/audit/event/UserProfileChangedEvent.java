package lt.satsyuk.distributed.audit.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Domain event raised when a user's profile fields are changed.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserProfileChangedEvent extends AuditEvent {

    private String userId;
    private Map<String, Object> changedFields;

    public static UserProfileChangedEvent of(String userId, Map<String, Object> changedFields) {
        UserProfileChangedEvent event = UserProfileChangedEvent.builder()
                .userId(userId)
                .changedFields(changedFields)
                .build();
        event.initDefaults(EventType.USER_PROFILE_CHANGED, "command-service");
        return event;
    }
}


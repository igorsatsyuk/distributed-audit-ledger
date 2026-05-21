package lt.satsyuk.distributed.audit.contracts.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Command DTO sent by clients to record an entity update.
 *
 * <p>Consumed by {@code POST /commands/entity/update} in the command-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityUpdatedCommand {

    /** Identifier of the user who initiated the update. */
    @NotBlank(message = "userId must not be blank")
    private String userId;

    /** Logical type of the updated entity. */
    @NotBlank(message = "entityType must not be blank")
    private String entityType;

    /** Stable identifier of the updated entity. */
    @NotBlank(message = "entityId must not be blank")
    private String entityId;

    /** Map of updated fields and their new values. */
    @NotNull(message = "changedFields must not be null")
    private Map<String, Object> changedFields;
}


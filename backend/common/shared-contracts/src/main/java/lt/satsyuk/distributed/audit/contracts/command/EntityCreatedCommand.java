package lt.satsyuk.distributed.audit.contracts.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Command DTO sent by clients to record a new entity creation.
 *
 * <p>Consumed by {@code POST /commands/entity/create} in the command-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityCreatedCommand {

    /** Identifier of the user who initiated the creation. */
    @NotBlank(message = "userId must not be blank")
    private String userId;

    /** Logical type of the created entity. */
    @NotBlank(message = "entityType must not be blank")
    private String entityType;

    /** Stable identifier of the created entity. */
    @NotBlank(message = "entityId must not be blank")
    private String entityId;

    /** Full entity payload captured at creation time. */
    @NotNull(message = "entityData must not be null")
    private Map<String, Object> entityData;
}


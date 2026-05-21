package lt.satsyuk.distributed.audit.contracts.command;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command DTO sent by clients to record a data deletion.
 *
 * <p>Consumed by {@code POST /commands/data/delete} in the command-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataDeletedCommand {

    /** Identifier of the user who initiated the deletion. */
    @NotBlank(message = "userId must not be blank")
    private String userId;

    /** Logical type of the deleted entity. */
    @NotBlank(message = "entityType must not be blank")
    private String entityType;

    /** Stable identifier of the deleted entity. */
    @NotBlank(message = "entityId must not be blank")
    private String entityId;

    /** Optional human-readable reason for deletion. */
    private String reason;
}


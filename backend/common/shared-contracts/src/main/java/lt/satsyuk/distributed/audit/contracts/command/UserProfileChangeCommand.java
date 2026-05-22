package lt.satsyuk.distributed.audit.contracts.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Command DTO sent by clients to record a user profile change.
 *
 * <p>Consumed by {@code POST /commands/user/profile-change} in the command-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileChangeCommand {

    /** Identifier of the user whose profile was changed. */
    @NotBlank(message = "userId must not be blank")
    private String userId;

    /** Map of changed profile fields and their new values. */
    @NotNull(message = "changedFields must not be null")
    private Map<String, Object> changedFields;
}


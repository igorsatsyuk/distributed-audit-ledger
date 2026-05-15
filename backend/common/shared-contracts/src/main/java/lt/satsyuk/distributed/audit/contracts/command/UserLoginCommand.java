package lt.satsyuk.distributed.audit.contracts.command;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command DTO sent by clients to record a user login event.
 *
 * <p>Consumed by {@code POST /commands/user/login} in the command-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginCommand {

    /** Identifier of the user performing the login. */
    @NotBlank(message = "userId must not be blank")
    private String userId;

    /** Optional: originating IP address of the request. */
    private String ipAddress;

    /** Optional: HTTP User-Agent header value. */
    private String userAgent;
}


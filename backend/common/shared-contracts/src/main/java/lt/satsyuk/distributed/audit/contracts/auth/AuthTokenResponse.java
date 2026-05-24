package lt.satsyuk.distributed.audit.contracts.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * Successful authentication response containing a bearer token and resolved roles.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenResponse {

    private String accessToken;
    private String tokenType;
    private Instant expiresAt;
    private String username;
    private Set<UserRole> roles;
}


package lt.satsyuk.distributed.audit.contracts.auth;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

/**
 * Shared JWT configuration values used by backend services.
 */
@Getter
@Setter
public class JwtProperties {

    private String secret;
    private String issuer;
    private Duration expiration;
}


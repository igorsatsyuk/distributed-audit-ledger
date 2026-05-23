package lt.satsyuk.distributed.audit.query.config;

import lt.satsyuk.distributed.audit.contracts.auth.JwtProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings shared with command-service for validating bearer tokens.
 */
@ConfigurationProperties(prefix = "auth.jwt")
public class AuthJwtProperties extends JwtProperties {
}


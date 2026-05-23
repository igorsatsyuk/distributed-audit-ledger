package lt.satsyuk.distributed.audit.query.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings shared with command-service for validating bearer tokens.
 */
@ConfigurationProperties(prefix = "auth.jwt")
public class AuthJwtProperties {

    private String secret = "distributed-audit-ledger-development-secret-please-change";
    private String issuer = "distributed-audit-ledger";
    private Duration expiration = Duration.ofHours(1);

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getExpiration() {
        return expiration;
    }

    public void setExpiration(Duration expiration) {
        this.expiration = expiration;
    }
}


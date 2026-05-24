package lt.satsyuk.distributed.audit.contracts.auth;

import java.time.Instant;
import java.util.Set;

/**
 * Parsed JWT claims consumed by backend security filters.
 */
public record JwtClaims(
        String subject,
        Set<UserRole> roles,
        Instant issuedAt,
        Instant expiresAt,
        String issuer
) {
}


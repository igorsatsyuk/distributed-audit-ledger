package lt.satsyuk.distributed.audit.contracts.auth.security;

import lt.satsyuk.distributed.audit.contracts.auth.JwtClaims;
import lt.satsyuk.distributed.audit.contracts.auth.JwtService;
import lt.satsyuk.distributed.audit.contracts.auth.JwtValidationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Validates bearer JWT and maps role claims to Spring Security authorities.
 */
public class JwtTokenReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtService jwtService;

    public JwtTokenReactiveAuthenticationManager(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = String.valueOf(authentication.getCredentials());
        return Mono.fromSupplier(() -> jwtService.parseAndValidate(token, Instant.now()))
                .map(this::toAuthentication)
                .onErrorMap(JwtValidationException.class,
                        exception -> new BadCredentialsException(exception.getMessage(), exception));
    }

    private Authentication toAuthentication(JwtClaims claims) {
        return UsernamePasswordAuthenticationToken.authenticated(
                claims.subject(),
                null,
                claims.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .toList()
        );
    }
}


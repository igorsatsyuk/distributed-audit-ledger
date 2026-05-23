package lt.satsyuk.distributed.audit.contracts.auth.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Mono;

/**
 * Extracts a bearer token from Authorization header for reactive security filters.
 */
public class BearerTokenAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public @NonNull Mono<Authentication> convert(@NonNull ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return Mono.empty();
        }
        if (!authorization.startsWith(BEARER_PREFIX) || authorization.length() <= BEARER_PREFIX.length()) {
            return Mono.empty();
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return Mono.empty();
        }

        return Mono.just(UsernamePasswordAuthenticationToken.unauthenticated("jwt", token));
    }
}


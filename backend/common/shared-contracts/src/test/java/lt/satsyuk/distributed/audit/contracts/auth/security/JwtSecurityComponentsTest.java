package lt.satsyuk.distributed.audit.contracts.auth.security;

import lt.satsyuk.distributed.audit.contracts.auth.JwtService;
import lt.satsyuk.distributed.audit.contracts.auth.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecurityComponentsTest {
    private static final Instant FIXED_ISSUED_AT = Instant.parse("2099-05-15T10:15:30Z");

    @Test
    void bearerTokenConverterExtractsTokenFromAuthorizationHeader() {
        BearerTokenAuthenticationConverter converter = new BearerTokenAuthenticationConverter();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-value")
                        .build()
        );

        Authentication authentication = converter.convert(exchange).block();

        assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(authentication.getCredentials()).isEqualTo("token-value");
    }

    @Test
    void bearerTokenConverterAcceptsLowercaseBearerScheme() {
        BearerTokenAuthenticationConverter converter = new BearerTokenAuthenticationConverter();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "bearer token-value")
                        .build()
        );

        Authentication authentication = converter.convert(exchange).block();

        assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(authentication.getCredentials()).isEqualTo("token-value");
    }

    @Test
    void jwtAuthenticationManagerMapsRolesToGrantedAuthorities() {
        JwtService jwtService = new JwtService("shared-security-test-secret-123456", "dal-test", Duration.ofMinutes(30));
        String token = jwtService.generateToken("auditor", Set.of(UserRole.AUDITOR, UserRole.ADMIN), FIXED_ISSUED_AT);

        JwtTokenReactiveAuthenticationManager authenticationManager =
                new JwtTokenReactiveAuthenticationManager(jwtService);

        Authentication result = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("jwt", token)
        ).block();

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("auditor");
        assertThat(result.getAuthorities()).extracting(Object::toString)
                .containsExactlyInAnyOrder(
                        new SimpleGrantedAuthority("ROLE_AUDITOR").toString(),
                        new SimpleGrantedAuthority("ROLE_ADMIN").toString()
                );
    }
}


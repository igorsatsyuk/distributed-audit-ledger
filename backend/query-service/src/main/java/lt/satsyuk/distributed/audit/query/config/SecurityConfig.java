package lt.satsyuk.distributed.audit.query.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lt.satsyuk.distributed.audit.contracts.auth.JwtService;
import lt.satsyuk.distributed.audit.contracts.auth.security.BearerTokenAuthenticationConverter;
import lt.satsyuk.distributed.audit.contracts.auth.security.JwtTokenReactiveAuthenticationManager;
import lt.satsyuk.distributed.audit.query.api.ApiErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Reactive security configuration for query-service read APIs.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Bean
    JwtService jwtService(AuthJwtProperties authJwtProperties) {
        return new JwtService(
                authJwtProperties.getSecret(),
                authJwtProperties.getIssuer(),
                authJwtProperties.getExpiration()
        );
    }

    @Bean
    ReactiveAuthenticationManager reactiveAuthenticationManager(JwtService jwtService) {
        return new JwtTokenReactiveAuthenticationManager(jwtService);
    }

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                     ReactiveAuthenticationManager authenticationManager) {
        AuthenticationWebFilter jwtFilter = new AuthenticationWebFilter(authenticationManager);
        jwtFilter.setServerAuthenticationConverter(new BearerTokenAuthenticationConverter());
        jwtFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        jwtFilter.setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.pathMatchers("/api/**"));

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ignored) -> writeJson(
                                exchange,
                                401,
                                new ApiErrorResponse("Authentication is required")
                        ))
                        .accessDeniedHandler((exchange, ignored) -> writeJson(
                                exchange,
                                403,
                                new ApiErrorResponse("Access denied")
                        )))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .pathMatchers("/api/**").hasAnyRole("AUDITOR", "ADMIN")
                        .anyExchange().authenticated())
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    private Mono<Void> writeJson(ServerWebExchange exchange,
                                 int status,
                                 ApiErrorResponse body) {
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = OBJECT_MAPPER.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }
}


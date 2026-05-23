package lt.satsyuk.distributed.audit.command.service;

import lt.satsyuk.distributed.audit.command.config.AuthProperties;
import lt.satsyuk.distributed.audit.contracts.auth.AuthLoginRequest;
import lt.satsyuk.distributed.audit.contracts.auth.AuthTokenResponse;
import lt.satsyuk.distributed.audit.contracts.auth.JwtService;
import lt.satsyuk.distributed.audit.contracts.auth.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Verifies user credentials against configured in-memory users and issues JWT tokens.
 */
@Service
public class AuthenticationService {

    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Map<String, AuthenticatedUser> usersByUsername;

    public AuthenticationService(AuthProperties authProperties,
                                 PasswordEncoder passwordEncoder,
                                 JwtService jwtService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.usersByUsername = authProperties.getUsers().stream()
                .collect(Collectors.toUnmodifiableMap(
                        AuthProperties.User::getUsername,
                        user -> new AuthenticatedUser(
                                user.getUsername(),
                                passwordEncoder.encode(user.getPassword()),
                                Set.copyOf(user.getRoles())
                        ),
                        preferFirst()
                ));
    }

    public Mono<AuthTokenResponse> login(AuthLoginRequest request) {
        return Mono.fromSupplier(() -> authenticate(request));
    }

    private AuthTokenResponse authenticate(AuthLoginRequest request) {
        AuthenticatedUser user = usersByUsername.get(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.encodedPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        Instant issuedAt = Instant.now();
        return AuthTokenResponse.builder()
                .accessToken(jwtService.generateToken(user.username(), user.roles(), issuedAt))
                .tokenType("Bearer")
                .expiresAt(issuedAt.plus(jwtService.getExpiration()))
                .username(user.username())
                .roles(user.roles())
                .build();
    }

    private static <T> java.util.function.BinaryOperator<T> preferFirst() {
        return (left, ignored) -> left;
    }

    private record AuthenticatedUser(String username, String encodedPassword, Set<UserRole> roles) {
    }
}


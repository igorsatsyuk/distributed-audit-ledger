package lt.satsyuk.distributed.audit.command.service;

import lt.satsyuk.distributed.audit.command.config.AuthProperties;
import lt.satsyuk.distributed.audit.contracts.auth.AuthLoginRequest;
import lt.satsyuk.distributed.audit.contracts.auth.AuthTokenResponse;
import lt.satsyuk.distributed.audit.contracts.auth.JwtService;
import lt.satsyuk.distributed.audit.contracts.auth.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
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
        List<AuthProperties.User> configuredUsers = Objects.requireNonNull(authProperties.getUsers(), "auth.users must be configured");
        if (configuredUsers.isEmpty()) {
            throw new IllegalStateException("auth.users must not be empty");
        }

        // Validate each configured user before building the map
        Set<String> usernames = new HashSet<>();
        for (AuthProperties.User user : configuredUsers) {
            if (user.getUsername() == null || user.getUsername().isBlank()) {
                throw new IllegalStateException("auth.users[*].username must not be blank");
            }
            if (!usernames.add(user.getUsername())) {
                throw new IllegalStateException("auth.users contains duplicate username: " + user.getUsername());
            }
            if (user.getPassword() == null || user.getPassword().isBlank()) {
                throw new IllegalStateException("auth.users[*].password must not be blank");
            }
            if (user.getRoles() == null || user.getRoles().isEmpty()) {
                throw new IllegalStateException("auth.users[*].roles must not be empty");
            }
        }

        this.usersByUsername = configuredUsers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        AuthProperties.User::getUsername,
                        user -> new AuthenticatedUser(
                                user.getUsername(),
                                passwordEncoder.encode(user.getPassword()),
                                Set.copyOf(user.getRoles())
                        )
                ));
    }

    public Mono<AuthTokenResponse> login(AuthLoginRequest request) {
        return Mono.fromCallable(() -> authenticate(request))
                .subscribeOn(Schedulers.boundedElastic());
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


    private record AuthenticatedUser(String username, String encodedPassword, Set<UserRole> roles) {
    }
}


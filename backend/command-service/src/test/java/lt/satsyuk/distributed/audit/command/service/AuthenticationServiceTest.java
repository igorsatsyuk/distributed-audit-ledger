package lt.satsyuk.distributed.audit.command.service;

import lt.satsyuk.distributed.audit.command.config.AuthProperties;
import lt.satsyuk.distributed.audit.contracts.auth.AuthLoginRequest;
import lt.satsyuk.distributed.audit.contracts.auth.AuthTokenResponse;
import lt.satsyuk.distributed.audit.contracts.auth.JwtService;
import lt.satsyuk.distributed.audit.contracts.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        AuthProperties authProperties = createValidAuthProperties();
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation ->
                "encoded_" + invocation.getArgument(0)
        );
        authenticationService = new AuthenticationService(authProperties, passwordEncoder, jwtService);
    }

    @Test
    void loginSucceedsWithValidCredentials() {
        when(jwtService.generateToken(anyString(), anySet(), any(Instant.class)))
                .thenReturn("valid.jwt.token");
        when(jwtService.getExpiration())
                .thenReturn(Duration.ofMinutes(15));

        AuthLoginRequest request = AuthLoginRequest.builder()
                .username("testuser")
                .password("testpass")
                .build();

        when(passwordEncoder.matches("testpass", "encoded_testpass"))
                .thenReturn(true);

        AuthTokenResponse response = authenticationService.login(request).block();

        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        assertEquals("valid.jwt.token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertNotNull(response.getExpiresAt());
        assertTrue(response.getRoles().contains(UserRole.USER));
    }

    @Test
    void loginFailsWithInvalidPassword() {
        AuthLoginRequest request = AuthLoginRequest.builder()
                .username("testuser")
                .password("wrongpass")
                .build();

        when(passwordEncoder.matches("wrongpass", "encoded_testpass"))
                .thenReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                authenticationService.login(request)::block
        );
        assertNotNull(exception);
    }

    @Test
    void loginFailsWithNonexistentUser() {
        AuthLoginRequest request = AuthLoginRequest.builder()
                .username("nonexistent")
                .password("testpass")
                .build();

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                authenticationService.login(request)::block
        );
        assertNotNull(exception);
    }

    @Test
    void constructorThrowsWhenUsersConfigurationIsNull() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setUsers(null);

        assertThrows(
                NullPointerException.class,
                () -> new AuthenticationService(authProperties, passwordEncoder, jwtService)
        );
    }

    @Test
    void constructorThrowsWhenUsersConfigurationIsEmpty() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setUsers(List.of());

        assertThrows(
                IllegalStateException.class,
                () -> new AuthenticationService(authProperties, passwordEncoder, jwtService)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCredentials")
    void constructorThrowsWhenUserCredentialsAreInvalid(String username, String password) {
        AuthProperties authProperties = new AuthProperties();
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername(username);
        user.setPassword(password);
        user.setRoles(EnumSet.of(UserRole.USER));
        authProperties.setUsers(List.of(user));

        assertThrows(
                IllegalStateException.class,
                () -> new AuthenticationService(authProperties, passwordEncoder, jwtService)
        );
    }

    @Test
    void constructorThrowsWhenRolesIsNull() {
        AuthProperties authProperties = new AuthProperties();
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("testuser");
        user.setPassword("testpass");
        user.setRoles(null);
        authProperties.setUsers(List.of(user));

        assertThrows(
                IllegalStateException.class,
                () -> new AuthenticationService(authProperties, passwordEncoder, jwtService)
        );
    }

    @Test
    void constructorThrowsWhenRolesIsEmpty() {
        AuthProperties authProperties = new AuthProperties();
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("testuser");
        user.setPassword("testpass");
        user.setRoles(EnumSet.noneOf(UserRole.class));
        authProperties.setUsers(List.of(user));

        assertThrows(
                IllegalStateException.class,
                () -> new AuthenticationService(authProperties, passwordEncoder, jwtService)
        );
    }

    @Test
    void constructorValidatesAllUsersInConfiguration() {
        AuthProperties authProperties = new AuthProperties();
        AuthProperties.User user1 = new AuthProperties.User();
        user1.setUsername("user1");
        user1.setPassword("pass1");
        user1.setRoles(EnumSet.of(UserRole.USER));

        AuthProperties.User user2 = new AuthProperties.User();
        user2.setUsername("   ");  // Invalid second user
        user2.setPassword("pass2");
        user2.setRoles(EnumSet.of(UserRole.USER));

        authProperties.setUsers(List.of(user1, user2));

        assertThrows(
                IllegalStateException.class,
                () -> new AuthenticationService(authProperties, passwordEncoder, jwtService)
        );
    }

    @Test
    void constructorThrowsWhenDuplicateUsernamesConfigured() {
        AuthProperties authProperties = new AuthProperties();

        AuthProperties.User first = new AuthProperties.User();
        first.setUsername("duplicate-user");
        first.setPassword("pass1");
        first.setRoles(EnumSet.of(UserRole.USER));

        AuthProperties.User second = new AuthProperties.User();
        second.setUsername("duplicate-user");
        second.setPassword("pass2");
        second.setRoles(EnumSet.of(UserRole.ADMIN));

        authProperties.setUsers(List.of(first, second));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new AuthenticationService(authProperties, passwordEncoder, jwtService)
        );
        assertTrue(exception.getMessage().contains("duplicate username"));
    }

    private AuthProperties createValidAuthProperties() {
        AuthProperties authProperties = new AuthProperties();
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("testuser");
        user.setPassword("testpass");
        user.setRoles(EnumSet.of(UserRole.USER));
        authProperties.setUsers(List.of(user));
        return authProperties;
    }

    private static Stream<Arguments> invalidCredentials() {
        return Stream.of(
                Arguments.of("   ", "testpass"),
                Arguments.of(null, "testpass"),
                Arguments.of("testuser", "   "),
                Arguments.of("testuser", null)
        );
    }
}


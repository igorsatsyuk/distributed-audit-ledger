package lt.satsyuk.distributed.audit.command.service;

import lt.satsyuk.distributed.audit.command.config.AuthProperties;
import lt.satsyuk.distributed.audit.contracts.auth.AuthLoginRequest;
import lt.satsyuk.distributed.audit.contracts.auth.AuthTokenResponse;
import lt.satsyuk.distributed.audit.contracts.auth.JwtService;
import lt.satsyuk.distributed.audit.contracts.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

        assertThrows(
                InvalidCredentialsException.class,
                () -> authenticationService.login(request).block()
        );
    }

    @Test
    void loginFailsWithNonexistentUser() {
        AuthLoginRequest request = AuthLoginRequest.builder()
                .username("nonexistent")
                .password("testpass")
                .build();

        assertThrows(
                InvalidCredentialsException.class,
                () -> authenticationService.login(request).block()
        );
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

    @Test
    void constructorThrowsWhenUsernameIsBlank() {
        AuthProperties authProperties = new AuthProperties();
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("   ");
        user.setPassword("testpass");
        user.setRoles(EnumSet.of(UserRole.USER));
        authProperties.setUsers(List.of(user));

        assertThrows(
                IllegalStateException.class,
                () -> new AuthenticationService(authProperties, passwordEncoder, jwtService)
        );
    }

    @Test
    void constructorThrowsWhenUsernameIsNull() {
        AuthProperties authProperties = new AuthProperties();
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername(null);
        user.setPassword("testpass");
        user.setRoles(EnumSet.of(UserRole.USER));
        authProperties.setUsers(List.of(user));

        assertThrows(
                IllegalStateException.class,
                () -> new AuthenticationService(authProperties, passwordEncoder, jwtService)
        );
    }

    @Test
    void constructorThrowsWhenPasswordIsBlank() {
        AuthProperties authProperties = new AuthProperties();
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("testuser");
        user.setPassword("   ");
        user.setRoles(EnumSet.of(UserRole.USER));
        authProperties.setUsers(List.of(user));

        assertThrows(
                IllegalStateException.class,
                () -> new AuthenticationService(authProperties, passwordEncoder, jwtService)
        );
    }

    @Test
    void constructorThrowsWhenPasswordIsNull() {
        AuthProperties authProperties = new AuthProperties();
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("testuser");
        user.setPassword(null);
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

    private AuthProperties createValidAuthProperties() {
        AuthProperties authProperties = new AuthProperties();
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("testuser");
        user.setPassword("testpass");
        user.setRoles(EnumSet.of(UserRole.USER));
        authProperties.setUsers(List.of(user));
        return authProperties;
    }
}


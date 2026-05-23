package lt.satsyuk.distributed.audit.command.config;

import lt.satsyuk.distributed.audit.contracts.auth.UserRole;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Externalized authentication settings for command-service.
 */
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private Jwt jwt = new Jwt();
    private List<User> users = defaultUsers();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    private static List<User> defaultUsers() {
        List<User> defaults = new ArrayList<>();
        defaults.add(new User("admin", "admin123!", EnumSet.of(UserRole.ADMIN, UserRole.AUDITOR, UserRole.USER)));
        defaults.add(new User("auditor", "auditor123!", EnumSet.of(UserRole.AUDITOR)));
        defaults.add(new User("user", "user123!", EnumSet.of(UserRole.USER)));
        return defaults;
    }

    public static class Jwt {
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

    public static class User {
        private String username;
        private String password;
        private Set<UserRole> roles = EnumSet.of(UserRole.USER);

        public User() {
        }

        public User(String username, String password, Set<UserRole> roles) {
            this.username = username;
            this.password = password;
            this.roles = roles;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Set<UserRole> getRoles() {
            return roles;
        }

        public void setRoles(Set<UserRole> roles) {
            this.roles = roles;
        }
    }
}


package lt.satsyuk.distributed.audit.command.config;

import lt.satsyuk.distributed.audit.contracts.auth.JwtProperties;
import lt.satsyuk.distributed.audit.contracts.auth.UserRole;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Externalized authentication settings for command-service.
 */
@ConfigurationProperties(prefix = "auth")
@Getter
@Setter
public class AuthProperties {

    private JwtProperties jwt;
    private List<User> users;


    @Getter
    @Setter
    public static class User {
        private String username;
        private String password;
        private Set<UserRole> roles = EnumSet.of(UserRole.USER);
    }
}


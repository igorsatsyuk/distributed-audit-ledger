package lt.satsyuk.distributed.audit.contracts.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    @Test
    void jwtPropertiesStoresSecretIssuerAndExpiration() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-value");
        properties.setIssuer("test-issuer");
        properties.setExpiration(Duration.ofMinutes(15));

        assertThat(properties.getSecret()).isEqualTo("test-secret-value");
        assertThat(properties.getIssuer()).isEqualTo("test-issuer");
        assertThat(properties.getExpiration()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void jwtPropertiesAllowsNullValues() {
        JwtProperties properties = new JwtProperties();

        assertThat(properties.getSecret()).isNull();
        assertThat(properties.getIssuer()).isNull();
        assertThat(properties.getExpiration()).isNull();
    }
}


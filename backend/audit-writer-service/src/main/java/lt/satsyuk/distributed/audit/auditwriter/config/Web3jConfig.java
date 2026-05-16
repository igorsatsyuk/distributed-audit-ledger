package lt.satsyuk.distributed.audit.auditwriter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

/**
 * Spring configuration that creates Web3j-related beans.
 *
 * <p>The {@link Web3j} bean is always created so the application can start even when
 * {@code web3j.private-key} is not configured (read-only / degraded mode).
 * The {@link Credentials} bean is created only for a valid private key so startup remains
 * in degraded mode for missing/malformed keys; runtime validation happens in
 * {@link lt.satsyuk.distributed.audit.auditwriter.service.BlockchainWriterService}.
 */
@Configuration
public class Web3jConfig {

    @Bean
    public Web3j web3j(Web3jProperties props) {
        return Web3j.build(new HttpService(props.getClientAddress()));
    }

    /**
     * Creates the signing credentials only when {@code web3j.private-key} is a valid
     * 64-hex Ethereum private key (with optional {@code 0x} prefix).
     *
     * <p>Malformed values intentionally skip bean creation so the app can still start
     * in degraded mode and report misconfiguration at runtime.
     */
    @Bean
    @Conditional(ValidPrivateKeyCondition.class)
    public Credentials credentials(Web3jProperties props) {
        return Credentials.create(props.getPrivateKey().trim());
    }

    static class ValidPrivateKeyCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String privateKey = context.getEnvironment().getProperty("web3j.private-key");
            return Web3jValidationUtils.isValidPrivateKey(privateKey);
        }
    }
}


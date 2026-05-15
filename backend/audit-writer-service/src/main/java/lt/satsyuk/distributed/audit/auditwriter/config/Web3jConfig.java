package lt.satsyuk.distributed.audit.auditwriter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

/**
 * Spring configuration that creates Web3j-related beans.
 *
 * <p>The {@link Web3j} bean is always created so the application can start even when
 * {@code web3j.private-key} is not configured (read-only / degraded mode).
 * The {@link Credentials} bean is only created when a non-blank private key is provided;
 * {@link lt.satsyuk.distributed.audit.auditwriter.service.BlockchainWriterService} guards
 * against a missing credentials bean at runtime.
 */
@Configuration
public class Web3jConfig {

    @Bean
    public Web3j web3j(Web3jProperties props) {
        return Web3j.build(new HttpService(props.getClientAddress()));
    }

    /**
     * Creates the signing credentials only when {@code web3j.private-key} is non-blank.
     * Avoids a startup failure when the key has not yet been configured.
     */
    @Bean
    @ConditionalOnProperty(name = "web3j.private-key", matchIfMissing = false)
    public Credentials credentials(Web3jProperties props) {
        String key = props.getPrivateKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        return Credentials.create(key);
    }
}


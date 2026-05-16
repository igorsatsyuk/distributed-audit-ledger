package lt.satsyuk.distributed.audit.auditwriter.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties for Web3j / Ganache connectivity.
 * Bound from the {@code web3j.*} namespace in {@code application.yml}.
 */
@Data
@ToString(exclude = "privateKey")  // Prevent accidental exposure in logs
@ConfigurationProperties(prefix = "web3j")
public class Web3jProperties {

    /** Ganache (or any EVM-compatible node) JSON-RPC endpoint. */
    private String clientAddress = "http://localhost:8545";

    /** Deployed AuditLedger contract address (set after {@code npm run deploy:ganache}). */
    private String contractAddress = "";

    /** Hex-encoded private key for the signing account (Ganache deterministic account 0). */
    private String privateKey = "";

    /**
     * Number of service-side write retries before the Kafka error handler takes over.
     * Total attempts are {@code blockchainWriteRetries + 1}.
     */
    private int blockchainWriteRetries = 3;

    /**
     * Maximum time to wait for a mined transaction receipt before the write attempt is
     * treated as failed and retried. Bounded to prevent the Kafka listener thread from
     * waiting indefinitely on receipt polling.
     */
    private int receiptWaitTimeoutSeconds = 30;

    /**
     * Maximum number of seconds that an event's {@code occurredAt} timestamp may be
     * in the future before the event is treated as non-recoverable.  Configurable so
     * operators can compensate for producer clock-skew or intentionally future-dated fixtures without
     * rebuilding the service.  Defaults to 300 (5 minutes).
     */
    private int futureTimestampToleranceSeconds = 300;
}


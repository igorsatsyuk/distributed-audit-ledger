package lt.satsyuk.distributed.audit.auditwriter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration properties for Web3j / Ganache connectivity.
 * Bound from the {@code web3j.*} namespace in {@code application.yml}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "web3j")
public class Web3jProperties {

    /** Ganache (or any EVM-compatible node) JSON-RPC endpoint. */
    private String clientAddress = "http://localhost:8545";

    /** Deployed AuditLedger contract address (set after {@code npm run deploy:ganache}). */
    private String contractAddress = "";

    /** Hex-encoded private key for the signing account (Ganache deterministic account 0). */
    private String privateKey = "";
}


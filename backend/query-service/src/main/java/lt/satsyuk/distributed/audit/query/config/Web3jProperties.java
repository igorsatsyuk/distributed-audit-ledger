package lt.satsyuk.distributed.audit.query.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "web3j")
public class Web3jProperties {

    private String clientAddress = "http://localhost:8545";
    private String contractAddress = "";
}


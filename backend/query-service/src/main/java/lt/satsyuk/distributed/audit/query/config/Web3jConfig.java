package lt.satsyuk.distributed.audit.query.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class Web3jConfig {

    @Bean
    public Web3j web3j(Web3jProperties props) {
        String clientAddress = props.getClientAddress();
        if (clientAddress == null || clientAddress.isBlank()) {
            throw new IllegalStateException("web3j.client-address must not be blank");
        }

        return Web3j.build(new HttpService(clientAddress.trim()));
    }
}

